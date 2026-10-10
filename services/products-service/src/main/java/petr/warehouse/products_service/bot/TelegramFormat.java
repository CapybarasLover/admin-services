package petr.warehouse.products_service.bot;

import petr.warehouse.products_service.model.ItemStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

//Разметка сообщений и раскладки кнопок в одном месте.
public final class TelegramFormat {
    public static final String CB_STORAGE = "st";
    public static final String CB_PRODUCT = "pr";
    public static final String CB_BUY = "buy";
    public static final String CB_SKIP = "skip";
    public static final String CB_CONFIRM = "ok";
    public static final String CB_CANCEL = "no";
    public static final String CB_STOCK = "stock";
    public static final String CB_STORAGES = "sl";
    public static final String CB_VIEW = "sv";

    public static final String BTN_ADMISSION = "📥 Поступление";
    public static final String BTN_STOCK = "📉 Что заканчивается";
    public static final String BTN_STORAGES = "🏬 Склады";
    public static final String BTN_CANCEL = "✖️ Отмена";

    private TelegramFormat() {
    }

    //parse_mode=HTML: всё, что пришло из базы, обязано быть экранировано.
    public static String esc(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String bold(String raw) {
        return "<b>" + esc(raw) + "</b>";
    }

    public static String money(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " ₽";
    }

    public static String statusLabel(ItemStatus status) {
        return switch (status) {
            case OUT -> "закончился";
            case FEW -> "заканчивается";
            case ENOUGH -> "в наличии";
        };
    }

    public static String statusIcon(ItemStatus status) {
        return switch (status) {
            case OUT -> "🔴";
            case FEW -> "🟡";
            case ENOUGH -> "🟢";
        };
    }

    public static Map<String, Object> inlineKeyboard(List<List<Map<String, String>>> rows) {
        return Map.of("inline_keyboard", rows);
    }

    public static Map<String, String> inlineButton(String text, String callbackData) {
        return Map.of("text", text, "callback_data", callbackData);
    }

    //Отмена висит под каждым вопросом бота: выйти из диалога можно на любом шаге.
    public static Map<String, Object> cancelKeyboard() {
        return inlineKeyboard(List.of(List.of(inlineButton(BTN_CANCEL, CB_CANCEL))));
    }

    //В группе бот с включённым privacy mode обычного текста не видит — только команды,
    //упоминания и ОТВЕТЫ на свои сообщения. force_reply просит клиент оформить ответ
    //как reply, поэтому текстовый шаг диалога доезжает до бота.
    //selective не ставим: сообщение не адресовано конкретному участнику, и с ним ответ не запросится ни у кого.
    public static Map<String, Object> forceReply() {
        return Map.of("force_reply", true);
    }

    //Постоянная клавиатура: Даше не надо помнить команды, всё в две кнопки.
    public static Map<String, Object> mainKeyboard() {
        return Map.of(
                "keyboard", List.of(
                        List.of(Map.of("text", BTN_ADMISSION)),
                        List.of(Map.of("text", BTN_STOCK), Map.of("text", BTN_STORAGES))
                ),
                "resize_keyboard", true,
                "is_persistent", true
        );
    }
}
