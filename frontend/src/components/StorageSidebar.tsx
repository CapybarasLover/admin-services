import { LogOut, Package, Plus, Warehouse } from 'lucide-react'
import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { CreateStorageDialog } from '@/components/CreateStorageDialog'
import { ThemeToggle } from '@/components/ThemeToggle'
import { useStorages } from '@/hooks/useStorages'
import { useAuth } from '@/lib/auth'
import { ROLE_LABEL } from '@/lib/constants'
import { cn } from '@/lib/utils'

const TABS = ['items', 'operations', 'report'] as const
export type WorkspaceTab = (typeof TABS)[number]

/** Вкладка сохраняется при переключении склада: смотрим товары — останемся на товарах. */
export function useCurrentTab(): WorkspaceTab {
  const { pathname } = useLocation()
  const found = TABS.find((tab) => pathname.endsWith(`/${tab}`))
  return found ?? 'items'
}

export function StorageSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { data: storages, isLoading, isError } = useStorages()
  const { storageId } = useParams()
  const tab = useCurrentTab()
  const { user, logout } = useAuth()
  const [creating, setCreating] = useState(false)

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex items-center gap-2.5 px-4 py-4">
        <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-violet text-brand-ink">
          <Warehouse className="size-4" />
        </span>
        <span className="eyebrow flex-1">Склады</span>
        <Button
          variant="ghost"
          size="icon"
          className="size-7"
          aria-label="Создать склад"
          title="Создать склад"
          onClick={() => setCreating(true)}
        >
          <Plus />
        </Button>
      </div>

      <nav className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        {isLoading ? (
          <div className="space-y-1.5 px-1">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-9 w-full" />
            ))}
          </div>
        ) : isError ? (
          <p className="px-3 py-2 text-sm text-destructive">Список складов недоступен</p>
        ) : !storages?.length ? (
          <div className="px-3 py-6 text-center">
            <Package className="mx-auto size-5 text-muted-foreground" />
            <p className="mt-2 text-sm text-muted-foreground">Складов пока нет</p>
            <Button variant="outline" size="sm" className="mt-3" onClick={() => setCreating(true)}>
              Создать первый
            </Button>
          </div>
        ) : (
          <ul className="space-y-0.5">
            {storages.map((storage) => {
              const active = String(storage.id) === storageId
              return (
                <li key={storage.id}>
                  <Link
                    to={`/storages/${storage.id}/${tab}`}
                    onClick={onNavigate}
                    aria-current={active ? 'page' : undefined}
                    className={cn(
                      'flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors',
                      active
                        ? 'bg-violet font-semibold text-accent-foreground'
                        : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
                    )}
                  >
                    <span className="truncate">{storage.name}</span>
                    {active ? <span aria-hidden className="ml-auto size-1.5 rounded-full bg-brand" /> : null}
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </nav>

      <div className="border-t p-3">
        <div className="flex items-center gap-2">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{user?.username}</p>
            {user ? (
              <Badge className="mt-1 border-transparent bg-secondary text-secondary-foreground">
                {ROLE_LABEL[user.role]}
              </Badge>
            ) : null}
          </div>
          <ThemeToggle />
          <Button variant="ghost" size="icon" onClick={logout} aria-label="Выйти" title="Выйти">
            <LogOut />
          </Button>
        </div>
      </div>

      <CreateStorageDialog open={creating} onOpenChange={setCreating} />
    </div>
  )
}
