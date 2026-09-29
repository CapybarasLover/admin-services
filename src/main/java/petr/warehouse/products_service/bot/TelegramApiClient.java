package petr.warehouse.products_service.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//Тонкая обёртка над Bot API. Отдельной библиотеки не берём:
//нужно ровно четыре метода, а лишняя зависимость тянет свой жизненный цикл.
@Component
public class TelegramApiClient {
    private static final Logger log = LoggerFactory.getLogger(TelegramApiClient.class);

    private final TelegramProperties properties;
    private final RestClient restClient;

    public TelegramApiClient(TelegramProperties properties) {
        this.properties = properties;

        //Read timeout обязан пережить long polling, иначе каждый опрос падает по таймауту.
        Duration readTimeout = properties.getPollTimeout().plusSeconds(15);

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(properties.getApiUrl() + "/bot" + properties.getToken())
                .requestFactory(requestFactory)
                .build();
    }

    //Единственный метод, который бросает наружу: поллер сам решает, как отступать при ошибке.
    public JsonNode getUpdates(long offset) {
        return call("getUpdates", Map.of(
                "offset", offset,
                "timeout", properties.getPollTimeout().toSeconds(),
                "allowed_updates", List.of("message", "callback_query", "my_chat_member")
        ));
    }

    public void sendMessage(String chatId, String text) {
        sendMessage(chatId, text, null);
    }

    //Отправка никогда не роняет вызывающий код: уведомление не должно ломать операцию склада.
    public void sendMessage(String chatId, String text, Object replyMarkup) {
        if (!properties.isConfigured() || chatId == null || chatId.isBlank()) {
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }

        try {
            call("sendMessage", body);
        } catch (RuntimeException e) {
            log.warn("Не удалось отправить сообщение в Telegram (chat {}): {}", chatId, e.toString());
        }
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(String.valueOf(chatId), text, null);
    }

    public void sendMessage(long chatId, String text, Object replyMarkup) {
        sendMessage(String.valueOf(chatId), text, replyMarkup);
    }

    //Без ответа кнопка в клиенте крутит «часики» ещё полминуты.
    public void answerCallback(String callbackId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackId);
        if (text != null) {
            body.put("text", text);
        }
        try {
            call("answerCallbackQuery", body);
        } catch (RuntimeException e) {
            log.debug("answerCallbackQuery не прошёл: {}", e.toString());
        }
    }

    //Если у бота остался вебхук, getUpdates отвечает 409 и поллер зацикливается на ошибке.
    public void deleteWebhook() {
        try {
            call("deleteWebhook", Map.of("drop_pending_updates", false));
        } catch (RuntimeException e) {
            log.debug("deleteWebhook не прошёл: {}", e.toString());
        }
    }

    public void setMyCommands(List<Map<String, String>> commands) {
        try {
            call("setMyCommands", Map.of("commands", commands));
        } catch (RuntimeException e) {
            log.debug("setMyCommands не прошёл: {}", e.toString());
        }
    }

    //Отдаём getMe целиком: кроме имени оттуда читается privacy mode (can_read_all_group_messages).
    public JsonNode getMe() {
        try {
            return call("getMe", Map.of());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonNode call(String method, Map<String, Object> body) {
        JsonNode response = restClient.post()
                .uri("/" + method)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Пустой ответ Telegram на " + method);
        }
        if (!response.path("ok").asBoolean(false)) {
            throw new IllegalStateException("Telegram отклонил " + method + ": " + response.path("description").asString(""));
        }
        return response.path("result");
    }
}
