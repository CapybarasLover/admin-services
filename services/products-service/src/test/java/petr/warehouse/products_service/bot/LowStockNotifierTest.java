package petr.warehouse.products_service.bot;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import petr.warehouse.products_service.event.LowStockEvent;
import petr.warehouse.products_service.model.ItemStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LowStockNotifierTest {

    @Test
    void mentionsBuyerAndOffersAdmissionButton() {
        TelegramProperties properties = new TelegramProperties();
        properties.setToken("TEST-TOKEN");
        properties.setChatId("-100500");
        properties.setMention("@dasha");

        TelegramApiClient api = mock(TelegramApiClient.class);
        LowStockNotifier notifier = new LowStockNotifier(properties, api, registry());

        notifier.onLowStock(new LowStockEvent(1L, "Основной", 10L, "Стаканы <300>", 7, ItemStatus.FEW));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> markup = ArgumentCaptor.forClass(Object.class);
        verify(api, timeout(2_000)).sendMessage(eq("-100500"), text.capture(), markup.capture());

        assertThat(text.getValue())
                .contains("Товар заканчивается")
                .contains("@dasha, надо закупить.")
                .contains("7 шт.")
                //Название товара уезжает в HTML — угловые скобки обязаны быть экранированы.
                .contains("Стаканы &lt;300&gt;");
        assertThat(markup.getValue().toString()).contains("buy:1:10");
    }

    //Бота добавили в две группы — уведомление уходит в обе, и явный chat-id не дублируется.
    @Test
    void sendsToEveryChatWhereBotLives() {
        TelegramProperties properties = new TelegramProperties();
        properties.setToken("TEST-TOKEN");
        properties.setChatId("-100500");

        TelegramApiClient api = mock(TelegramApiClient.class);
        LowStockNotifier notifier = new LowStockNotifier(properties, api, registry(-100500L, -100600L));

        notifier.onLowStock(new LowStockEvent(1L, "Основной", 10L, "Стаканы", 7, ItemStatus.FEW));

        verify(api, timeout(2_000)).sendMessage(eq("-100500"), anyString(), any());
        verify(api, timeout(2_000)).sendMessage(eq("-100600"), anyString(), any());
        verify(api, timeout(2_000).times(2)).sendMessage(anyString(), anyString(), any());
    }

    @Test
    void staysSilentWhenBotIsNotInAnyChat() {
        TelegramProperties properties = new TelegramProperties();
        properties.setToken("TEST-TOKEN");

        TelegramApiClient api = mock(TelegramApiClient.class);
        LowStockNotifier notifier = new LowStockNotifier(properties, api, registry());

        notifier.onLowStock(new LowStockEvent(1L, "Основной", 10L, "Стаканы", 0, ItemStatus.OUT));

        verify(api, never()).sendMessage(anyString(), anyString(), any());
    }

    private static TelegramChatRegistry registry(Long... chatIds) {
        TelegramChatRegistry registry = mock(TelegramChatRegistry.class);
        when(registry.subscribedChats()).thenReturn(java.util.List.of(chatIds));
        return registry;
    }
}
