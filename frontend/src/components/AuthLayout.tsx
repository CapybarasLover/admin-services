import type { ReactNode } from 'react'
import { Warehouse } from 'lucide-react'
import { AUTH_ENABLED } from '@/lib/api'

export function AuthLayout({
  title,
  description,
  children,
  footer,
}: {
  title: string
  description: string
  children: ReactNode
  footer: ReactNode
}) {
  return (
    <div className="relative isolate flex min-h-dvh items-center justify-center overflow-hidden bg-background px-4 py-10">
      {/* Подсветка входного экрана — единственное место, где она уместна:
          дальше начинается работа с таблицами, и фон обязан молчать. */}
      <div className="aurora" aria-hidden>
        <span />
        <span />
      </div>

      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center gap-2">
          <div className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Warehouse className="size-4" />
          </div>
          <span className="display text-lg">Складской учёт</span>
        </div>

        <div className="card-glow rounded-xl p-6">
          <h1 className="display text-lg">{title}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{description}</p>
          <div className="mt-5">{children}</div>
        </div>

        <div className="mt-4 text-center text-sm text-muted-foreground">{footer}</div>

        {!AUTH_ENABLED ? (
          <p className="mt-4 rounded-lg border border-dashed border-line-2 p-3 text-center text-xs text-muted-foreground">
            Авторизация выключена через VITE_AUTH_ENABLED=false — вход не требуется.
          </p>
        ) : null}
      </div>
    </div>
  )
}
