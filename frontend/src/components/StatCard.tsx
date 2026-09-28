import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface StatCardProps {
  label: string
  value: ReactNode
  hint?: ReactNode
  valueClassName?: string
  className?: string
}

export function StatCard({ label, value, hint, valueClassName, className }: StatCardProps) {
  return (
    <div className={cn('card-glow rounded-xl p-4', className)}>
      <p className="eyebrow eyebrow--mark">{label}</p>
      <p className={cn('tabular display mt-2.5 text-[1.75rem] text-brand', valueClassName)}>{value}</p>
      {hint ? <p className="mt-1.5 text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  )
}
