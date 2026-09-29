package petr.warehouse.products_service.bot;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import petr.warehouse.products_service.event.LowStockEvent;
import petr.warehouse.products_service.model.ItemStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static petr.warehouse.products_service.bot.TelegramFormat.*;

//Триггер: остаток упал до «заканчивается» или «закончился» — пишем во все чаты бота с тегом закупщика.
@Component
public class LowStockNotifier {
    private static final Logger log = LoggerFactory.getLogger(LowStockNotifier.class);

    private final TelegramProperties properties;
    private final TelegramApiClient api;
    private final TelegramChatRegistry chats;

    //Telegram отвечает не мгновенно, а событие прилетает в потоке http-запроса склада.
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "telegram-alerts");
        thread.setDaemon(true);
        return thread;
    });

    public LowStockNotifier(TelegramProperties properties, TelegramApiClient api, TelegramChatRegistry chats) {
        this.properties = properties;
        this.api = api;
        this.chats = chats;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        if (!properties.isConfigured()) {
            return;
        }
        sender.execute(() -> send(event));
    }

    private void send(LowStockEvent event) {
        Set<String> targets = targets();
        if (targets.isEmpty()) {
            log.debug("Уведомление о «{}» некуда слать: бота ещё не добавили ни в один чат", event.productName());
            return;
        }

        String header = event.status() == ItemStatus.OUT
                ? statusIcon(ItemStatus.OUT) + " <b>Товар закончился</b>"
                : statusIcon(ItemStatus.FEW) + " <b>Товар заканчивается</b>";

        StringBuilder text = new StringBuilder()
                .append(header).append("\n\n")
                .append("Склад: ").append(bold(event.storageName())).append('\n')
                .append("Товар: ").append(bold(event.productName())).append('\n')
                .append("Остаток: ").append(bold(event.count() + " шт.")).append("\n\n");

        String mention = properties.getMention();
        if (mention != null && !mention.isBlank()) {
            text.append(esc(mention)).append(", ");
        }
        text.append("надо закупить.");

        Map<String, Object> keyboard = inlineKeyboard(List.of(
                List.of(inlineButton(
                        "📥 Записать поступление",
                        CB_BUY + ":" + event.storageId() + ":" + event.productId()))
        ));

        for (String chatId : targets) {
            api.sendMessage(chatId, text.toString(), keyboard);
        }
    }

    //Явно заданный telegram.chat-id плюс все чаты, куда бота добавили. Без дублей.
    private Set<String> targets() {
        Set<String> targets = new LinkedHashSet<>();

        if (!properties.getChatId().isBlank()) {
            targets.add(properties.getChatId().trim());
        }
        for (Long chatId : chats.subscribedChats()) {
            targets.add(String.valueOf(chatId));
        }
        return targets;
    }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
    }
}
