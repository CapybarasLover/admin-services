// Реверс-прокси к Bot API для серверов, с которых api.telegram.org недоступен.
//
// Как поднять (5 минут, бесплатно):
//   1. dash.cloudflare.com → Workers & Pages → Create → Worker
//   2. Вставить этот файл целиком, Deploy
//   3. Скопировать адрес вида https://<имя>.<аккаунт>.workers.dev
//   4. На сервере в .env: TELEGRAM_API_URL=https://<имя>.<аккаунт>.workers.dev
//      и docker compose up -d backend
//
// Токен уходит в URL так же, как и при прямом обращении к Telegram,
// поэтому воркер должен быть в вашем аккаунте, а не в чужом.
export default {
  async fetch(request) {
    const url = new URL(request.url);

    // Наружу торчит только /bot<token>/<method> — всё остальное закрыто.
    if (!url.pathname.startsWith('/bot')) {
      return new Response('Not found', { status: 404 });
    }

    // Заголовки собираем сами: Host и cf-* от воркера Telegram не нужны.
    const headers = new Headers();
    const contentType = request.headers.get('content-type');
    if (contentType) {
      headers.set('content-type', contentType);
    }

    const hasBody = request.method !== 'GET' && request.method !== 'HEAD';

    return fetch('https://api.telegram.org' + url.pathname + url.search, {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
    });
  },
};
