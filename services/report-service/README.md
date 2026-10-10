# Сервис формирования PDF

Принимает `SummaryReportDto` бэкенда и возвращает готовый PDF.
Наружу не публикуется: ходит только Java-прокси `GET /report/pdf`,
поэтому origin один и авторизация одна.

```
браузер → GET /report/pdf → Spring: ReportService собирает SummaryReportDto
                                 → POST /render в этот сервис
                                 ← PDF ← отдаётся клиенту как attachment
```

## Запуск в docker

Отдельных действий не нужно — сервис описан в `docker-compose.yaml`
и поднимается вместе с остальными:

```bash
docker compose up -d
```

Бэкенд ждёт его готовности через healthcheck и ходит по
`REPORT_PDF_URL=http://report-service:8000`.

## Запуск локально

```bash
cd report-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8000
```

Бэкенд ищет сервис по `REPORT_PDF_URL`, по умолчанию `http://localhost:8000`.
Если порт занят, поднимите на другом и передайте адрес явно:

```bash
uvicorn app.main:app --port 8010
REPORT_PDF_URL=http://127.0.0.1:8010 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Эндпоинты

| Метод | Путь | Что делает |
| --- | --- | --- |
| `GET` | `/health` | Проверка живости, заодно показывает выбранный шрифт |
| `POST` | `/render?theme=light\|dark` | Тело — `SummaryReportDto`, ответ — `application/pdf` |

Тему прокидывает интерфейс: отчёт печатается в той же палитре, в которой
пользователь смотрит экран. Неизвестное значение трактуется как светлая.

Если сервис лежит, бэкенд отвечает 503 с человекочитаемым ProblemDetail,
а не 500 — интерфейс показывает его текстом и предлагает выгрузку в JSON.

## Шрифты

Встроенные шрифты PDF кириллицу не поддерживают, поэтому нужен TTF.
Сервис сам находит DejaVu (в контейнере), Arial (macOS) или Liberation;
пути переопределяются через `REPORT_FONT_REGULAR` и `REPORT_FONT_BOLD`.

Знак рубля U+20BD появился только в 2014 году, и в системных шрифтах macOS
его нет. Сервис проверяет наличие глифа и подставляет «руб.» вместо пустого
квадрата — в контейнере с DejaVu печатается обычное ₽.
