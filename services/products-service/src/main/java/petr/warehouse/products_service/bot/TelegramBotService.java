package petr.warehouse.products_service.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import petr.warehouse.products_service.dto.OperationRequestDto;
import petr.warehouse.products_service.dto.StorageDto;
import petr.warehouse.products_service.dto.StorageInfoDto;
import petr.warehouse.products_service.dto.StorageItemDto;
import petr.warehouse.products_service.exception.data.InsufficientStockException;
import petr.warehouse.products_service.exception.data.ProductNotFoundException;
import petr.warehouse.products_service.exception.data.StorageNotFoundException;
import petr.warehouse.products_service.exception.request.ZeroOrNullAdmissionCost;
import petr.warehouse.products_service.model.ItemStatus;
import petr.warehouse.products_service.model.OperationType;
import petr.warehouse.products_service.service.OperationService;
import petr.warehouse.products_service.service.StorageManagerService;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static petr.warehouse.products_service.bot.TelegramFormat.*;

//Разбор апдейтов и диалог «записать поступление».
//Состояние диалога живёт в памяти: незаконченный черновик не жалко потерять при рестарте.
@Service
public class TelegramBotService {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private static final Duration DRAFT_TTL = Duration.ofMinutes(30);
    private static final int MAX_PRODUCT_BUTTONS = 24;
    private static final int MAX_STOCK_LINES = 30;

    private final TelegramApiClient api;
    private final StorageManagerService storageService;
    private final OperationService operationService;
    private final TelegramChatRegistry chats;

    private final Map<Long, AdmissionDraft> drafts = new ConcurrentHashMap<>();

    public TelegramBotService(
            TelegramApiClient api,
            StorageManagerService storageService,
            OperationService operationService,
            TelegramChatRegistry chats
    ) {
        this.api = api;
        this.storageService = storageService;
        this.operationService = operationService;
        this.chats = chats;
    }

    public void handleUpdate(JsonNode update) {
        if (update.has("my_chat_member")) {
            handleMembership(update.path("my_chat_member"));
        } else if (update.has("callback_query")) {
            handleCallback(update.path("callback_query"));
        } else if (update.has("message")) {
            handleMessage(update.path("message"));
        }
    }

    // ----------------------------------------------------------------- сообщения

    private void handleMessage(JsonNode message) {
        long chatId = message.path("chat").path("id").asLong();
        String text = message.path("text").asString("").trim();

        if (text.isEmpty()) {
            return;
        }

        rememberChat(message.path("chat"));

        String command = normalizeCommand(text);

        switch (command) {
            case "/start" -> {
                drafts.remove(chatId);
                sendWelcome(chatId);
            }
            case "/help" -> sendHelp(chatId);
            case "/chatid" -> api.sendMessage(chatId, "id этого чата: <code>" + chatId + "</code>");
            case "/alerts" -> toggleAlerts(chatId, message.path("chat"));
            case "/cancel" -> cancelDialog(chatId);
            case "/stock" -> sendLowStock(chatId);
            case "/storages" -> sendStorages(chatId);
            case "/admission" -> startAdmission(chatId, null, null);
            default -> handleFreeText(chatId, text);
        }
    }

    //Команду можно прислать как /stock@my_bot — хвост с именем бота нам не нужен.
    private String normalizeCommand(String text) {
        if (!text.startsWith("/")) {
            return "";
        }
        String head = text.split("\\s+")[0].toLowerCase();
        int at = head.indexOf('@');
        return at > 0 ? head.substring(0, at) : head;
    }

