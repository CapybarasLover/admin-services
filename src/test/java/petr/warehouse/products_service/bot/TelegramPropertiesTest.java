package petr.warehouse.products_service.bot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

//Пустые TELEGRAM_* в .env не должны ронять старт приложения.
class TelegramPropertiesTest {

    @Test
    void bindsEmptyEnvironmentWithoutFailing() {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "telegram.enabled", "true",
                "telegram.token", "",
                "telegram.chat-id", "",
                "telegram.mention", "",
                "telegram.poll-timeout", "25s",
                "telegram.api-url", ""
        ));

        TelegramProperties properties = new Binder(source)
                .bind("telegram", TelegramProperties.class)
                .get();

        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.hasAlertChat()).isFalse();
        assertThat(properties.getPollTimeout().toSeconds()).isEqualTo(25);
        //Пустой TELEGRAM_API_URL в .env не должен оставить клиент без схемы.
        assertThat(properties.getApiUrl()).isEqualTo("https://api.telegram.org");
    }

    @Test
    void usesProxyUrlWithoutTrailingSlash() {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "telegram.token", "123:abc",
                "telegram.api-url", "https://tg.example.workers.dev/"
        ));

        TelegramProperties properties = new Binder(source)
                .bind("telegram", TelegramProperties.class)
                .get();

        assertThat(properties.getApiUrl()).isEqualTo("https://tg.example.workers.dev");
    }

    //Заданный chat-id включает уведомления в этот чат вдобавок к тем, куда бота добавили.
    @Test
    void readsTokenAndAlertChat() {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "telegram.token", "123:abc",
                "telegram.chat-id", "-100500"
        ));

        TelegramProperties properties = new Binder(source)
                .bind("telegram", TelegramProperties.class)
                .get();

        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.hasAlertChat()).isTrue();
    }
}
