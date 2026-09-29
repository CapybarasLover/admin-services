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
import { useExecuteOperation } from '@/hooks/useOperations'
import { LIMITS, OPERATION_META } from '@/lib/constants'
import { showApiError } from '@/lib/errors'
import { formatMoney, pieces } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ExecutableOperationType, StorageItemDto } from '@/types/api'

interface OperationDialogProps {
  storageId: number
  storageName: string
  item: StorageItemDto | null
  type: ExecutableOperationType | null
  onClose: () => void
}

function round2(value: number) {
  return Math.round(value * 100) / 100
}

function toNumber(raw: string) {
  return Number(raw.replace(',', '.'))
}

export function OperationDialog({ storageId, storageName, item, type, onClose }: OperationDialogProps) {
  const [count, setCount] = useState('')
  const [comment, setComment] = useState('')
  const [admissionCost, setAdmissionCost] = useState('')
  const execute = useExecuteOperation(storageId)

  const open = item !== null && type !== null

  // Каждое открытие — чистая форма.
  useEffect(() => {
    if (!open || !item) return
    setCount('')
    setComment('')
    setAdmissionCost('')
  }, [open, item])

  if (!open || !item || !type) return null

  const meta = OPERATION_META[type]
  const isAdmission = type === 'ADMISSION'
  const available = item.count

  const parsedCount = toNumber(count)
  const countValid = count !== '' && Number.isInteger(parsedCount) && parsedCount > 0
  const exceedsStock = !isAdmission && countValid && parsedCount > available

  // Поступление записывается только полной суммой: цена в карточке товара —
  // это цена продажи, умножать её на количество закупки бессмысленно.
  const parsedAdmissionCost = toNumber(admissionCost)
  const admissionTotal =
    admissionCost !== '' && Number.isFinite(parsedAdmissionCost) && parsedAdmissionCost > 0
      ? round2(parsedAdmissionCost)
      : null

  const settlementTotal = countValid ? round2(item.cost * parsedCount) : null

  const commentTooLong = comment.length > LIMITS.comment
  const canSubmit =
    countValid &&
    !exceedsStock &&
    !commentTooLong &&
    (!isAdmission || (admissionTotal !== null && admissionTotal > 0)) &&
    !execute.isPending

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!canSubmit || !item || !type) return
    try {
      await execute.mutateAsync({
        operationType: type,
        productName: item.name,
        count: parsedCount,
        operationCost: isAdmission ? (admissionTotal ?? undefined) : undefined,
        comment: comment.trim() || undefined,
      })
      const remainder = isAdmission ? available + parsedCount : available - parsedCount
      toast.success(`${meta.label}: ${item.name}, ${pieces(parsedCount)}`, {
        description: `Остаток на складе: ${pieces(remainder)}`,
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
          <DialogTitle className="flex items-center gap-2">
            <span className={meta.textClassName}>{meta.action}</span>
            <span aria-hidden className="text-muted-foreground">
              ·
            </span>
            <span className="truncate">{item.name}</span>
          </DialogTitle>
          <DialogDescription>
            {isAdmission
              ? `Склад «${storageName}»`
              : `Склад «${storageName}» · цена в карточке ${formatMoney(item.cost)} за единицу`}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="operation-count">Количество, шт.</Label>
            <Input
              id="operation-count"
              type="number"
              inputMode="numeric"
              min="1"
              step="1"
              max={isAdmission ? undefined : available}
              autoFocus
              value={count}
              onChange={(event) => setCount(event.target.value)}
              aria-invalid={exceedsStock}
              aria-describedby="operation-count-hint"
            />
            <p
              id="operation-count-hint"
              className={cn('text-xs', exceedsStock ? 'text-destructive' : 'text-muted-foreground')}
            >
              {isAdmission
                ? `Сейчас на складе ${pieces(available)}`
                : exceedsStock
                  ? `На складе только ${pieces(available)} — больше провести нельзя`
                  : `Доступно: ${pieces(available)}`}
            </p>
          </div>

          {isAdmission ? (
            <AdmissionCostFields value={admissionCost} onChange={setAdmissionCost} />
          ) : (
            <div className="space-y-1.5">
              <Label htmlFor="operation-settlement">
                {type === 'SELL' ? 'Сумма продажи' : 'Стоимость списанного'}
              </Label>
              <Input
                id="operation-settlement"
                readOnly
                tabIndex={-1}
                className="tabular bg-muted"
                value={settlementTotal === null ? '—' : formatMoney(settlementTotal)}
              />
              <p className="text-xs text-muted-foreground">
                {countValid ? `${pieces(parsedCount)} × ${formatMoney(item.cost)}. ` : ''}
                {type === 'SELL'
                  ? 'Сумму считает сервер по цене из карточки — вручную её не задать.'
                  : 'Списание не даёт выручки: в отчёте оно учитывается только в штуках.'}
              </p>
            </div>
          )}

          <div className="space-y-1.5">
            <div className="flex items-baseline justify-between">
              <Label htmlFor="operation-comment">Комментарий</Label>
              <span className="tabular text-xs text-muted-foreground">
                {comment.length}/{LIMITS.comment}
              </span>
            </div>
            <Input
              id="operation-comment"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              placeholder="Необязательно"
              aria-invalid={commentTooLong}
            />
            {commentTooLong ? (
              <p className="text-xs text-destructive">Не длиннее {LIMITS.comment} символов</p>
            ) : null}
          </div>

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
 * Бэкенд ждёт стоимость ВСЕЙ партии, и ввести её можно только руками:
 * цена в карточке товара — это цена продажи, считать по ней закупку нельзя.
 */
function AdmissionCostFields({
  value,
  onChange,
}: {
  value: string
  onChange: (value: string) => void
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor="admission-total">Сумма поступления, ₽</Label>
      <Input
        id="admission-total"
        type="number"
        inputMode="decimal"
        min="0"
        step="0.01"
        placeholder="Сколько заплатили за всю партию"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
      <p className="text-xs text-muted-foreground">
        Полная стоимость закупки целиком, а не цена за штуку. Цена в карточке товара при
        поступлении не пересчитывается.
      </p>
    </div>
  )
}
