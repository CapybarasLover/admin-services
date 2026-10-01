import {
  MoreHorizontal,
  PackageMinus,
  PackagePlus,
  PackageSearch,
  Pencil,
  Plus,
  Search,
  ShoppingCart,
  Trash2,
  X,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { AddProductDialog } from '@/components/AddProductDialog'
import { AdmissionDialog } from '@/components/AdmissionDialog'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EditProductDialog } from '@/components/EditProductDialog'
import { EmptyState } from '@/components/EmptyState'
import { OperationDialog, type OutgoingOperationType } from '@/components/OperationDialog'
import { StatusBadge } from '@/components/StatusBadge'
import { TableSkeleton } from '@/components/TableSkeleton'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useDeleteProduct } from '@/hooks/useStorageMutations'
import { useAuth } from '@/lib/auth'
import { OPERATION_META, STATUS_META } from '@/lib/constants'
import { showApiError } from '@/lib/errors'
import { formatMoney, pieces } from '@/lib/format'
import { useStorageContext } from '@/pages/StorageLayout'
import type { ItemStatus, StorageItemDto } from '@/types/api'

export function ItemsTab() {
  const { storageId, storage, isLoading } = useStorageContext()
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState('')
  const [adding, setAdding] = useState(false)
  const [admitting, setAdmitting] = useState(false)
  const [outgoing, setOutgoing] = useState<OutgoingOperationType | null>(null)
  const [editing, setEditing] = useState<StorageItemDto | null>(null)
  const [pendingDeletion, setPendingDeletion] = useState<StorageItemDto | null>(null)
  const { isAdmin } = useAuth()
  const deleteProduct = useDeleteProduct(storageId)

  const statusFilter = searchParams.get('status') as ItemStatus | null
  const items = useMemo(() => storage?.storageItemListDto ?? [], [storage])

  // Проблемы всегда сверху: сначала закончившееся, потом заканчивающееся.
  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    return items
      .filter((item) => (statusFilter ? item.status === statusFilter : true))
      .filter((item) => (normalized ? item.name.toLowerCase().includes(normalized) : true))
      .slice()
      .sort(
        (a, b) =>
          STATUS_META[a.status].order - STATUS_META[b.status].order ||
          a.name.localeCompare(b.name, 'ru'),
      )
  }, [items, query, statusFilter])

  function clearStatusFilter() {
    const next = new URLSearchParams(searchParams)
    next.delete('status')
    setSearchParams(next, { replace: true })
  }

  async function confirmDeletion() {
    if (!pendingDeletion) return
    try {
      await deleteProduct.mutateAsync(pendingDeletion.id)
      toast.success(`Товар «${pendingDeletion.name}» удалён`, {
        description: 'Записи в журнале операций сохранены.',
      })
      setPendingDeletion(null)
    } catch (cause) {
      showApiError(cause, 'Не удалось удалить товар')
    }
  }

  const storageName = storage?.name ?? ''
  const filtered = Boolean(statusFilter) || query.trim().length > 0
  const hasStock = items.some((item) => item.count > 0)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-0 flex-1 sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-9"
            placeholder="Поиск по названию"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            aria-label="Поиск товара"
          />
        </div>

        {statusFilter && STATUS_META[statusFilter] ? (
          <button
            type="button"
            onClick={clearStatusFilter}
            className="inline-flex h-9 items-center gap-1.5 rounded-md border px-3 text-sm transition-colors hover:bg-accent"
          >
            <span className={STATUS_META[statusFilter].textClassName}>{STATUS_META[statusFilter].label}</span>
            <X className="size-3.5 text-muted-foreground" />
            <span className="sr-only">Сбросить фильтр по статусу</span>
          </button>
        ) : null}

        {/* Операции больше не привязаны к строке: товар выбирается внутри формы. */}
        <div className="order-last grid w-full grid-cols-3 gap-2 md:order-none md:mx-auto md:flex md:w-auto max-sm:[&_svg]:hidden">
          <Button variant="outline" disabled={!items.length} onClick={() => setAdmitting(true)}>
            <PackagePlus className={OPERATION_META.ADMISSION.textClassName} />
            {OPERATION_META.ADMISSION.action}
          </Button>
          <Button
            variant="outline"
            disabled={!hasStock}
            title={hasStock ? undefined : 'На складе нет остатков'}
            onClick={() => setOutgoing('SELL')}
          >
            <ShoppingCart className={OPERATION_META.SELL.textClassName} />
            {OPERATION_META.SELL.action}
          </Button>
          <Button
            variant="outline"
            disabled={!hasStock}
            title={hasStock ? undefined : 'На складе нет остатков'}
            onClick={() => setOutgoing('WRITE_OFF')}
          >
            <PackageMinus className={OPERATION_META.WRITE_OFF.textClassName} />
            {OPERATION_META.WRITE_OFF.action}
          </Button>
        </div>

        <Button className="ml-auto" onClick={() => setAdding(true)}>
          <Plus />
          Товар
        </Button>
      </div>

      <div className="rounded-xl border bg-card">
        {isLoading ? (
          <TableSkeleton columns={7} />
        ) : !items.length ? (
          <EmptyState
            icon={PackagePlus}
            title="На складе пока нет товаров"
            description="Добавьте позицию, чтобы проводить по ней поступления, продажи и списания."
            action={<Button onClick={() => setAdding(true)}>Добавить товар</Button>}
          />
        ) : !visible.length ? (
          <EmptyState
            icon={PackageSearch}
            title="Ничего не найдено"
            description="Под текущий поиск и фильтр не подходит ни одна позиция."
            action={
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setQuery('')
                  clearStatusFilter()
                }}
              >
                Сбросить
              </Button>
            }
          />
        ) : (
          <>
            <div className="hidden md:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Название</TableHead>
                    <TableHead className="text-right">Остаток</TableHead>
                    <TableHead>Статус</TableHead>
                    <TableHead className="text-right">Закупка за ед.</TableHead>
                    <TableHead className="text-right">Цена за ед.</TableHead>
                    <TableHead className="text-right">Стоимость остатка</TableHead>
                    <TableHead className="w-12" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {visible.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.name}</TableCell>
                      <TableCell className="tabular text-right">{item.count}</TableCell>
                      <TableCell>
                        <StatusBadge status={item.status} />
                      </TableCell>
                      <TableCell className="tabular text-right text-muted-foreground">
                        {formatMoney(item.buyCost)}
                      </TableCell>
                      <TableCell className="tabular text-right">{formatMoney(item.cost)}</TableCell>
                      <TableCell className="tabular text-right">{formatMoney(item.cost * item.count)}</TableCell>
                      <TableCell className="text-right">
                        <RowActions
                          item={item}
                          isAdmin={isAdmin}
                          onEdit={() => setEditing(item)}
                          onDelete={() => setPendingDeletion(item)}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <ul className="divide-y md:hidden">
              {visible.map((item) => (
                <li key={item.id} className="flex items-start gap-3 p-4">
                  <div className="min-w-0 flex-1 space-y-2">
                    <p className="font-medium">{item.name}</p>
                    <StatusBadge status={item.status} />
                    <p className="tabular text-sm text-muted-foreground">
                      {pieces(item.count)} × {formatMoney(item.cost)} = {formatMoney(item.cost * item.count)}
                    </p>
                    {item.buyCost !== null ? (
                      <p className="tabular text-xs text-muted-foreground">Закупка {formatMoney(item.buyCost)} за шт.</p>
                    ) : null}
                  </div>
                  <RowActions
                    item={item}
                    isAdmin={isAdmin}
                    onEdit={() => setEditing(item)}
                    onDelete={() => setPendingDeletion(item)}
                  />
                </li>
              ))}
            </ul>
          </>
        )}
      </div>

      {filtered && visible.length > 0 ? (
        <p className="text-xs text-muted-foreground">
          Показано {visible.length} из {items.length}
        </p>
      ) : null}

      <AddProductDialog
        storageId={storageId}
        storageName={storageName}
        open={adding}
        onOpenChange={setAdding}
      />

      <AdmissionDialog
        storageId={storageId}
        storageName={storageName}
        items={items}
        open={admitting}
        onClose={() => setAdmitting(false)}
      />

      <OperationDialog
        storageId={storageId}
        storageName={storageName}
        items={items}
        type={outgoing}
        onClose={() => setOutgoing(null)}
      />

      <EditProductDialog
        storageId={storageId}
        storageName={storageName}
        item={editing}
        onClose={() => setEditing(null)}
      />

      <ConfirmDialog
        open={pendingDeletion !== null}
        onOpenChange={(open) => (open ? null : setPendingDeletion(null))}
        title={`Удалить «${pendingDeletion?.name ?? ''}»?`}
        pending={deleteProduct.isPending}
        onConfirm={confirmDeletion}
        description={
          <>
            {pendingDeletion && pendingDeletion.count > 0 ? (
              <span className="mb-2 block font-medium text-destructive">
                На складе ещё {pieces(pendingDeletion.count)} на {formatMoney(pendingDeletion.cost * pendingDeletion.count)}.
              </span>
            ) : null}
            Позиция исчезнет со склада. Записи в журнале операций и в отчётах останутся —
            они хранят имя товара, а не ссылку на него.
          </>
        }
      />
    </div>
  )
}

/** Правка и удаление меняют карточку товара — это работа админа. */
function RowActions({
  item,
  isAdmin,
  onEdit,
  onDelete,
}: {
  item: StorageItemDto
  isAdmin: boolean
  onEdit: () => void
  onDelete: () => void
}) {
  if (!isAdmin) return null
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={`Действия с товаром ${item.name}`}>
          <MoreHorizontal />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={onEdit}>
          <Pencil />
          Изменить
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={onDelete}>
          <Trash2 />
          Удалить
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
