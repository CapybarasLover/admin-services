package petr.warehouse.products_service.bot;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

//Настройки телеграм-бота. Без токена бот просто не поднимается,
//а приложение при этом работает как раньше.
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {
    private static final String DEFAULT_API_URL = "https://api.telegram.org";

    private boolean enabled = true;

    //Токен от @BotFather
    private String token = "";

    //Чат, куда падают уведомления «товар заканчивается».
    //Для группы это отрицательное число, для личной переписки — id пользователя.
    private String chatId = "";

    //Ветка (topic) супергруппы-форума из chat-id, в которой живёт бот: пишет только туда
    //и не реагирует на остальные ветки этой группы. Другие чаты не затрагивает. 1 — ветка General.
    //Узнать id: /chatid прямо в ветке, или последнее число в ссылке на сообщение t.me/c/.../<ветка>/...
    private String topicId = "";

    //Кого дёргать в уведомлении. Пинг сработает только если у человека есть @username.
    private String mention = "";

    //Сколько держать long polling открытым.
    private Duration pollTimeout = Duration.ofSeconds(25);

    private String apiUrl = DEFAULT_API_URL;

    //Пустая переменная окружения — это заданная переменная, дефолт из yaml её уже не перебьёт.
    //Без этой подстраховки TELEGRAM_API_URL= в .env роняет бота в «URI with undefined scheme».
    public String getApiUrl() {
        if (apiUrl == null || apiUrl.isBlank()) {
            return DEFAULT_API_URL;
        }
        String trimmed = apiUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public boolean isConfigured() {
        return enabled && !token.isBlank();
    }

    //Ветка для этого чата или null, если бот в нём не ограничен веткой.
    public Long topicFor(String chat) {
        if (topicId == null || topicId.isBlank() || chatId.isBlank() || !chatId.trim().equals(chat)) {
            return null;
        }
        try {
            return Long.parseLong(topicId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isOutsideTopic(long chat, long threadId) {
        Long topic = topicFor(String.valueOf(chat));
        return topic != null && topic != threadId;
    }

    public boolean hasAlertChat() {
        return isConfigured() && !chatId.isBlank();
    }
}
