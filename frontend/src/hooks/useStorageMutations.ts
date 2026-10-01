import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '@/lib/api'
import { queryKeys } from '@/hooks/keys'

/** Мутации позиции собраны здесь, чтобы экраны не знали про адреса эндпоинтов. */

interface AddProductInput {
  productName: string
  productCost: number
  buyCost: number
  countThreshold: number
}

export function useAddProduct(storageId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: AddProductInput) =>
      apiRequest<void>(`/storage/${storageId}/products`, {
        method: 'POST',
        params: { ...input },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.storage(storageId) }),
  })
}

/** Все поля необязательные: бэкенд меняет только те, что пришли. */
export interface UpdateProductInput {
  productId: number
  productCost?: number
  buyCost?: number
  countThreshold?: number
}

export function useUpdateProduct(storageId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ productId, ...changes }: UpdateProductInput) =>
      apiRequest<void>(`/storage/${storageId}/products/${productId}`, {
        method: 'PATCH',
        params: { ...changes },
      }),
    // Порог меняет статус позиции, а цена — стоимость остатка в отчёте.
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.storage(storageId) })
      queryClient.invalidateQueries({ queryKey: ['report'] })
    },
  })
}

export function useDeleteProduct(storageId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (productId: number) =>
      apiRequest<void>(`/storage/${storageId}/products/${productId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.storage(storageId) }),
  })
}