    private void handleFreeText(long chatId, String text) {
        if (text.startsWith("/")) {
            api.sendMessage(chatId, "Не знаю такую команду. /help — что я умею.", mainKeyboard());
            return;
        }

        //Проверяем до диалога: иначе на шаге «сколько штук» слово «отмена» уйдёт в парсер числа.
        if (isCancelWord(text)) {
            cancelDialog(chatId);
            return;
        }

        //Кнопки нижней клавиатуры работают всегда, в том числе посреди диалога.
        if (text.equals(BTN_ADMISSION)) {
            startAdmission(chatId, null, null);
            return;
        }
        if (text.equals(BTN_STOCK)) {
            sendLowStock(chatId);
            return;
        }
        if (text.equals(BTN_STORAGES)) {
            sendStorages(chatId);
            return;
        }

        //Пока идёт диалог, любой текст — это ответ на вопрос бота, а не новая команда:
        //иначе комментарий «поступление от Ивана» начинал бы всё заново.
        AdmissionDraft draft = activeDraft(chatId);
        if (draft != null) {
            continueAdmission(chatId, draft, text);
            return;
        }

        String lower = text.toLowerCase();
        if (lower.contains("заканчива") || lower.contains("остатк")) {
            sendLowStock(chatId);
            return;
        }
        if (lower.contains("поступлен") || lower.contains("закупк") || lower.contains("закупит")) {
            startAdmission(chatId, null, null);
            return;
        }

        api.sendMessage(chatId, "Не понял. Нажмите кнопку ниже или /help.", mainKeyboard());
    }

    // ----------------------------------------------------------------- кнопки

    private void handleCallback(JsonNode callback) {
        String callbackId = callback.path("id").asString("");
        long chatId = callback.path("message").path("chat").path("id").asLong();
        String data = callback.path("data").asString("");

        api.answerCallback(callbackId, null);

        rememberChat(callback.path("message").path("chat"));

        String[] parts = data.split(":");
        switch (parts[0]) {
            case CB_STOCK -> sendLowStock(chatId);
            case CB_STORAGES -> sendStorages(chatId);
            case CB_VIEW -> {
                if (parts.length == 2) {
                    sendStorageItems(chatId, parseLong(parts[1]));
                }
            }
            case CB_CANCEL -> cancelDialog(chatId);
            //Кнопка из уведомления приходит со складом и товаром, из списка остатков — пустая.
            case CB_BUY -> {
                if (parts.length == 3) {
                    startAdmission(chatId, parseLong(parts[1]), parseLong(parts[2]));
                } else if (parts.length == 2) {
                    startAdmission(chatId, parseLong(parts[1]), null);
                } else {
                    startAdmission(chatId, null, null);
                }
            }
            case CB_STORAGE -> {
                AdmissionDraft draft = activeDraft(chatId);
                if (draft == null || parts.length < 2) {
                    api.sendMessage(chatId, "Черновик устарел, начните заново.", mainKeyboard());
                    return;
                }
                applyStorage(chatId, draft, parseLong(parts[1]));
            }
            case CB_PRODUCT -> {
                AdmissionDraft draft = activeDraft(chatId);
                if (draft == null || parts.length < 3) {
                    api.sendMessage(chatId, "Черновик устарел, начните заново.", mainKeyboard());
                    return;
                }
                applyProductById(chatId, draft, parseLong(parts[2]));
            }
            case CB_SKIP -> {
                AdmissionDraft draft = activeDraft(chatId);
                if (draft != null && draft.step == Step.COMMENT) {
                    draft.comment = null;
                    askConfirm(chatId, draft);
                } else {
                    api.sendMessage(chatId, "Черновик устарел, начните заново.", mainKeyboard());
                }
            }
            case CB_CONFIRM -> {
                AdmissionDraft draft = activeDraft(chatId);
                if (draft == null || draft.step != Step.CONFIRM) {
                    api.sendMessage(chatId, "Черновик устарел, начните заново.", mainKeyboard());
                    return;
                }
                executeAdmission(chatId, draft);
            }
            default -> log.debug("Неизвестный callback: {}", data);
        }
    }

    // ----------------------------------------------------------------- диалог поступления

