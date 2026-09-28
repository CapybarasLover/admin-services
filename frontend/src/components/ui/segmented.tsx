import { cn } from '@/lib/utils'

export interface SegmentedOption<T extends string> {
  value: T
  label: string
  /** Активный сегмент можно подкрасить в цвет сущности, которую он фильтрует. */
  activeClassName?: string
}

interface SegmentedProps<T extends string> {
  value: T
  onValueChange: (value: T) => void
  options: SegmentedOption<T>[]
  label: string
  className?: string
  size?: 'sm' | 'default'
}

export function Segmented<T extends string>({
  value,
  onValueChange,
  options,
  label,
  className,
  size = 'default',
}: SegmentedProps<T>) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={cn('flex w-full flex-wrap gap-1 rounded-lg border bg-muted/60 p-1', className)}
    >
      {options.map((option) => {
        const active = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onValueChange(option.value)}
            className={cn(
              'flex-1 cursor-pointer whitespace-nowrap rounded-md px-3 font-medium transition-colors',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              size === 'sm' ? 'h-7 text-xs' : 'h-8 text-sm',
              active
                ? cn('bg-violet text-accent-foreground', option.activeClassName)
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
