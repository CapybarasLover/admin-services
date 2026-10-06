package petr.warehouse.products_service.bot;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

//Проверяем ровно то, что нельзя проверить глазами: как тела запросов уезжают в Telegram
//и как разбирается ответ. Настоящий api.telegram.org здесь не участвует.
class TelegramApiClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private TelegramProperties properties;
    private TelegramApiClient client;
    private final Map<String, String> requests = new ConcurrentHashMap<>();
    private final Map<String, String> responses = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String method = exchange.getRequestURI().getPath().substring("/botTEST-TOKEN/".length());
            try (InputStream body = exchange.getRequestBody()) {
                requests.put(method, new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] payload = responses.getOrDefault(method, "{\"ok\":true,\"result\":{}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();

        properties = new TelegramProperties();
        properties.setToken("TEST-TOKEN");
        properties.setChatId("-100500");
        properties.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setPollTimeout(Duration.ofSeconds(1));

        client = new TelegramApiClient(properties);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsMessageWithInlineKeyboard() {
        client.sendMessage("-100500", "<b>Заканчивается</b>", TelegramFormat.inlineKeyboard(List.of(
                List.of(TelegramFormat.inlineButton("Записать", "buy:1:2"))
        )));

        JsonNode sent = MAPPER.readTree(requests.get("sendMessage"));
        assertThat(sent.path("chat_id").asString()).isEqualTo("-100500");
        assertThat(sent.path("text").asString()).isEqualTo("<b>Заканчивается</b>");
        assertThat(sent.path("parse_mode").asString()).isEqualTo("HTML");
        assertThat(sent.path("reply_markup").path("inline_keyboard").get(0).get(0).path("callback_data").asString())
                .isEqualTo("buy:1:2");
    }

    @Test
    void parsesUpdatesAndKeepsOffset() {
        responses.put("getUpdates", """
                {"ok":true,"result":[
                  {"update_id":42,"message":{"chat":{"id":7},"text":"/stock"}},
                  {"update_id":43,"callback_query":{"id":"cb","data":"ok","message":{"chat":{"id":7}}}}
                ]}""");

        JsonNode updates = client.getUpdates(0);

        List<Long> ids = new ArrayList<>();
        updates.forEach(update -> ids.add(update.path("update_id").asLong()));

        assertThat(ids).containsExactly(42L, 43L);
        assertThat(updates.get(0).path("message").path("text").asString()).isEqualTo("/stock");

        JsonNode request = MAPPER.readTree(requests.get("getUpdates"));
        assertThat(request.path("timeout").asLong()).isEqualTo(1);
        assertThat(request.path("allowed_updates").get(0).asString()).isEqualTo("message");
    }

    @Test
    void sendsIntoBoundTopicOnlyForItsGroup() {
        properties.setTopicId("45");

        client.sendMessage("-100500", "в ветку");
        assertThat(MAPPER.readTree(requests.get("sendMessage")).path("message_thread_id").asLong()).isEqualTo(45);

        client.sendMessage("777", "в личку");
        assertThat(MAPPER.readTree(requests.get("sendMessage")).has("message_thread_id")).isFalse();
    }

    @Test
    void failedSendDoesNotThrow() {
        responses.put("sendMessage", "{\"ok\":false,\"description\":\"chat not found\"}");

        client.sendMessage("-100500", "что угодно");

        assertThat(requests).containsKey("sendMessage");
    }
}
