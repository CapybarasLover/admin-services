import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useExecuteOperation } from '@/hooks/useOperations'
import { LIMITS, OPERATION_META } from '@/lib/constants'
import { showApiError } from '@/lib/errors'
import { formatMoney, parseDecimal, pieces, round2 } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { StorageItemDto } from '@/types/api'

/** Поступление живёт в отдельной форме — у него несколько позиций и своя цена. */
export type OutgoingOperationType = 'SELL' | 'WRITE_OFF'

interface OperationDialogProps {
  storageId: number
  storageName: string
  items: StorageItemDto[]
  type: OutgoingOperationType | null
  onClose: () => void
}

export function OperationDialog({ storageId, storageName, items, type, onClose }: OperationDialogProps) {
  const [productId, setProductId] = useState('')
  const [count, setCount] = useState('')
  const [comment, setComment] = useState('')
  const execute = useExecuteOperation(storageId)

  const open = type !== null

  // Каждое открытие — чистая форма.
  useEffect(() => {
    if (!open) return
    setProductId('')
    setCount('')
    setComment('')
  }, [open])

  if (!type) return null

  const meta = OPERATION_META[type]
  const item = items.find((candidate) => String(candidate.id) === productId) ?? null
  const available = item?.count ?? 0

  const parsedCount = parseDecimal(count)
  const countValid = parsedCount !== null && Number.isInteger(parsedCount) && parsedCount > 0
  const exceedsStock = item !== null && countValid && parsedCount > available
  const settlementTotal = item && countValid ? round2(item.cost * parsedCount) : null

  const commentTooLong = comment.length > LIMITS.comment
  const canSubmit = item !== null && countValid && !exceedsStock && !commentTooLong && !execute.isPending

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!canSubmit || !item || !type || !countValid) return
    try {
      await execute.mutateAsync({
        operationType: type,
        productName: item.name,
        count: parsedCount,
        comment: comment.trim() || undefined,
      })
      toast.success(`${meta.label}: ${item.name}, ${pieces(parsedCount)}`, {
        description: `Остаток на складе: ${pieces(available - parsedCount)}`,
      })
      onClose()
    } catch (cause) {
      showApiError(cause, 'Не удалось провести операцию')
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? null : onClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className={meta.textClassName}>{meta.action}</DialogTitle>
          <DialogDescription>Склад «{storageName}»</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="operation-product">Товар</Label>
            <ProductSelect id="operation-product" items={items} value={productId} onValueChange={setProductId} />
            {item ? (
              <p className="text-xs text-muted-foreground">
                Цена в карточке {formatMoney(item.cost)} за единицу
              </p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="operation-count">Количество, шт.</Label>
            <Input
              id="operation-count"
              type="number"
              inputMode="numeric"
              min="1"
              step="1"
              max={item ? available : undefined}
              disabled={!item}
              value={count}
              onChange={(event) => setCount(event.target.value)}
              aria-invalid={exceedsStock}
              aria-describedby="operation-count-hint"
            />
            <p
              id="operation-count-hint"
              className={cn('text-xs', exceedsStock ? 'text-destructive' : 'text-muted-foreground')}
            >
              {!item
                ? 'Сначала выберите товар'
                : exceedsStock
                  ? `На складе только ${pieces(available)} — больше провести нельзя`
                  : `Доступно: ${pieces(available)}`}
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="operation-settlement">{type === 'SELL' ? 'Сумма продажи' : 'Стоимость списанного'}</Label>
            <Input
              id="operation-settlement"
              readOnly
              tabIndex={-1}
              className="tabular bg-muted"
              value={settlementTotal === null ? '—' : formatMoney(settlementTotal)}
            />
            <p className="text-xs text-muted-foreground">
              {item && countValid ? `${pieces(parsedCount)} × ${formatMoney(item.cost)}. ` : ''}
              {type === 'SELL'
                ? 'Сумму считает сервер по цене из карточки — вручную её не задать.'
                : 'Списание не даёт выручки: в отчёте оно учитывается только в штуках.'}
            </p>
          </div>

          <CommentField id="operation-comment" value={comment} onChange={setComment} />

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Отмена
            </Button>
            <Button type="submit" disabled={!canSubmit}>
              {execute.isPending ? 'Проводим…' : `Провести ${meta.accusative}`}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/**
 * Выбор позиции склада. Для продажи и списания позиции без остатка
 * видны, но выбрать их нельзя; уже выбранные в других строках — скрыты.
 */
export function ProductSelect({
  id,
  items,
  value,
  onValueChange,
  requireStock = true,
  exclude,
}: {
  id?: string
  items: StorageItemDto[]
  value: string
  onValueChange: (value: string) => void
  requireStock?: boolean
  exclude?: Set<string>
}) {
  const options = items
    .filter((item) => !exclude?.has(String(item.id)) || String(item.id) === value)
    .slice()
    .sort((a, b) => a.name.localeCompare(b.name, 'ru'))

  return (
    <Select value={value} onValueChange={onValueChange}>
      <SelectTrigger id={id}>
        <SelectValue placeholder="Выберите товар" />
      </SelectTrigger>
      <SelectContent>
        {options.map((item) => {
          const blocked = requireStock && item.count === 0
          return (
            <SelectItem key={item.id} value={String(item.id)} disabled={blocked}>
              {item.name}
              <span className="ml-2 text-xs text-muted-foreground">{blocked ? 'нет остатка' : pieces(item.count)}</span>
            </SelectItem>
          )
        })}
      </SelectContent>
    </Select>
  )
}

export function CommentField({
  id,
  value,
  onChange,
  hint,
}: {
  id: string
  value: string
  onChange: (value: string) => void
  hint?: string
}) {
  const tooLong = value.length > LIMITS.comment
  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between">
        <Label htmlFor={id}>Комментарий</Label>
        <span className="tabular text-xs text-muted-foreground">
          {value.length}/{LIMITS.comment}
        </span>
      </div>
      <Input
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="Необязательно"
        aria-invalid={tooLong}
      />
      {tooLong ? (
        <p className="text-xs text-destructive">Не длиннее {LIMITS.comment} символов</p>
      ) : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  )
}
