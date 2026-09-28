import * as React from 'react'
import { cn } from '@/lib/utils'

export function Badge({ className, ...props }: React.ComponentProps<'span'>) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 text-[0.6875rem] font-bold uppercase tracking-[0.08em] whitespace-nowrap',
        className,
      )}
      {...props}
    />
  )
}
