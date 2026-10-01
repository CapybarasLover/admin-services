import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { StatusBadge } from '@/components/StatusBadge'
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
import { useUpdateProduct, type UpdateProductInput } from '@/hooks/useStorageMutations'
import { showApiError } from '@/lib/errors'
import { formatMoney, parseDecimal, pieces } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ItemStatus, StorageItemDto } from '@/types/api'

interface EditProductDialogProps {
  storageId: number
  storageName: string
  item: StorageItemDto | null
  onClose: () => void
}

/** Та же логика, что в StorageItem.changeStatus на бэкенде, — для превью статуса. */
function statusFor(count: number, threshold: number): ItemStatus {
  if (count === 0) return 'OUT'
  return count < threshold ? 'FEW' : 'ENOUGH'
}

export function EditProductDialog({ storageId, storageName, item, onClose }: EditProductDialogProps) {
  const [cost, setCost] = useState('')
  const [buyCost, setBuyCost] = useState('')
  const [threshold, setThreshold] = useState('')
  const update = useUpdateProduct(storageId)

  const open = item !== null

  // Каждое открытие — значения из карточки.
  useEffect(() => {
    if (!item) return
    setCost(String(item.cost))
    setBuyCost(item.buyCost === null ? '' : String(item.buyCost))
    setThreshold(String(item.countThreshold))
  }, [item])

  if (!item) return null

  const parsedCost = parseDecimal(cost)
  const parsedBuyCost = parseDecimal(buyCost)
  const parsedThreshold = parseDecimal(threshold)

  const costValid = parsedCost !== null && parsedCost > 0
  // Цену закупки у старой позиции можно так и не задать — тогда её просто не шлём.
  const buyCostValid = buyCost === '' ? item.buyCost === null : parsedBuyCost !== null && parsedBuyCost > 0
  const thresholdValid = parsedThreshold !== null && Number.isInteger(parsedThreshold) && parsedThreshold > 0

  // PATCH принимает только изменённые поля — остальные бэкенд не трогает.
  const changes: Omit<UpdateProductInput, 'productId'> = {}
  if (costValid && parsedCost !== item.cost) changes.productCost = parsedCost
  if (buyCost !== '' && buyCostValid && parsedBuyCost !== item.buyCost) changes.buyCost = parsedBuyCost!
  if (thresholdValid && parsedThreshold !== item.countThreshold) changes.countThreshold = parsedThreshold

  const hasChanges = Object.keys(changes).length > 0
  const canSubmit = costValid && buyCostValid && thresholdValid && hasChanges && !update.isPending

  const nextStatus = thresholdValid ? statusFor(item.count, parsedThreshold) : item.status

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!canSubmit || !item) return
    try {
      await update.mutateAsync({ productId: item.id, ...changes })
      toast.success(`Товар «${item.name}» обновлён`)
      onClose()
    } catch (cause) {
      showApiError(cause, 'Не удалось изменить товар')
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? null : onClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="truncate">{item.name}</DialogTitle>
          <DialogDescription>
            Склад «{storageName}» · на складе {pieces(item.count)}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="edit-cost">Цена продажи, ₽</Label>
              <Input
                id="edit-cost"
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                autoFocus
                value={cost}
                onChange={(event) => setCost(event.target.value)}
                aria-invalid={!costValid}
              />
              <p className={cn('text-xs', costValid ? 'text-muted-foreground' : 'text-destructive')}>
                {costValid ? 'За единицу. По ней считаются продажи и списания.' : 'Цена должна быть больше нуля'}
              </p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="edit-buy-cost">Цена закупки, ₽</Label>
              <Input
                id="edit-buy-cost"
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                placeholder={item.buyCost === null ? 'Не задана' : undefined}
                value={buyCost}
                onChange={(event) => setBuyCost(event.target.value)}
                aria-invalid={!buyCostValid}
              />
              <p className={cn('text-xs', buyCostValid ? 'text-muted-foreground' : 'text-destructive')}>
                {buyCostValid
                  ? 'За единицу. Подставляется в форму поступления.'
                  : buyCost === ''
                    ? 'Убрать цену закупки нельзя — только заменить'
                    : 'Цена должна быть больше нуля'}
              </p>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="edit-threshold">Порог «заканчивается», шт.</Label>
            <Input
              id="edit-threshold"
              type="number"
              inputMode="numeric"
              min="1"
              step="1"
              value={threshold}
              onChange={(event) => setThreshold(event.target.value)}
              aria-invalid={!thresholdValid}
            />
            {thresholdValid ? (
              <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
                Меньше {pieces(parsedThreshold)} — «Заканчивается».{' '}
                {nextStatus === item.status ? 'Статус:' : 'Статус станет:'}
                <StatusBadge status={nextStatus} />
              </p>
            ) : (
              <p className="text-xs text-destructive">Целое число больше нуля</p>
            )}
          </div>

          {changes.productCost !== undefined && item.count > 0 ? (
            <p className="rounded-md bg-muted px-3 py-2 text-xs text-muted-foreground">
              Стоимость остатка: {formatMoney(item.cost * item.count)} →{' '}
              <span className="tabular font-medium text-foreground">{formatMoney(parsedCost! * item.count)}</span>.
              Уже проведённые продажи не пересчитываются.
            </p>
          ) : null}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Отмена
            </Button>
            <Button type="submit" disabled={!canSubmit}>
              {update.isPending ? 'Сохраняем…' : 'Сохранить'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
