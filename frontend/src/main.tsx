import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'sonner'
import { App } from '@/App'
import { ApiError } from '@/lib/api'
import { AuthProvider } from '@/lib/auth'
import '@/index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Повторять запрос после 4xx бессмысленно — ответ не изменится.
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status < 500) && failureCount < 2,
      refetchOnWindowFocus: false,
      staleTime: 15_000,
    },
  },
  queryCache: new QueryCache({
    onError: (error) => {
      if (error instanceof ApiError && error.status === 403) {
        // Экраны сами по себе доступны всем ролям, так что это сигнал о
        // рассинхроне матрицы прав, а не рядовая ситуация.
        console.warn('Запрос отклонён по правам:', error.message)
      }
    },
  }),
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter basename={import.meta.env.BASE_URL}>
        <AuthProvider>
          <App />
          <Toaster richColors position="top-right" closeButton />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
