import type { ExecutableOperationType, ItemStatus, OperationType } from '@/types/api'

interface StatusMeta {
  label: string
  /** Порядок критичности: чем меньше, тем выше в таблице. */
  order: number
  badgeClassName: string
  dotClassName: string
  textClassName: string
}

export const STATUS_META: Record<ItemStatus, StatusMeta> = {
  OUT: {
    label: 'Закончился',
    order: 0,
    badgeClassName: 'bg-status-out-bg text-status-out border-status-out/25',
    dotClassName: 'bg-status-out',
    textClassName: 'text-status-out',
  },
  FEW: {
    label: 'Заканчивается',
    order: 1,
    badgeClassName: 'bg-status-few-bg text-status-few border-status-few/25',
    dotClassName: 'bg-status-few',
    textClassName: 'text-status-few',
  },
  ENOUGH: {
    label: 'В наличии',
    order: 2,
    badgeClassName: 'bg-status-enough-bg text-status-enough border-status-enough/25',
    dotClassName: 'bg-status-enough',
    textClassName: 'text-status-enough',
  },
}

/** Порог из StorageItem.changeStatus: меньше 10 штук — FEW. */
export const FEW_THRESHOLD = 10

interface OperationMeta {
  label: string
  /** Подпись действия в меню строки и в заголовке диалога. */
  action: string
  /** Винительный падеж для «Провести …» на кнопке. */
  accusative: string
  badgeClassName: string
  textClassName: string
}

export const OPERATION_META: Record<OperationType, OperationMeta> = {
  ADMISSION: {
    label: 'Поступление',
    action: 'Поступление',
    accusative: 'поступление',
    badgeClassName: 'bg-op-admission-bg text-op-admission border-op-admission/25',
    textClassName: 'text-op-admission',
  },
  SELL: {
    label: 'Продажа',
    action: 'Продажа',
    accusative: 'продажу',
    badgeClassName: 'bg-op-sell-bg text-op-sell border-op-sell/25',
    textClassName: 'text-op-sell',
  },
  WRITE_OFF: {
    label: 'Списание',
    action: 'Списание',
    accusative: 'списание',
    badgeClassName: 'bg-op-writeoff-bg text-op-writeoff border-op-writeoff/25',
    textClassName: 'text-op-writeoff',
  },
  // Служебная запись, а не движение товара, — поэтому нейтральный серый.
  CANCELLATION: {
    label: 'Отмена',
    action: 'Отмена',
    accusative: 'отмену',
    badgeClassName: 'bg-muted text-muted-foreground border-border',
    textClassName: 'text-muted-foreground',
  },
}

/** Что пользователь проводит сам. Отмену за него создаёт бэкенд. */
export const OPERATION_TYPES: ExecutableOperationType[] = ['ADMISSION', 'SELL', 'WRITE_OFF']

/** В журнале фильтруем и по отменам — иначе их не найти. */
export const OPERATION_FILTER_TYPES: OperationType[] = [...OPERATION_TYPES, 'CANCELLATION']

export const ROLE_LABEL: Record<'ADMIN' | 'WORKER', string> = {
  ADMIN: 'Админ',
  WORKER: 'Работник',
}

/** Ограничения из аннотаций бэкенда — дублируем, чтобы не доводить до 400. */
export const LIMITS = {
  storageName: 25,
  productName: 100,
  comment: 255,
} as const

export const PAGE_SIZES = [20, 50, 100]