    private void startAdmission(long chatId, Long storageId, Long productId) {
        List<StorageInfoDto> storages = storageService.getAllStorages();
        if (storages.isEmpty()) {
            api.sendMessage(chatId, "Складов пока нет — создайте склад в интерфейсе.", mainKeyboard());
            return;
        }

        AdmissionDraft draft = new AdmissionDraft();
        draft.startedAt = Instant.now();
        drafts.put(chatId, draft);

        Long targetStorage = storageId != null ? storageId : (storages.size() == 1 ? storages.get(0).getId() : null);

        if (targetStorage == null) {
            draft.step = Step.STORAGE;
            List<List<Map<String, String>>> rows = new ArrayList<>();
            for (StorageInfoDto storage : storages) {
                rows.add(List.of(inlineButton(storage.getName(), CB_STORAGE + ":" + storage.getId())));
            }
            rows.add(List.of(inlineButton(BTN_CANCEL, CB_CANCEL)));
            api.sendMessage(chatId, "📥 <b>Новое поступление</b>\n\nНа какой склад?", inlineKeyboard(rows));
            return;
        }

        if (productId != null) {
            draft.storageId = targetStorage;
            StorageDto storage = loadStorage(chatId, targetStorage);
            if (storage == null) {
                return;
            }
            draft.storageName = storage.getName();
            applyProductById(chatId, draft, productId);
            return;
        }

        applyStorage(chatId, draft, targetStorage);
    }

