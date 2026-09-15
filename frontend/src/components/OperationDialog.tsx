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
import { Segmented } from '@/components/ui/segmented'
import { useExecuteOperation } from '@/hooks/useOperations'
import { LIMITS, OPERATION_META } from '@/lib/constants'
import { showApiError } from '@/lib/errors'
import { formatMoney, pieces } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ExecutableOperationType, StorageItemDto } from '@/types/api'

type CostMode = 'unit' | 'batch'

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
  const [costMode, setCostMode] = useState<CostMode>('unit')
  const [unitPrice, setUnitPrice] = useState('')
  const [batchPrice, setBatchPrice] = useState('')
  const execute = useExecuteOperation(storageId)

  const open = item !== null && type !== null

  // Каждое открытие — чистая форма; цена за единицу подставляется из карточки.
  useEffect(() => {
    if (!open || !item) return
    setCount('')
    setComment('')
    setCostMode('unit')
    setUnitPrice(String(item.cost ?? ''))
    setBatchPrice('')
  }, [open, item])

  if (!open || !item || !type) return null

  const meta = OPERATION_META[type]
  const isAdmission = type === 'ADMISSION'
  const available = item.count

  const parsedCount = toNumber(count)
  const countValid = count !== '' && Number.isInteger(parsedCount) && parsedCount > 0
  const exceedsStock = !isAdmission && countValid && parsedCount > available

  const parsedUnitPrice = toNumber(unitPrice)
  const parsedBatchPrice = toNumber(batchPrice)

  // В API всегда уходит стоимость всей партии — как её ни ввели.
  const admissionTotal =
    costMode === 'unit'
      ? countValid && Number.isFinite(parsedUnitPrice) && parsedUnitPrice > 0
        ? round2(parsedUnitPrice * parsedCount)
        : null
      : Number.isFinite(parsedBatchPrice) && parsedBatchPrice > 0
        ? round2(parsedBatchPrice)
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
            Склад «{storageName}» · цена в карточке {formatMoney(item.cost)} за единицу
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
            <AdmissionCostFields
              costMode={costMode}
              onCostModeChange={setCostMode}
              unitPrice={unitPrice}
              onUnitPriceChange={setUnitPrice}
              batchPrice={batchPrice}
              onBatchPriceChange={setBatchPrice}
              total={admissionTotal}
            />
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
 * Бэкенд ждёт стоимость ВСЕЙ партии. Ввести цену за штуку в это поле —
 * самая дорогая ошибка на экране, поэтому способ ввода выбирается явно,
 * а итог всегда показан крупно.
 */
function AdmissionCostFields({
  costMode,
  onCostModeChange,
  unitPrice,
  onUnitPriceChange,
  batchPrice,
  onBatchPriceChange,
  total,
}: {
  costMode: CostMode
  onCostModeChange: (mode: CostMode) => void
  unitPrice: string
  onUnitPriceChange: (value: string) => void
  batchPrice: string
  onBatchPriceChange: (value: string) => void
  total: number | null
}) {
  return (
    <div className="space-y-2 rounded-lg border p-3">
      <Segmented<CostMode>
        label="Способ ввода стоимости"
        size="sm"
        value={costMode}
        onValueChange={onCostModeChange}
        options={[
          { value: 'unit', label: 'Цена за единицу' },
          { value: 'batch', label: 'Сумма за партию' },
        ]}
      />

      {costMode === 'unit' ? (
        <div className="space-y-1.5">
          <Label htmlFor="admission-unit-price">Цена закупки за единицу, ₽</Label>
          <Input
            id="admission-unit-price"
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            value={unitPrice}
            onChange={(event) => onUnitPriceChange(event.target.value)}
          />
        </div>
      ) : (
        <div className="space-y-1.5">
          <Label htmlFor="admission-batch-price">Сумма за всю партию, ₽</Label>
          <Input
            id="admission-batch-price"
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            value={batchPrice}
            onChange={(event) => onBatchPriceChange(event.target.value)}
          />
        </div>
      )}

      <div className="rounded-md bg-muted px-3 py-2">
        <p className="text-xs text-muted-foreground">Стоимость всей партии</p>
        <p className="tabular text-lg font-semibold">{total === null ? '—' : formatMoney(total)}</p>
      </div>

      <p className="text-xs text-muted-foreground">
        Цена в карточке товара при поступлении не пересчитывается — продажи и списания
        по-прежнему считаются по ней.
      </p>
    </div>
  )
}
