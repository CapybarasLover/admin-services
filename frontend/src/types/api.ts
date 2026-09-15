// Зеркало DTO бэкенда. Имена полей повторяют json как есть,
// включая storageItemListDto — переименовывать на клиенте нечего.

export type ItemStatus = 'ENOUGH' | 'FEW' | 'OUT'
export type OperationType = 'ADMISSION' | 'SELL' | 'WRITE_OFF' | 'CANCELLATION'
export type Role = 'ADMIN' | 'WORKER'

/** Отмену нельзя провести руками: её создаёт бэкенд при удалении операции. */
export type ExecutableOperationType = Exclude<OperationType, 'CANCELLATION'>

export interface StorageInfoDto {
  id: number
  name: string
}

export interface StorageItemDto {
  id: number
  name: string
  count: number
  status: ItemStatus
  cost: number
}

export interface StorageDto {
  name: string
  storageItemListDto: StorageItemDto[]
}

export interface OperationDto {
  id: number
  storageName: string
  operationType: OperationType
  productName: string
  amount: number
  operationDateTime: string
  operationCost: number | null
  comment: string | null
  /** Операция удалена: остаток откачен, в отчёты она уже не попадает. */
  isCanceled: boolean
  /** У отменяющей операции — id той, которую она отменила. */
  cancelsOperationId: number | null
}

export interface PageDto<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
  numberOfElements: number
}

export interface StorageStats {
  admissionsCount: number
  admissionsTotal: number
  sellsCount: number
  sellsTotal: number
  writeOffsCount: number
  writeOffsTotal: number
  spending: number
  revenue: number
  profit: number
}

export interface ProductStats {
  admissionsCount: number
  admissionsTotal: number
  sellsCount: number
  sellsTotal: number
  writeOffsCount: number
  writeOffsTotal: number
  productSpending: number
  productRevenue: number
  productProfit: number
}

export interface SummaryReportDto {
  storageName: string
  dateFrom: string
  dateTo: string
  generatedAt: string
  currentStock: StorageItemDto[]
  storageStats: StorageStats
  productStats: Record<string, ProductStats>
}

export interface AuthUser {
  username: string
  role: Role
}

export interface AuthResponse extends AuthUser {
  token: string
}

export interface OperationRequest {
  operationType: ExecutableOperationType
  productName: string
  count: number
  operationCost?: number
  comment?: string
}

export interface OperationFilterParams {
  storageName?: string
  operationType?: OperationType
  productName?: string
  dateFrom?: string
  dateTo?: string
}

export interface PageParams {
  page?: number
  size?: number
  sort?: string
}
