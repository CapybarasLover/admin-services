import { Download, FileJson, FileText, Inbox } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { DateRangePicker } from '@/components/DateRangePicker'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { StatCard } from '@/components/StatCard'
import { StatusBadge } from '@/components/StatusBadge'
import { TableSkeleton } from '@/components/TableSkeleton'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useReport } from '@/hooks/useReport'
import { toast } from 'sonner'
import { ApiError, apiDownload, triggerDownload } from '@/lib/api'
import { RANGE_PRESETS, validateRange } from '@/lib/dates'
import { showApiError } from '@/lib/errors'
import { formatDateTime, formatMoney, formatNumber, pieces } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useStorageContext } from '@/pages/StorageLayout'
import type { SummaryReportDto } from '@/types/api'

const DEFAULT_RANGE = RANGE_PRESETS.find((preset) => preset.key === 'month')!.build()

export function ReportTab() {
  const { storage, isLoading: storageLoading } = useStorageContext()
  const [searchParams, setSearchParams] = useSearchParams()
  const [downloading, setDownloading] = useState<'pdf' | 'json' | null>(null)

  const dateFrom = searchParams.get('from') ?? DEFAULT_RANGE.dateFrom
  const dateTo = searchParams.get('to') ?? DEFAULT_RANGE.dateTo
  // У /report/summary параметры без аннотаций — период проверяет только фронт.
  const dateError = validateRange(dateFrom, dateTo)

  const query = useReport(storage?.name, dateFrom, dateTo, !dateError)
  const report = query.data

  const productRows = useMemo(() => {
    if (!report) return []
    return Object.entries(report.productStats).sort(
      ([, a], [, b]) => b.productRevenue - a.productRevenue,
    )
  }, [report])

  function setRange(range: { dateFrom: string; dateTo: string }) {
    const next = new URLSearchParams(searchParams)
    next.set('from', range.dateFrom)
    next.set('to', range.dateTo)
    setSearchParams(next, { replace: true })
  }

  async function downloadPdf() {
    if (!storage?.name) return
    setDownloading('pdf')
    try {
      await apiDownload(
        '/report/pdf',
        {
          storageName: storage.name,
          dateFrom,
          dateTo,
          // Печатаем в той же теме, в которой пользователь сейчас смотрит интерфейс.
          theme: document.documentElement.classList.contains('dark') ? 'dark' : 'light',
        },
        `report-${storage.name}-${dateFrom}-${dateTo}.pdf`,
      )
    } catch (cause) {
      // Java-прокси к Python-рендереру ещё не подключён: 404 от Spring
      // приходит с техническим текстом, который пользователю ничего не говорит.
      if (cause instanceof ApiError && cause.status === 404) {
        toast.error('Выгрузка PDF недоступна', {
          description: 'Сервис формирования PDF ещё не подключён к бэкенду. Отчёт можно скачать в JSON.',
        })
      } else {
        showApiError(cause, 'Не удалось получить PDF')
      }
    } finally {
      setDownloading(null)
    }
  }

  function downloadJson() {
    if (!report) return
    setDownloading('json')
    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' })
    triggerDownload(blob, `report-${report.storageName}-${dateFrom}-${dateTo}.json`)
    setDownloading(null)
  }

  const stats = report?.storageStats
  const empty =
    stats !== undefined &&
    stats.admissionsCount === 0 &&
    stats.sellsCount === 0 &&
    stats.writeOffsCount === 0

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4 rounded-xl border bg-card p-4">
        <DateRangePicker
          idPrefix="report"
          value={{ dateFrom, dateTo }}
          onChange={setRange}
          error={dateError}
        />
        <div className="flex gap-2">
          <Button variant="outline" onClick={downloadJson} disabled={!report || downloading !== null}>
            <FileJson />
            JSON
          </Button>
          <Button onClick={downloadPdf} disabled={!storage?.name || downloading !== null}>
            {downloading === 'pdf' ? <Download className="animate-pulse" /> : <FileText />}
            {downloading === 'pdf' ? 'Готовим…' : 'Скачать PDF'}
          </Button>
        </div>
      </div>

      {dateError ? (
        <div className="rounded-xl border bg-card">
          <EmptyState icon={Inbox} title="Поправьте период" description={dateError} />
        </div>
      ) : storageLoading || query.isLoading ? (
        <ReportSkeleton />
      ) : query.isError ? (
        <div className="rounded-xl border bg-card">
          <ErrorState error={query.error} onRetry={() => void query.refetch()} />
        </div>
      ) : report ? (
        <>
          <ReportKpis report={report} />

          <section className="rounded-xl border bg-card">
            <h2 className="eyebrow eyebrow--mark border-b px-4 py-3.5">По товарам</h2>
            {empty || !productRows.length ? (
              <EmptyState
                icon={Inbox}
                title="За период операций не было"
                description="Выберите другой диапазон дат или проведите операции по товарам этого склада."
              />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Товар</TableHead>
                    <TableHead className="text-right">Поступления</TableHead>
                    <TableHead className="text-right">Продажи</TableHead>
                    <TableHead className="text-right">Списания</TableHead>
                    <TableHead className="text-right">Затраты</TableHead>
                    <TableHead className="text-right">Выручка</TableHead>
                    <TableHead className="text-right">Прибыль</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {productRows.map(([name, product]) => (
                    <TableRow key={name}>
                      <TableCell className="font-medium">{name}</TableCell>
                      <TableCell className="tabular text-right">
                        <OpsCell ops={product.admissionsCount} total={product.admissionsTotal} />
                      </TableCell>
                      <TableCell className="tabular text-right">
                        <OpsCell ops={product.sellsCount} total={product.sellsTotal} />
                      </TableCell>
                      <TableCell className="tabular text-right">
                        <OpsCell ops={product.writeOffsCount} total={product.writeOffsTotal} />
                      </TableCell>
                      <TableCell className="tabular text-right">{formatMoney(product.productSpending)}</TableCell>
                      <TableCell className="tabular text-right">{formatMoney(product.productRevenue)}</TableCell>
                      <TableCell
                        className={cn(
                          'tabular text-right font-medium',
                          product.productProfit > 0 && 'text-positive',
                          product.productProfit < 0 && 'text-negative',
                        )}
                      >
                        {formatMoney(product.productProfit)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </section>

          <section className="rounded-xl border bg-card">
            <h2 className="eyebrow eyebrow--mark border-b px-4 py-3.5">Остатки на сейчас</h2>
            {!report.currentStock.length ? (
              <EmptyState icon={Inbox} title="На складе нет позиций" />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Товар</TableHead>
                    <TableHead className="text-right">Остаток</TableHead>
                    <TableHead>Статус</TableHead>
                    <TableHead className="text-right">Цена за ед.</TableHead>
                    <TableHead className="text-right">Стоимость остатка</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {report.currentStock.map((item) => (
                    <TableRow key={item.id ?? item.name}>
                      <TableCell className="font-medium">{item.name}</TableCell>
                      <TableCell className="tabular text-right">{item.count}</TableCell>
                      <TableCell>
                        <StatusBadge status={item.status} />
                      </TableCell>
                      <TableCell className="tabular text-right">{formatMoney(item.cost)}</TableCell>
                      <TableCell className="tabular text-right">{formatMoney(item.cost * item.count)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </section>

          <p className="text-xs text-muted-foreground">Сформирован: {formatDateTime(report.generatedAt)}</p>
        </>
      ) : null}
    </div>
  )
}

function ReportKpis({ report }: { report: SummaryReportDto }) {
  const stats = report.storageStats
  return (
    <>
      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard label="Закупки" value={formatMoney(stats.spending)} hint="Сумма поступлений за период" />
        <StatCard label="Выручка" value={formatMoney(stats.revenue)} hint="Сумма продаж за период" />
        <StatCard
          label="Прибыль"
          value={formatMoney(stats.profit)}
          valueClassName={cn(stats.profit > 0 && 'text-positive', stats.profit < 0 && 'text-negative')}
          // Кассовый метод, а не себестоимость проданного: крупная закупка
          // внутри периода честно уводит цифру в минус.
          hint="Продажи − Закупки за период"
        />
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard
          label="Поступления"
          value={formatNumber(stats.admissionsCount)}
          hint={`${pieces(stats.admissionsTotal)} принято`}
        />
        <StatCard
          label="Продажи"
          value={formatNumber(stats.sellsCount)}
          hint={`${pieces(stats.sellsTotal)} продано`}
        />
        <StatCard
          label="Списания"
          value={formatNumber(stats.writeOffsCount)}
          hint={`${pieces(stats.writeOffsTotal)} списано`}
        />
      </div>
    </>
  )
}

function OpsCell({ ops, total }: { ops: number; total: number }) {
  if (!ops) return <span className="text-muted-foreground">—</span>
  return (
    <span>
      {formatNumber(ops)}
      <span className="text-muted-foreground"> / {formatNumber(total)} шт.</span>
    </span>
  )
}

function ReportSkeleton() {
  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <Skeleton key={index} className="h-24" />
        ))}
      </div>
      <div className="rounded-xl border bg-card">
        <TableSkeleton columns={7} />
      </div>
    </div>
  )
}
