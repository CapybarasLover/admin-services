import { Plus, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { CommentField, ProductSelect } from '@/components/OperationDialog'
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
import { formatMoney, parseDecimal, pieces, positions, round2 } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { StorageItemDto } from '@/types/api'

/** «Количество × цена» или «вся сумма руками» — бэкенду в обоих случаях уходит итог строки. */
type CostMode = 'unit' | 'total'

interface Line {
  key: number
  productId: string
  count: string
  unitPrice: string
  total: string
}

interface AdmissionDialogProps {
  storageId: number
  storageName: string
  items: StorageItemDto[]
  open: boolean
  onClose: () => void
}

interface EvaluatedLine {
  line: Line
  item: StorageItemDto | null
  count: number | null
  amount: number | null
  blank: boolean
  valid: boolean
}

function evaluate(line: Line, mode: CostMode, items: StorageItemDto[]): EvaluatedLine {
  const item = items.find((candidate) => String(candidate.id) === line.productId) ?? null
  const parsedCount = parseDecimal(line.count)
  const count = parsedCount !== null && Number.isInteger(parsedCount) && parsedCount > 0 ? parsedCount : null

  const unitPrice = parseDecimal(line.unitPrice)
  const total = parseDecimal(line.total)
  const amount =
    mode === 'unit'
      ? count !== null && unitPrice !== null && unitPrice > 0
        ? round2(unitPrice * count)
        : null
      : total !== null && total > 0
        ? round2(total)
        : null

  return {
    line,
    item,
    count,
    amount,
    blank: line.productId === '' && line.count === '',
    valid: item !== null && count !== null && amount !== null && amount > 0,
  }
}

export function AdmissionDialog({ storageId, storageName, items, open, onClose }: AdmissionDialogProps) {
  const nextKey = useRef(1)
  const newLine = (): Line => ({ key: nextKey.current++, productId: '', count: '', unitPrice: '', total: '' })

  const [mode, setMode] = useState<CostMode>('unit')
  const [lines, setLines] = useState<Line[]>(() => [newLine()])
  const [comment, setComment] = useState('')
  const [failedKey, setFailedKey] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const execute = useExecuteOperation(storageId)

  // Каждое открытие — чистая форма с одной пустой строкой.
  useEffect(() => {
    if (!open) return
    setMode('unit')
    setLines([newLine()])
    setComment('')
    setFailedKey(null)
  }, [open])

  const meta = OPERATION_META.ADMISSION
  const evaluated = lines.map((line) => evaluate(line, mode, items))
  // Пустую строку, добавленную «на всякий случай», молча пропускаем.
  const filled = evaluated.filter((entry) => !entry.blank)
  const grandTotal = round2(filled.reduce((sum, entry) => sum + (entry.amount ?? 0), 0))
  const totalPieces = filled.reduce((sum, entry) => sum + (entry.count ?? 0), 0)

  const commentTooLong = comment.length > LIMITS.comment
  const canSubmit = filled.length > 0 && filled.every((entry) => entry.valid) && !commentTooLong && !submitting
  const chosen = new Set(lines.map((line) => line.productId).filter(Boolean))
  const canAddLine = chosen.size < items.length && lines.every((line) => line.productId !== '')

  function patchLine(key: number, patch: Partial<Line>) {
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...patch } : line)))
    if (key === failedKey) setFailedKey(null)
  }

  function chooseProduct(key: number, productId: string) {
    const item = items.find((candidate) => String(candidate.id) === productId)
    // Цена закупки подставляется из карточки, но её можно поправить под конкретную партию.
    patchLine(key, { productId, unitPrice: item?.buyCost != null ? String(item.buyCost) : '' })
  }

  function removeLine(key: number) {
    setLines((current) => (current.length === 1 ? [newLine()] : current.filter((line) => line.key !== key)))
    if (key === failedKey) setFailedKey(null)
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setFailedKey(null)

    // Бэкенд проводит поступление по одному товару за запрос — идём строка за строкой.
    // Упасть может любая: тогда проведённые убираем из формы, а на упавшей останавливаемся,
    // чтобы повторная отправка не задвоила уже записанное.
    const done = new Set<number>()
    const trimmedComment = comment.trim() || undefined
    try {
      for (const entry of filled) {
        try {
          await execute.mutateAsync({
            operationType: 'ADMISSION',
            productName: entry.item!.name,
            count: entry.count!,
            operationCost: entry.amount!,
            comment: trimmedComment,
          })
          done.add(entry.line.key)
        } catch (cause) {
          setLines((current) => current.filter((line) => !done.has(line.key)))
          setFailedKey(entry.line.key)
          showApiError(cause, `Не удалось провести «${entry.item!.name}»`)
          if (done.size > 0) {
            toast.info(`Проведено ${done.size} из ${filled.length}`, {
              description: 'Проведённые строки убраны из формы — остальные можно отправить ещё раз.',
            })
          }
          return
        }
      }
    } finally {
      setSubmitting(false)
    }

    toast.success(
      filled.length === 1
        ? `${meta.label}: ${filled[0].item!.name}, ${pieces(filled[0].count!)}`
        : `${meta.label}: ${positions(filled.length)}, ${pieces(totalPieces)}`,
      { description: `На сумму ${formatMoney(grandTotal)}` },
    )
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next || submitting ? null : onClose())}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className={meta.textClassName}>{meta.action}</DialogTitle>
          <DialogDescription>Склад «{storageName}» · можно провести несколько товаров сразу</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <Segmented<CostMode>
            label="Как задать стоимость"
            size="sm"
            value={mode}
            onValueChange={setMode}
            options={[
              { value: 'unit', label: 'Количество × цена' },
              { value: 'total', label: 'Общей суммой' },
            ]}
          />

          <ul className="space-y-2">
            {evaluated.map((entry, index) => (
              <AdmissionLine
                key={entry.line.key}
                index={index}
                entry={entry}
                mode={mode}
                items={items}
                exclude={chosen}
                failed={entry.line.key === failedKey}
                disabled={submitting}
                onProductChange={(productId) => chooseProduct(entry.line.key, productId)}
                onChange={(patch) => patchLine(entry.line.key, patch)}
                onRemove={lines.length > 1 || !entry.blank ? () => removeLine(entry.line.key) : undefined}
              />
            ))}
          </ul>

          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={!canAddLine || submitting}
            onClick={() => setLines((current) => [...current, newLine()])}
          >
            <Plus />
            Ещё товар
          </Button>

          <div className="flex items-baseline justify-between gap-3 rounded-lg bg-muted px-3 py-2.5">
            <div>
              <p className="text-xs text-muted-foreground">Стоимость поступления</p>
              {filled.length > 1 ? (
                <p className="text-xs text-muted-foreground">
                  {positions(filled.length)} · {pieces(totalPieces)}
                </p>
              ) : null}
            </div>
            <p className="tabular text-lg font-semibold">{grandTotal > 0 ? formatMoney(grandTotal) : '—'}</p>
          </div>

          <CommentField
            id="admission-comment"
            value={comment}
            onChange={setComment}
            hint={filled.length > 1 ? 'Запишется к каждому товару поступления.' : undefined}
          />

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Отмена
            </Button>
            <Button type="submit" disabled={!canSubmit}>
              {submitting ? 'Проводим…' : `Провести ${meta.accusative}`}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function AdmissionLine({
  index,
  entry,
  mode,
  items,
  exclude,
  failed,
  disabled,
  onProductChange,
  onChange,
  onRemove,
}: {
  index: number
  entry: EvaluatedLine
  mode: CostMode
  items: StorageItemDto[]
  exclude: Set<string>
  failed: boolean
  disabled: boolean
  onProductChange: (productId: string) => void
  onChange: (patch: Partial<Line>) => void
  onRemove?: () => void
}) {
  const { line, item, count, amount } = entry
  const id = `admission-${line.key}`
  const countInvalid = line.count !== '' && count === null
  const unitPrice = parseDecimal(line.unitPrice)
  const unitInvalid = line.unitPrice !== '' && (unitPrice === null || unitPrice <= 0)
  const totalValue = parseDecimal(line.total)
  const totalInvalid = line.total !== '' && (totalValue === null || totalValue <= 0)

  let hint: string | null = null
  if (item && mode === 'unit') {
    if (item.buyCost === null) hint = 'В карточке нет цены закупки — введите вручную.'
    else if (unitPrice !== null && unitPrice !== item.buyCost)
      hint = `В карточке ${formatMoney(item.buyCost)} — для этой партии своя цена.`
  }

  return (
    <li className={cn('space-y-2 rounded-lg border p-3', failed && 'border-destructive/60')}>
      <div className="flex items-end gap-2">
        <div className="min-w-0 flex-1 space-y-1">
          <Label htmlFor={`${id}-product`} className="text-xs text-muted-foreground">
            Товар {index + 1}
          </Label>
          <ProductSelect
            id={`${id}-product`}
            items={items}
            value={line.productId}
            onValueChange={onProductChange}
            requireStock={false}
            exclude={exclude}
          />
        </div>
        {onRemove ? (
          <Button
            type="button"
            variant="ghost"
            size="icon"
            disabled={disabled}
            onClick={onRemove}
            aria-label={`Убрать строку ${index + 1}`}
          >
            <Trash2 />
          </Button>
        ) : null}
      </div>

      <div className={cn('grid gap-2', mode === 'unit' ? 'grid-cols-2 sm:grid-cols-3' : 'grid-cols-2')}>
        <div className="space-y-1">
          <Label htmlFor={`${id}-count`} className="text-xs text-muted-foreground">
            Количество, шт.
          </Label>
          <Input
            id={`${id}-count`}
            type="number"
            inputMode="numeric"
            min="1"
            step="1"
            value={line.count}
            onChange={(event) => onChange({ count: event.target.value })}
            aria-invalid={countInvalid}
          />
        </div>

        {mode === 'unit' ? (
          <>
            <div className="space-y-1">
              <Label htmlFor={`${id}-unit`} className="text-xs text-muted-foreground">
                Цена закупки, ₽/шт.
              </Label>
              <Input
                id={`${id}-unit`}
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                value={line.unitPrice}
                onChange={(event) => onChange({ unitPrice: event.target.value })}
                aria-invalid={unitInvalid}
              />
            </div>
            <div className="col-span-2 space-y-1 sm:col-span-1">
              <span className="block text-xs leading-none font-medium text-muted-foreground">Сумма</span>
              <p className="tabular flex h-9 items-center justify-end rounded-lg bg-muted px-3 text-sm font-medium">
                {amount === null ? '—' : formatMoney(amount)}
              </p>
            </div>
          </>
        ) : (
          <div className="space-y-1">
            <Label htmlFor={`${id}-total`} className="text-xs text-muted-foreground">
              Сумма за партию, ₽
            </Label>
            <Input
              id={`${id}-total`}
              type="number"
              inputMode="decimal"
              min="0"
              step="0.01"
              value={line.total}
              onChange={(event) => onChange({ total: event.target.value })}
              aria-invalid={totalInvalid}
            />
          </div>
        )}
      </div>

      {countInvalid || unitInvalid || totalInvalid ? (
        <p className="text-xs text-destructive">
          {countInvalid ? 'Количество — целое число больше нуля. ' : ''}
          {unitInvalid || totalInvalid ? 'Цена должна быть больше нуля.' : ''}
        </p>
      ) : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </li>
  )
}
