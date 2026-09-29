package petr.warehouse.products_service.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import petr.warehouse.products_service.dto.OperationRequestDto;
import petr.warehouse.products_service.dto.StorageDto;
import petr.warehouse.products_service.dto.StorageInfoDto;
import petr.warehouse.products_service.dto.StorageItemDto;
import petr.warehouse.products_service.model.ItemStatus;
import petr.warehouse.products_service.model.OperationType;
import petr.warehouse.products_service.service.OperationService;
import petr.warehouse.products_service.service.StorageManagerService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

//Диалог «записать поступление» целиком, без сети и без базы.
class TelegramBotServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CHAT = 777L;
    private static final long GROUP = -100500L;

    private TelegramApiClient api;
    private OperationService operationService;
    private TelegramChatRegistry chats;
    private TelegramBotService bot;

    @BeforeEach
    void setUp() {
        api = mock(TelegramApiClient.class);
        operationService = mock(OperationService.class);
        chats = mock(TelegramChatRegistry.class);
        StorageManagerService storageService = mock(StorageManagerService.class);

        StorageInfoDto info = new StorageInfoDto();
        info.setId(1L);
        info.setName("Основной");

        StorageItemDto cups = new StorageItemDto();
        cups.setId(10L);
        cups.setName("Стаканы 300");
        cups.setCount(3);
        cups.setStatus(ItemStatus.FEW);

        StorageDto storage = new StorageDto();
        storage.setName("Основной");
        storage.setStorageItemListDto(List.of(cups));

        when(storageService.getAllStorages()).thenReturn(List.of(info));
        when(storageService.getStorageById(1L)).thenReturn(storage);

        bot = new TelegramBotService(api, storageService, operationService, chats);
    }

    @Test
    void writesAdmissionThroughFullDialog() {
        bot.handleUpdate(message("/admission"));
        bot.handleUpdate(callback("pr:1:10"));
        bot.handleUpdate(message("50"));
        bot.handleUpdate(message("4500,50"));
        bot.handleUpdate(callback("skip"));
        bot.handleUpdate(callback("ok"));

        ArgumentCaptor<OperationRequestDto> request = ArgumentCaptor.forClass(OperationRequestDto.class);
        verify(operationService).executeOperation(eq(1L), request.capture());

        assertThat(request.getValue().getOperationType()).isEqualTo(OperationType.ADMISSION);
        assertThat(request.getValue().getProductName()).isEqualTo("Стаканы 300");
        assertThat(request.getValue().getCount()).isEqualTo(50);
        assertThat(request.getValue().getOperationCost()).isEqualByComparingTo(new BigDecimal("4500.50"));
        assertThat(request.getValue().getComment()).isNull();
    }

    //Кнопка из уведомления «надо закупить» ведёт сразу к количеству.
    @Test
    void alertButtonSkipsStorageAndProductSteps() {
        bot.handleUpdate(callback("buy:1:10"));
        bot.handleUpdate(message("12"));
        bot.handleUpdate(message("900"));
        bot.handleUpdate(message("от поставщика"));
        bot.handleUpdate(callback("ok"));

        ArgumentCaptor<OperationRequestDto> request = ArgumentCaptor.forClass(OperationRequestDto.class);
        verify(operationService).executeOperation(eq(1L), request.capture());

        assertThat(request.getValue().getCount()).isEqualTo(12);
        assertThat(request.getValue().getOperationCost()).isEqualByComparingTo(new BigDecimal("900"));
        assertThat(request.getValue().getComment()).isEqualTo("от поставщика");
    }

    @Test
    void rejectsBadCountAndBadCost() {
        bot.handleUpdate(callback("buy:1:10"));
        bot.handleUpdate(message("ноль"));
        bot.handleUpdate(message("0"));
        bot.handleUpdate(message("7"));
        bot.handleUpdate(message("-5"));
        bot.handleUpdate(message("350"));
        bot.handleUpdate(callback("skip"));
        bot.handleUpdate(callback("ok"));

        ArgumentCaptor<OperationRequestDto> request = ArgumentCaptor.forClass(OperationRequestDto.class);
        verify(operationService).executeOperation(eq(1L), request.capture());

        assertThat(request.getValue().getCount()).isEqualTo(7);
        assertThat(request.getValue().getOperationCost()).isEqualByComparingTo(new BigDecimal("350"));
    }

    @Test
    void cancelDropsDraft() {
        bot.handleUpdate(callback("buy:1:10"));
        bot.handleUpdate(message("12"));
        bot.handleUpdate(message("/cancel"));
        bot.handleUpdate(callback("ok"));

        verify(operationService, never()).executeOperation(anyLong(), any());
    }

    @Test
    void unknownProductNameDoesNotStartOperation() {
        bot.handleUpdate(message("/admission"));
        bot.handleUpdate(message("Тарелки"));
        bot.handleUpdate(callback("ok"));

        verify(operationService, never()).executeOperation(anyLong(), any());
        verify(api).sendMessage(eq(CHAT), contains("нет товара"), any());
    }

    private static String contains(String fragment) {
        return org.mockito.ArgumentMatchers.argThat(text -> text != null && text.contains(fragment));
    }

    //Склады из системы показываются списком, по складу видны его остатки.
    @Test
    void listsStoragesAndShowsItemsOfOne() {
        bot.handleUpdate(message("/storages"));
        verify(api).sendMessage(eq(CHAT), contains("Склады"), any());

        bot.handleUpdate(callback("sv:1"));
        verify(api).sendMessage(eq(CHAT), contains("Стаканы 300 — 3 шт."), any());
    }

    //Кнопка поступления со страницы склада ведёт сразу к выбору товара.
    @Test
    void admissionFromStoragePageSkipsStorageStep() {
        bot.handleUpdate(callback("buy:1"));
        bot.handleUpdate(callback("pr:1:10"));
        bot.handleUpdate(message("4"));
        bot.handleUpdate(message("800"));
        bot.handleUpdate(callback("skip"));
        bot.handleUpdate(callback("ok"));

        ArgumentCaptor<OperationRequestDto> request = ArgumentCaptor.forClass(OperationRequestDto.class);
        verify(operationService).executeOperation(eq(1L), request.capture());
        assertThat(request.getValue().getCount()).isEqualTo(4);
    }

    //Бота добавили в группу — он сам подписывает её на уведомления и здоровается.
    @Test
    void subscribesGroupWhenAdded() {
        when(chats.remember(eq(-100500L), anyString(), anyString())).thenReturn(true);

        bot.handleUpdate(MAPPER.readTree("""
                {"my_chat_member":{
                   "chat":{"id":-100500,"title":"Склад Талант","type":"supergroup"},
                   "new_chat_member":{"status":"member"}}}"""));

        verify(chats).remember(-100500L, "Склад Талант", "supergroup");
        verify(api).sendMessage(eq(-100500L), contains("Я бот склада"), any());
    }

    @Test
    void unsubscribesGroupWhenKicked() {
        bot.handleUpdate(MAPPER.readTree("""
                {"my_chat_member":{
                   "chat":{"id":-100500,"title":"Склад Талант","type":"supergroup"},
                   "new_chat_member":{"status":"kicked"}}}"""));

        verify(chats).forget(-100500L);
        verify(chats, never()).remember(anyLong(), anyString(), anyString());
    }

    //Бот работает в любом чате: личка подписывается так же сама, как и группа.
    @Test
    void privateChatIsSubscribedAutomatically() {
        bot.handleUpdate(message("/start"));

        verify(chats).remember(eq(CHAT), anyString(), anyString());
    }

    //Отмена словом — на шаге, где бот ждёт количество, а не команду.
    @Test
    void cancelWordDropsDraftMidDialog() {
        bot.handleUpdate(callback("buy:1:10"));
        bot.handleUpdate(message("отмена"));
        bot.handleUpdate(message("12"));
        bot.handleUpdate(callback("ok"));

        verify(operationService, never()).executeOperation(anyLong(), any());
        verify(api).sendMessage(eq(CHAT), contains("Отменил"), any());
    }

    private static JsonNode message(String text) {
        return MAPPER.readTree("{\"message\":{\"chat\":{\"id\":" + CHAT + "},\"text\":\"" + text + "\"}}");
    }

    //В группе privacy mode не отдаёт боту обычный текст, поэтому числовые шаги
    //спрашиваются через force_reply — ответ на сообщение бота Telegram пропускает.
    @Test
    void asksCountWithForceReplyInGroup() {
        bot.handleUpdate(groupCallback("buy:1:10"));

        ArgumentCaptor<Object> markup = ArgumentCaptor.forClass(Object.class);
        verify(api).sendMessage(eq(GROUP), contains("Сколько штук пришло?"), markup.capture());
        assertThat(markup.getValue()).isEqualTo(Map.of("force_reply", true));
    }

    //В личке ничего не меняется: под вопросом остаётся кнопка отмены.
    @Test
    void asksCountWithCancelButtonInPrivateChat() {
        bot.handleUpdate(callback("buy:1:10"));

        ArgumentCaptor<Object> markup = ArgumentCaptor.forClass(Object.class);
        verify(api).sendMessage(eq(CHAT), contains("Сколько штук пришло?"), markup.capture());
        assertThat(markup.getValue().toString()).contains("inline_keyboard").contains("no");
    }

    private static JsonNode groupCallback(String data) {
        return MAPPER.readTree("{\"callback_query\":{\"id\":\"cb\",\"data\":\"" + data
                + "\",\"message\":{\"chat\":{\"id\":" + GROUP + ",\"type\":\"supergroup\"}}}}");
    }

    private static JsonNode callback(String data) {
        return MAPPER.readTree("{\"callback_query\":{\"id\":\"cb\",\"data\":\"" + data
                + "\",\"message\":{\"chat\":{\"id\":" + CHAT + "}}}}");
    }
}