    private void applyStorage(long chatId, AdmissionDraft draft, Long storageId) {
        StorageDto storage = loadStorage(chatId, storageId);
        if (storage == null) {
            return;
        }

        draft.storageId = storageId;
        draft.storageName = storage.getName();
        draft.step = Step.PRODUCT;

        List<StorageItemDto> items = sortedItems(storage);
        if (items.isEmpty()) {
            drafts.remove(chatId);
            api.sendMessage(chatId, "На складе «" + esc(storage.getName()) + "» нет товаров.", mainKeyboard());
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (StorageItemDto item : items.subList(0, Math.min(items.size(), MAX_PRODUCT_BUTTONS))) {
            rows.add(List.of(inlineButton(
                    statusIcon(item.getStatus()) + " " + item.getName() + " · " + item.getCount() + " шт.",
                    CB_PRODUCT + ":" + storageId + ":" + item.getId())));
        }
        rows.add(List.of(inlineButton(BTN_CANCEL, CB_CANCEL)));

        String tail = items.size() > MAX_PRODUCT_BUTTONS
                ? "\n\nПоказаны первые " + MAX_PRODUCT_BUTTONS + " — остальные можно вписать названием."
                : "";

        String nameHint = isGroupChat(chatId)
                ? " Выберите кнопкой — или пришлите название ответом на это сообщение."
                : " Выберите кнопкой или напишите название.";

        api.sendMessage(chatId,
                "Склад: " + bold(storage.getName()) + "\n\nКакой товар пришёл?" + nameHint + tail,
                inlineKeyboard(rows));
    }

    private void applyProductById(long chatId, AdmissionDraft draft, Long productId) {
        StorageDto storage = loadStorage(chatId, draft.storageId);
        if (storage == null) {
            return;
        }
        Optional<StorageItemDto> item = storage.getStorageItemListDto().stream()
                .filter(candidate -> candidate.getId().equals(productId))
                .findFirst();

        if (item.isEmpty()) {
            api.sendMessage(chatId, "Такого товара на складе уже нет. Напишите название вручную.", cancelKeyboard());
            return;
        }
        applyProduct(chatId, draft, item.get().getId(), item.get().getName(), item.get().getCount());
    }

    private void applyProductByName(long chatId, AdmissionDraft draft, String name) {
        StorageDto storage = loadStorage(chatId, draft.storageId);
        if (storage == null) {
            return;
        }
        Optional<StorageItemDto> item = storage.getStorageItemListDto().stream()
                .filter(candidate -> candidate.getName().equalsIgnoreCase(name))
                .findFirst();

        if (item.isEmpty()) {
            api.sendMessage(chatId, "На складе «" + esc(draft.storageName) + "» нет товара «" + esc(name)
                    + "». Проверьте название или выберите кнопкой." + textStepHint(chatId), textStepKeyboard(chatId));
            return;
        }
        applyProduct(chatId, draft, item.get().getId(), item.get().getName(), item.get().getCount());
    }

    private void applyProduct(long chatId, AdmissionDraft draft, Long productId, String productName, int currentCount) {
        draft.productId = productId;
        draft.productName = productName;
        draft.step = Step.COUNT;
        api.sendMessage(chatId,
                "Товар: " + bold(productName) + " (сейчас " + currentCount + " шт.)\n\nСколько штук пришло?"
                        + textStepHint(chatId),
                textStepKeyboard(chatId));
    }

    private void continueAdmission(long chatId, AdmissionDraft draft, String text) {
        switch (draft.step) {
            case STORAGE -> api.sendMessage(chatId, "Сначала выберите склад кнопкой выше.", cancelKeyboard());
            case PRODUCT -> applyProductByName(chatId, draft, text);
            case COUNT -> {
                Integer count = parseCount(text);
                if (count == null) {
                    api.sendMessage(chatId, "Нужно целое число больше нуля. Например: <code>12</code>"
                            + textStepHint(chatId), textStepKeyboard(chatId));
                    return;
                }
                draft.count = count;
                draft.step = Step.COST;
                api.sendMessage(chatId,
                        "Количество: " + bold(count + " шт.") + "\n\nСколько заплатили за <b>всю</b> партию, ₽?"
                                + textStepHint(chatId),
                        textStepKeyboard(chatId));
            }
            case COST -> {
                BigDecimal cost = parseMoney(text);
                if (cost == null) {
                    api.sendMessage(chatId, "Нужна сумма больше нуля. Например: <code>4500</code> или <code>4500.50</code>"
                            + textStepHint(chatId), textStepKeyboard(chatId));
                    return;
                }
                draft.cost = cost;
                draft.step = Step.COMMENT;
                api.sendMessage(chatId,
                        "Сумма поступления: " + bold(money(cost)) + "\n\nКомментарий? Можно пропустить."
                                + (isGroupChat(chatId) ? "\n\n<i>Комментарий присылайте ответом на это сообщение.</i>" : ""),
                        inlineKeyboard(List.of(
                                List.of(inlineButton("Пропустить", CB_SKIP)),
                                List.of(inlineButton(BTN_CANCEL, CB_CANCEL))
                        )));
            }
            case COMMENT -> {
                if (text.length() > 255) {
                    api.sendMessage(chatId, "Комментарий длиннее 255 символов — сократите."
                            + textStepHint(chatId), textStepKeyboard(chatId));
                    return;
                }
                draft.comment = text;
                askConfirm(chatId, draft);
            }
            case CONFIRM -> api.sendMessage(chatId, "Подтвердите кнопкой или напишите «отмена».",
                    inlineKeyboard(List.of(
                            List.of(inlineButton("✅ Записать", CB_CONFIRM)),
                            List.of(inlineButton(BTN_CANCEL, CB_CANCEL))
                    )));
        }
    }

    private void askConfirm(long chatId, AdmissionDraft draft) {
        draft.step = Step.CONFIRM;

        StringBuilder text = new StringBuilder("📥 <b>Проверьте поступление</b>\n\n")
                .append("Склад: ").append(bold(draft.storageName)).append('\n')
                .append("Товар: ").append(bold(draft.productName)).append('\n')
                .append("Количество: ").append(bold(draft.count + " шт.")).append('\n')
                .append("Сумма: ").append(bold(money(draft.cost)));
        if (draft.comment != null) {
            text.append('\n').append("Комментарий: ").append(esc(draft.comment));
        }

        api.sendMessage(chatId, text.toString(), inlineKeyboard(List.of(
                List.of(inlineButton("✅ Записать", CB_CONFIRM)),
                List.of(inlineButton(BTN_CANCEL, CB_CANCEL))
        )));
    }

    private void executeAdmission(long chatId, AdmissionDraft draft) {
        OperationRequestDto request = new OperationRequestDto();
        request.setOperationType(OperationType.ADMISSION);
        request.setProductName(draft.productName);
        request.setCount(draft.count);
        request.setOperationCost(draft.cost);
        request.setComment(draft.comment);

        try {
            operationService.executeOperation(draft.storageId, request);
        } catch (ProductNotFoundException e) {
            api.sendMessage(chatId, "Товар не найден на складе — поступление не записано.", mainKeyboard());
            return;
        } catch (ZeroOrNullAdmissionCost e) {
            api.sendMessage(chatId, "Не указана сумма поступления — попробуйте ещё раз.", mainKeyboard());
            return;
        } catch (InsufficientStockException e) {
            api.sendMessage(chatId, "Складу не хватает товара для этой операции.", mainKeyboard());
            return;
        } catch (RuntimeException e) {
            log.error("Поступление из бота не записалось", e);
            api.sendMessage(chatId, "Не удалось записать поступление. Попробуйте ещё раз или запишите через интерфейс.", mainKeyboard());
            return;
        } finally {
            drafts.remove(chatId);
        }

        int remainder = currentCount(draft.storageId, draft.productId).orElse(-1);
        String tail = remainder < 0 ? "" : "\nОстаток: " + bold(remainder + " шт.");

        api.sendMessage(chatId,
                "✅ <b>Поступление записано</b>\n\n"
                        + "Склад: " + bold(draft.storageName) + "\n"
                        + "Товар: " + bold(draft.productName) + "\n"
                        + "Пришло: " + bold(draft.count + " шт.") + " на " + bold(money(draft.cost))
                        + tail,
                mainKeyboard());
    }

    // ----------------------------------------------------------------- склады и остатки

    //Список складов из системы: по одному на кнопку.
    private void sendStorages(long chatId) {
        List<StorageInfoDto> storages = storageService.getAllStorages();
        if (storages.isEmpty()) {
            api.sendMessage(chatId, "Складов пока нет — создайте склад в интерфейсе.", mainKeyboard());
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (StorageInfoDto storage : storages) {
            rows.add(List.of(inlineButton("🏬 " + storage.getName(), CB_VIEW + ":" + storage.getId())));
        }

        api.sendMessage(chatId,
                "🏬 <b>Склады</b>\n\nВыберите склад, чтобы посмотреть остатки.",
                inlineKeyboard(rows));
    }

    //Остатки одного склада целиком, проблемные позиции сверху.
    private void sendStorageItems(long chatId, Long storageId) {
        StorageDto storage = loadStorage(chatId, storageId);
        if (storage == null) {
            return;
        }

        List<StorageItemDto> items = sortedItems(storage);
        StringBuilder text = new StringBuilder("🏬 <b>").append(esc(storage.getName())).append("</b>\n\n");

        if (items.isEmpty()) {
            text.append("Товаров на складе нет.");
        } else {
            for (StorageItemDto item : items.subList(0, Math.min(items.size(), MAX_STOCK_LINES))) {
                text.append(statusIcon(item.getStatus())).append(' ')
                        .append(esc(item.getName())).append(" — ")
                        .append(item.getCount()).append(" шт.\n");
            }
            if (items.size() > MAX_STOCK_LINES) {
                text.append("\n…и ещё ").append(items.size() - MAX_STOCK_LINES).append(" — смотрите в интерфейсе.");
            }
        }

        api.sendMessage(chatId, text.toString(), inlineKeyboard(List.of(
                List.of(inlineButton(BTN_ADMISSION, CB_BUY + ":" + storageId)),
                List.of(inlineButton("⬅️ К списку складов", CB_STORAGES))
        )));
    }

    private void sendLowStock(long chatId) {
        List<StorageInfoDto> storages = storageService.getAllStorages();
        StringBuilder text = new StringBuilder("📉 <b>Что заканчивается</b>\n");
        int printed = 0;

        for (StorageInfoDto info : storages) {
            if (printed > MAX_STOCK_LINES) {
                break;
            }
            StorageDto storage;
            try {
                storage = storageService.getStorageById(info.getId());
            } catch (StorageNotFoundException e) {
                continue;
            }

            List<StorageItemDto> problems = sortedItems(storage).stream()
                    .filter(item -> item.getStatus() == ItemStatus.FEW || item.getStatus() == ItemStatus.OUT)
                    .toList();

            if (problems.isEmpty()) {
                continue;
            }

            text.append("\n").append(bold(storage.getName())).append('\n');
            for (StorageItemDto item : problems) {
                if (printed++ >= MAX_STOCK_LINES) {
                    break;
                }
                text.append(statusIcon(item.getStatus())).append(' ')
                        .append(esc(item.getName())).append(" — ")
                        .append(item.getCount()).append(" шт.\n");
            }
        }

        if (printed == 0) {
            api.sendMessage(chatId, "✅ Всё в наличии, закупать нечего.", mainKeyboard());
            return;
        }
        if (printed > MAX_STOCK_LINES) {
            text.append("\n…и ещё позиции — смотрите в интерфейсе.");
        }

        api.sendMessage(chatId, text.toString(), inlineKeyboard(List.of(
                List.of(inlineButton(BTN_ADMISSION, CB_BUY))
        )));
    }

    // ----------------------------------------------------------------- вспомогательное

    //Бота добавили в чат или выкинули из него — годится и группа, и личка.
    private void handleMembership(JsonNode membership) {
        JsonNode chat = membership.path("chat");
        long chatId = chat.path("id").asLong();
        String status = membership.path("new_chat_member").path("status").asString("");

        if ("left".equals(status) || "kicked".equals(status)) {
            chats.forget(chatId);
            return;
        }
        if (chats.remember(chatId, chatTitle(chat), chat.path("type").asString(""))) {
            api.sendMessage(chatId,
                    "\uD83D\uDC4B Я бот склада.\n\n"
                            + "Буду писать сюда, когда товар заканчивается, и запишу поступление прямо из чата.\n\n"
                            + "Кнопки внизу или /help.",
                    mainKeyboard());
        }
    }

    //Бот работает в любом чате, куда его добавили: и группа, и личка подписываются сами.
    //Выключить уведомления в конкретном чате — /alerts.
    private void rememberChat(JsonNode chat) {
        chats.remember(chat.path("id").asLong(), chatTitle(chat), chat.path("type").asString(""));
    }

    private void toggleAlerts(long chatId, JsonNode chat) {
        if (chats.isSubscribed(chatId)) {
            chats.forget(chatId);
            api.sendMessage(chatId, "Больше не пишу сюда про закупки. Вернуть — /alerts", mainKeyboard());
            return;
        }
        chats.remember(chatId, chatTitle(chat), chat.path("type").asString(""));
        api.sendMessage(chatId, "Готово: буду писать сюда, когда товар заканчивается.", mainKeyboard());
    }

    private static String chatTitle(JsonNode chat) {
        String title = chat.path("title").asString("");
        if (!title.isBlank()) {
            return title;
        }
        String name = (chat.path("first_name").asString("") + " " + chat.path("last_name").asString("")).trim();
        return name.isBlank() ? chat.path("username").asString("") : name;
    }

    private void sendWelcome(long chatId) {
        api.sendMessage(chatId,
                "👋 Это бот склада.\n\n"
                        + "Я пишу сюда, когда товар заканчивается, и записываю поступления на сервер.\n\n"
                        + "Кнопки внизу — всё, что нужно. Список команд: /help",
                mainKeyboard());
    }

    private void sendHelp(long chatId) {
        api.sendMessage(chatId,
                "<b>Команды</b>\n"
                        + "/admission — записать поступление\n"
                        + "/stock — что заканчивается\n"
                        + "/storages — склады и остатки по каждому\n"
                        + "/cancel — отменить текущий ввод (или просто «отмена» на любом шаге)\n"
                        + "/alerts — включить или выключить уведомления в этом чате\n"
                        + "/chatid — id этого чата\n\n"
                        + "Можно просто написать «поступление» или «что заканчивается».",
                mainKeyboard());
    }

    private StorageDto loadStorage(long chatId, Long storageId) {
        if (storageId == null) {
            //Черновик без склада продолжить нельзя, поэтому не оставляем его висеть до TTL.
            drafts.remove(chatId);
            api.sendMessage(chatId, "Склад не выбран. Начните заново: /admission", mainKeyboard());
            return null;
        }
        try {
            return storageService.getStorageById(storageId);
        } catch (StorageNotFoundException e) {
            drafts.remove(chatId);
            api.sendMessage(chatId, "Склад не найден.", mainKeyboard());
            return null;
        }
    }

    //Сначала то, что горит: закончилось, заканчивается, потом остальное.
    private List<StorageItemDto> sortedItems(StorageDto storage) {
        List<StorageItemDto> items = storage.getStorageItemListDto();
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .sorted(Comparator.comparingInt((StorageItemDto item) -> switch (item.getStatus()) {
                    case OUT -> 0;
                    case FEW -> 1;
                    case ENOUGH -> 2;
                }).thenComparing(StorageItemDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Optional<Integer> currentCount(Long storageId, Long productId) {
        try {
            return storageService.getStorageById(storageId).getStorageItemListDto().stream()
                    .filter(item -> item.getId().equals(productId))
                    .map(StorageItemDto::getCount)
                    .findFirst();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    //Шаг, где бот ждёт текст. В личке под вопросом висит кнопка «Отмена», а в группе её место
    //занимает force_reply: с включённым privacy mode иначе ответ до бота просто не дойдёт.
    private static Map<String, Object> textStepKeyboard(long chatId) {
        return isGroupChat(chatId) ? forceReply() : cancelKeyboard();
    }

    //В группе кнопку отмены заменил force_reply, поэтому про отмену пишем текстом:
    //команда /cancel доходит до бота даже с privacy mode.
    private static String textStepHint(long chatId) {
        return isGroupChat(chatId) ? "\n\n<i>Ответьте на это сообщение. Отменить — /cancel</i>" : "";
    }

    //У групп, супергрупп и каналов id отрицательный, у личных чатов — положительный.
    private static boolean isGroupChat(long chatId) {
        return chatId < 0;
    }

    //Единственная точка отмены: /cancel, кнопка под любым вопросом и слово «отмена».
    private void cancelDialog(long chatId) {
        boolean had = drafts.remove(chatId) != null;
        api.sendMessage(chatId, had ? "Отменил. Ничего не записал." : "Нечего отменять.", mainKeyboard());
    }

    //Кнопку может унести вверх по переписке, поэтому отмену принимаем и словом.
    private static boolean isCancelWord(String text) {
        String lower = text.toLowerCase().replace("ё", "е");
        return lower.equals(BTN_CANCEL.toLowerCase())
                || lower.equals("отмена")
                || lower.equals("отменить")
                || lower.equals("отмени")
                || lower.equals("стоп")
                || lower.equals("cancel")
                || lower.equals("stop");
    }

    private AdmissionDraft activeDraft(long chatId) {
        AdmissionDraft draft = drafts.get(chatId);
        if (draft == null) {
            return null;
        }
        if (Duration.between(draft.startedAt, Instant.now()).compareTo(DRAFT_TTL) > 0) {
            drafts.remove(chatId);
            return null;
        }
        return draft;
    }

    private static Long parseLong(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseCount(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    //Люди пишут «4 500,50 ₽» — принимаем как есть.
    private static BigDecimal parseMoney(String raw) {
        String normalized = raw.replace(',', '.').replaceAll("[\\s ₽]", "");
        try {
            BigDecimal value = new BigDecimal(normalized);
            return value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private enum Step {
        STORAGE, PRODUCT, COUNT, COST, COMMENT, CONFIRM
    }

    private static final class AdmissionDraft {
        private Step step = Step.STORAGE;
        private Long storageId;
        private String storageName;
        private Long productId;
        private String productName;
        private Integer count;
        private BigDecimal cost;
        private String comment;
        private Instant startedAt = Instant.now();
    }
}
