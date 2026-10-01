import { format, parseISO } from 'date-fns'
import { ru } from 'date-fns/locale'

const money = new Intl.NumberFormat('ru-RU', {
  style: 'currency',
  currency: 'RUB',
  maximumFractionDigits: 2,
})

const moneyCompact = new Intl.NumberFormat('ru-RU', {
  style: 'currency',
  currency: 'RUB',
  maximumFractionDigits: 0,
})

const number = new Intl.NumberFormat('ru-RU')

export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return money.format(value)
}

export function formatMoneyCompact(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return Number.isInteger(value) ? moneyCompact.format(value) : money.format(value)
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return number.format(value)
}

/** operationDateTime приходит ISO Instant. */
export function formatDateTime(iso: string): string {
  return format(parseISO(iso), 'd MMM yyyy, HH:mm', { locale: ru })
}

/** Календарная дата yyyy-MM-dd — берём как есть, без пересчёта таймзоны. */
export function formatIsoDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return format(new Date(year, month - 1, day), 'd MMMM yyyy', { locale: ru })
}

export function plural(count: number, one: string, few: string, many: string): string {
  const mod10 = count % 10
  const mod100 = count % 100
  if (mod10 === 1 && mod100 !== 11) return one
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few
  return many
}

export function pluralize(count: number, one: string, few: string, many: string): string {
  return `${formatNumber(count)} ${plural(count, one, few, many)}`
}

export const positions = (count: number) => pluralize(count, 'позиция', 'позиции', 'позиций')
export const pieces = (count: number) => pluralize(count, 'шт.', 'шт.', 'шт.')
export const operations = (count: number) => pluralize(count, 'операция', 'операции', 'операций')

/** Число из поля ввода: запятая сойдёт за точку, пустое и мусор — null. */
export function parseDecimal(raw: string): number | null {
  if (raw.trim() === '') return null
  const value = Number(raw.replace(',', '.'))
  return Number.isFinite(value) ? value : null
}

/** Деньги дальше копеек не тащим — ни в итогах, ни в запросе. */
export function round2(value: number): number {
  return Math.round(value * 100) / 100
}
