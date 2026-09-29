import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '@/lib/api'
import { queryKeys } from '@/hooks/keys'
import type { OperationDto, OperationFilterParams, OperationRequest, PageDto, PageParams } from '@/types/api'

export function useOperations(filter: OperationFilterParams, page: PageParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.operations(filter, page),
    queryFn: () =>
      apiRequest<PageDto<OperationDto>>('/storage/operations', {
        params: { ...filter, ...page },
      }),
    enabled,
    placeholderData: (previous) => previous,
  })
}

export function useExecuteOperation(storageId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: OperationRequest) =>
      apiRequest<void>(`/storage/${storageId}/operation`, {
        method: 'POST',
        params: { ...request },
      }),
    onSuccess: () => {
      // Операция меняет и остаток, и журнал, и цифры отчёта.
      queryClient.invalidateQueries({ queryKey: queryKeys.storage(storageId) })
      queryClient.invalidateQueries({ queryKey: ['operations'] })
      queryClient.invalidateQueries({ queryKey: ['report'] })
    },
  })
}

/**
 * Удаление операции на бэкенде — это отмена: остаток откатывается,
 * исходная запись помечается отменённой, рядом появляется запись «Отмена».
 * Журнал показывает операции всех складов, поэтому сбрасываем их остатки целиком.
 */
export function useCancelOperation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (operationId: number) =>
      apiRequest<void>(`/storage/operations/${operationId}`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['storage'] })
      queryClient.invalidateQueries({ queryKey: ['operations'] })
      queryClient.invalidateQueries({ queryKey: ['report'] })
    },
  })
}
