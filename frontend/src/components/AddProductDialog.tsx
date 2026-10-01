import { useState } from 'react'
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
import { useAddProduct } from '@/hooks/useStorageMutations'
import { ApiError } from '@/lib/api'
import { DEFAULT_COUNT_THRESHOLD, LIMITS } from '@/lib/constants'
import { showApiError } from '@/lib/errors'
import { parseDecimal } from '@/lib/format'
import { cn } from '@/lib/utils'

interface AddProductDialogProps {
  storageId: number
  storageName: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function AddProductDialog({ storageId, storageName, open, onOpenChange }: AddProductDialogProps) {
  const [name, setName] = useState('')
  const [cost, setCost] = useState('')
  const [buyCost, setBuyCost] = useState('')
  const [threshold, setThreshold] = useState(String(DEFAULT_COUNT_THRESHOLD))
  const [nameError, setNameError] = useState<string | null>(null)
  const addProduct = useAddProduct(storageId)

  const trimmed = name.trim()
  const parsedCost = parseDecimal(cost)
  const costValid = parsedCost !== null && parsedCost > 0
  const parsedBuyCost = parseDecimal(buyCost)
  const buyCostValid = parsedBuyCost !== null && parsedBuyCost > 0
  const parsedThreshold = parseDecimal(threshold)
  const thresholdValid = parsedThreshold !== null && Number.isInteger(parsedThreshold) && parsedThreshold > 0
  const nameTooLong = trimmed.length > LIMITS.productName
  const canSubmit =
    Boolean(trimmed) && !nameTooLong && costValid && buyCostValid && thresholdValid && !addProduct.isPending

  function close(next: boolean) {
    if (!next) {
      setName('')
      setCost('')
      setBuyCost('')
      setThreshold(String(DEFAULT_COUNT_THRESHOLD))
      setNameError(null)
    }
    onOpenChange(next)
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!canSubmit) return
    setNameError(null)
    try {
      await addProduct.mutateAsync({
        productName: trimmed,
        productCost: parsedCost!,
        buyCost: parsedBuyCost!,
        countThreshold: parsedThreshold!,
      })
      toast.success(`Товар «${trimmed}» добавлен`, { description: 'Остаток пока нулевой — проведите поступление.' })
      close(false)
    } catch (cause) {
      // Уникальный индекс (item, storage_id) отдаёт 409 — показываем прямо у поля.
      if (cause instanceof ApiError && cause.status === 409) {
        setNameError(cause.detail ?? 'Товар с таким именем уже есть на складе!')
        return
      }
      if (cause instanceof ApiError && cause.fieldError('productName')) {
        setNameError(cause.fieldError('productName')!)
        return
      }
      showApiError(cause, 'Не удалось добавить товар')
    }
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Новый товар</DialogTitle>
          <DialogDescription>Склад «{storageName}»</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <div className="flex items-baseline justify-between">
              <Label htmlFor="product-name">Название</Label>
              <span className="tabular text-xs text-muted-foreground">
                {trimmed.length}/{LIMITS.productName}
              </span>
            </div>
            <Input
              id="product-name"
              autoFocus
              value={name}
              onChange={(event) => {
                setName(event.target.value)
                setNameError(null)
              }}
              placeholder="Гвозди 100 мм"
              aria-invalid={Boolean(nameError) || nameTooLong}
            />
            {nameTooLong ? (
              <p className="text-xs text-destructive">Не длиннее {LIMITS.productName} символов</p>
            ) : null}
            {nameError ? (
              <p role="alert" className="text-xs text-destructive">
                {nameError}
              </p>
            ) : null}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="product-cost">Цена продажи, ₽</Label>
              <Input
                id="product-cost"
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                value={cost}
                onChange={(event) => setCost(event.target.value)}
                placeholder="80"
                aria-invalid={cost !== '' && !costValid}
              />
              <p className={cn('text-xs', cost !== '' && !costValid ? 'text-destructive' : 'text-muted-foreground')}>
                {cost !== '' && !costValid ? 'Цена должна быть больше нуля' : 'За единицу. По ней считаются продажи и списания.'}
              </p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="product-buy-cost">Цена закупки, ₽</Label>
              <Input
                id="product-buy-cost"
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                value={buyCost}
                onChange={(event) => setBuyCost(event.target.value)}
                placeholder="50"
                aria-invalid={buyCost !== '' && !buyCostValid}
              />
              <p
                className={cn(
                  'text-xs',
                  buyCost !== '' && !buyCostValid ? 'text-destructive' : 'text-muted-foreground',
                )}
              >
                {buyCost !== '' && !buyCostValid
                  ? 'Цена должна быть больше нуля'
                  : 'За единицу. Подставляется в форму поступления.'}
              </p>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="product-threshold">Порог «заканчивается», шт.</Label>
            <Input
              id="product-threshold"
              type="number"
              inputMode="numeric"
              min="1"
              step="1"
              value={threshold}
              onChange={(event) => setThreshold(event.target.value)}
              aria-invalid={!thresholdValid}
            />
            <p className={cn('text-xs', thresholdValid ? 'text-muted-foreground' : 'text-destructive')}>
              {thresholdValid
                ? `Когда на складе останется меньше ${parsedThreshold} шт., товар получит статус «Заканчивается».`
                : 'Целое число больше нуля'}
            </p>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              Отмена
            </Button>
            <Button type="submit" disabled={!canSubmit}>
              {addProduct.isPending ? 'Добавляем…' : 'Добавить'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
