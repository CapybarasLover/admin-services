// Единственное место, где фронт разговаривает с бэкендом.
// Бэкенд отдаёт ошибки как RFC 7807 (application/problem+json) с готовым
// русским текстом в detail, поэтому свой словарь сообщений тут не нужен.

export interface FieldError {
  field: string
  message: string
}

export class ApiError extends Error {
  readonly status: number
  readonly title?: string
  readonly detail?: string
  readonly errors?: FieldError[]

  constructor(status: number, payload: { title?: string; detail?: string; errors?: FieldError[] }) {
    super(payload.detail || payload.title || `Ошибка ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.title = payload.title
    this.detail = payload.detail
    this.errors = payload.errors
  }

  /** Сообщение поля формы, если бэкенд завалил валидацию именно на нём. */
  fieldError(field: string): string | undefined {
    return this.errors?.find((e) => e.field === field)?.message
  }
}

const TOKEN_KEY = 'auth.token'

export const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED !== 'false'

let authToken: string | null = AUTH_ENABLED ? localStorage.getItem(TOKEN_KEY) : null

export function getToken() {
  return authToken
}

export function setToken(token: string | null) {
  authToken = token
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

/** Протухший токен ловится в одном месте; на событие реагирует AuthProvider. */
export const UNAUTHORIZED_EVENT = 'auth:unauthorized'

export type QueryParams = Record<string, string | number | boolean | undefined | null>

function buildUrl(path: string, params?: QueryParams) {
  if (!params) return path
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    search.append(key, String(value))
  }
  const qs = search.toString()
  return qs ? `${path}?${qs}` : path
}

interface RequestOptions {
  method?: string
  params?: QueryParams
  body?: unknown
  signal?: AbortSignal
  accept?: string
  timeoutMs?: number
}

// У fetch нет своего таймаута: зависший запрос не отваливается никогда,
// и интерфейс остаётся в «загрузке» без единого сообщения. Поэтому режем сами.
const DEFAULT_TIMEOUT_MS = 30_000
const DOWNLOAD_TIMEOUT_MS = 60_000

async function toApiError(response: Response): Promise<ApiError> {
  let payload: { title?: string; detail?: string; errors?: FieldError[] } = {}
  try {
    const text = await response.text()
    if (text) {
      const parsed = JSON.parse(text) as Record<string, unknown>
      payload = {
        title: typeof parsed.title === 'string' ? parsed.title : undefined,
        detail: typeof parsed.detail === 'string' ? parsed.detail : undefined,
        errors: Array.isArray(parsed.errors) ? (parsed.errors as FieldError[]) : undefined,
      }
    }
  } catch {
    // Не problem+json — обойдёмся статусом.
  }
  return new ApiError(response.status, payload)
}

async function send(path: string, options: RequestOptions = {}): Promise<Response> {
  const {
    method = 'GET',
    params,
    body,
    signal,
    accept = 'application/json',
    timeoutMs = DEFAULT_TIMEOUT_MS,
  } = options

  const headers: Record<string, string> = { Accept: accept }
  if (authToken) headers.Authorization = `Bearer ${authToken}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  // Свой контроллер: он же гасит запрос по таймауту, он же пробрасывает отмену снаружи
  // (react-query отменяет запросы при уходе со страницы — это не ошибка, а штатный случай).
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  const forwardAbort = () => controller.abort()
  signal?.addEventListener('abort', forwardAbort, { once: true })

  let response: Response
  try {
    response = await fetch(buildUrl(path, params), {
      method,
      headers,
      signal: controller.signal,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (cause) {
    if (controller.signal.aborted && !signal?.aborted) {
      throw new ApiError(408, {
        title: 'Сервер не ответил',
        detail: `Запрос оборван после ${Math.round(timeoutMs / 1000)} с ожидания. Проверьте соединение и повторите.`,
      })
    }
    throw cause
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', forwardAbort)
  }

  if (!response.ok) {
    if (response.status === 401 && AUTH_ENABLED) {
      setToken(null)
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT))
    }
    throw await toApiError(response)
  }

  return response
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options)
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** Скачивание файла: сохраняем имя из Content-Disposition, если бэкенд его прислал. */
export async function apiDownload(path: string, params: QueryParams, fallbackName: string) {
  const response = await send(path, { params, accept: '*/*', timeoutMs: DOWNLOAD_TIMEOUT_MS })
  const blob = await response.blob()

  const disposition = response.headers.get('Content-Disposition') ?? ''
  const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  const plainMatch = /filename="?([^";]+)"?/i.exec(disposition)
  const name = utf8Match
    ? decodeURIComponent(utf8Match[1])
    : plainMatch
      ? plainMatch[1]
      : fallbackName

  triggerDownload(blob, name)
}

export function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Отзывать blob-ссылку синхронно нельзя: click() только ставит скачивание в очередь,
  // и к моменту чтения ссылки её уже нет — файл молча не появляется (заметнее всего в Safari).
  setTimeout(() => URL.revokeObjectURL(url), 40_000)
}
