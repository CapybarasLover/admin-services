import { Link, NavLink, useSearchParams } from 'react-router-dom'
import { Skeleton } from '@/components/ui/skeleton'
import { STATUS_META } from '@/lib/constants'
import { positions } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { StorageDto } from '@/types/api'

const TAB_LINKS = [
  { to: 'items', label: 'Товары' },
  { to: 'operations', label: 'Операции' },
  { to: 'report', label: 'Отчёт' },
]

interface WorkspaceHeaderProps {
  storageId: number
  storage?: StorageDto
  isLoading: boolean
}

export function WorkspaceHeader({ storageId, storage, isLoading }: WorkspaceHeaderProps) {
  const [searchParams] = useSearchParams()
  const activeStatus = searchParams.get('status')

  const items = storage?.storageItemListDto ?? []
  const outCount = items.filter((item) => item.status === 'OUT').length
  const fewCount = items.filter((item) => item.status === 'FEW').length

  return (
    <header className="border-b px-4 pt-4 sm:px-6">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        {isLoading ? (
          <Skeleton className="h-7 w-52" />
        ) : (
          <h1 className="display text-2xl">{storage?.name ?? 'Склад'}</h1>
        )}

        {storage ? (
          <div className="flex flex-wrap items-center gap-1.5 text-sm text-muted-foreground">
            <span className="tabular">{positions(items.length)}</span>
            {outCount > 0 ? (
              <SummaryChip
                storageId={storageId}
                status="OUT"
                active={activeStatus === 'OUT'}
                label={`${outCount} ${STATUS_META.OUT.label.toLowerCase()}`}
              />
            ) : null}
            {fewCount > 0 ? (
              <SummaryChip
                storageId={storageId}
                status="FEW"
                active={activeStatus === 'FEW'}
                label={`${fewCount} ${STATUS_META.FEW.label.toLowerCase()}`}
              />
            ) : null}
          </div>
        ) : null}
      </div>

      <nav className="mt-4 flex gap-1 pb-3" aria-label="Разделы склада">
        {TAB_LINKS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={({ isActive }) =>
              cn(
                'rounded-lg px-3.5 py-2 text-[0.8125rem] font-semibold transition-colors',
                isActive
                  ? 'bg-violet text-accent-foreground'
                  : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
              )
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>
    </header>
  )
}

/** Сводка кликабельная: она же фильтр таблицы товаров, состояние живёт в URL. */
function SummaryChip({
  storageId,
  status,
  label,
  active,
}: {
  storageId: number
  status: 'OUT' | 'FEW'
  label: string
  active: boolean
}) {
  const meta = STATUS_META[status]
  return (
    <>
      <span aria-hidden className="text-muted-foreground/50">
        ·
      </span>
      <Link
        to={active ? `/storages/${storageId}/items` : `/storages/${storageId}/items?status=${status}`}
        aria-pressed={active}
        className={cn(
          'inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 text-[0.6875rem] font-bold uppercase tracking-[0.08em] transition-colors',
          meta.badgeClassName,
          active ? 'ring-2 ring-ring ring-offset-1 ring-offset-background' : 'hover:opacity-80',
        )}
      >
        <span aria-hidden className={cn('size-1.5 rounded-full', meta.dotClassName)} />
        {label}
      </Link>
    </>
  )
}
