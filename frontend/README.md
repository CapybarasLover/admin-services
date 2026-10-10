# UI складского учёта

SPA на React + TypeScript поверх REST-API бэкенда. Слева список складов,
справа рабочая область с вкладками «Товары», «Операции» и «Отчёт».

## Разработка

```bash
npm install
npm run dev        # http://localhost:5173, запросы проксируются на :8081
```

Бэкенд при этом должен быть поднят в dev-профиле:

```bash
docker compose up -d db liquibase
cd ../services/products-service
DB_NAME=... DB_USER=... DB_PASS=... ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Пока нет авторизации

Эндпоинты `/auth/*` делает заказчик. Чтобы работать с UI до их появления,
скопируйте `.env.example` в `.env.local` и поставьте:

```
VITE_AUTH_ENABLED=false
```

В этом режиме вход не запрашивается, а текущий пользователь считается админом.
Со значением по умолчанию (`true`) работают `/auth/login`, `/auth/register`
и `/auth/me`, а токен уходит в заголовке `Authorization: Bearer`.

### Выгрузка отчёта в PDF

Кнопка «Скачать PDF» на вкладке «Отчёт» бьёт в `GET /report/pdf`, а тот
проксирует запрос в python-сервис из `services/report-service/`. Без него бэкенд
ответит 503, и UI покажет это текстом, предложив выгрузку в JSON.

## Сборка в jar

`npm run build` кладёт результат в `../services/products-service/src/main/resources/static`,
поэтому порядок такой:

```bash
cd frontend && npm run build
cd ../services/products-service && ./mvnw package
java -jar target/*.jar        # UI на :8080 (prod-профиль)
```

Каталог `services/products-service/src/main/resources/static` в git не хранится — он собирается.
Клиентские маршруты в prod отдаёт `SpaForwardController`; список путей там
явный, чтобы не перехватывать API и Swagger.

## Что где лежит

| Путь | Что внутри |
| --- | --- |
| `src/lib/api.ts` | fetch-обёртка: Bearer-токен, разбор ProblemDetail в `ApiError` |
| `src/lib/auth.tsx` | `AuthProvider`, `ProtectedRoute`, `RoleGate` |
| `src/lib/constants.ts` | цветовой язык статусов и типов операций |
| `src/hooks/` | запросы и мутации на react-query |
| `src/components/ui/` | примитивы в стиле shadcn на Radix |
| `src/pages/` | экраны: вход, регистрация и три вкладки склада |

Мутации позиции собраны в `src/hooks/useStorageMutations.ts` — когда появится
эндпоинт правки товара, добавлять нужно туда и в меню строки `ItemsTab`.
