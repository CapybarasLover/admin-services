package petr.warehouse.products_service.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

//Long polling: вебхук требует публичного https, а бот должен работать и с ноутбука.
@Component
public class TelegramUpdatePoller implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(TelegramUpdatePoller.class);
    private static final long MIN_BACKOFF_MS = 5_000;
    private static final long MAX_BACKOFF_MS = 60_000;

    private final TelegramProperties properties;
    private final TelegramApiClient api;
    private final TelegramBotService botService;

    private volatile boolean running;
    private volatile Thread worker;

    public TelegramUpdatePoller(TelegramProperties properties, TelegramApiClient api, TelegramBotService botService) {
        this.properties = properties;
        this.api = api;
        this.botService = botService;
    }

    @Override
    public void start() {
        if (!properties.isConfigured()) {
            log.info("Телеграм-бот выключен: не задан telegram.token");
            return;
        }
        running = true;
        Thread thread = new Thread(this::pollLoop, "telegram-poller");
        thread.setDaemon(true);
        thread.start();
        worker = thread;
    }

    @Override
    public void stop() {
        running = false;
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void pollLoop() {
        //Оставшийся вебхук заставляет getUpdates отвечать 409 на каждый запрос.
        api.deleteWebhook();
        api.setMyCommands(List.of(
                Map.of("command", "admission", "description", "Записать поступление"),
                Map.of("command", "stock", "description", "Что заканчивается"),
                Map.of("command", "storages", "description", "Склады и остатки"),
                Map.of("command", "cancel", "description", "Отменить текущий ввод"),
                Map.of("command", "alerts", "description", "Уведомления в этом чате"),
                Map.of("command", "help", "description", "Помощь")
        ));

        JsonNode me = api.getMe();
        String username = me == null ? "" : me.path("username").asString("");
        log.info("Телеграм-бот запущен{}", username.isBlank() ? "" : ": @" + username);

        //Без этой строки включённый privacy mode выглядит как «бот не отвечает»: в группе Telegram
        //не отдаёт боту обычный текст, поэтому диалог молча встаёт на первом же вопросе.
        if (me != null && !me.path("can_read_all_group_messages").asBoolean(false)) {
            log.warn("У бота включён privacy mode: в группах обычный текст до него не доходит. "
                    + "Текстовые шаги диалога спрашиваются через force_reply — отвечайте на сообщение бота. "
                    + "Чтобы работал и обычный ввод: @BotFather → /setprivacy → Disable.");
        }

        long offset = 0;
        long backoff = MIN_BACKOFF_MS;

        while (running) {
            try {
                JsonNode updates = api.getUpdates(offset);
                backoff = MIN_BACKOFF_MS;
                for (JsonNode update : updates) {
                    //Сдвигаем offset до обработки: упавший апдейт не должен крутиться вечно.
                    offset = Math.max(offset, update.path("update_id").asLong() + 1);
                    try {
                        botService.handleUpdate(update);
                    } catch (RuntimeException e) {
                        log.error("Ошибка обработки апдейта {}", update.path("update_id").asLong(), e);
                    }
                }
            } catch (RuntimeException e) {
                if (!running) {
                    break;
                }
                //Если Telegram недоступен совсем (заблокирован исход, лежит сеть),
                //лог не должен превращаться в поток одинаковых строк каждые пять секунд.
                log.warn("Опрос Telegram не удался, повтор через {} с: {}", backoff / 1000, e.toString());
                sleepQuietly(backoff);
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }
        log.info("Телеграм-бот остановлен");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
