package petr.warehouse.products_service.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import petr.warehouse.products_service.model.TelegramChat;
import petr.warehouse.products_service.repository.TelegramChatRepo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

//Бот работает в любой группе, куда его добавили: список чатов копится сам.
//В памяти держим только кеш «этот чат уже активен», чтобы не ходить в базу на каждое сообщение.
@Service
public class TelegramChatRegistry {
    private static final Logger log = LoggerFactory.getLogger(TelegramChatRegistry.class);

    private final TelegramChatRepo repo;
    private final Map<Long, Boolean> knownActive = new ConcurrentHashMap<>();

    public TelegramChatRegistry(TelegramChatRepo repo) {
        this.repo = repo;
    }

    //true, если чат подписали прямо сейчас — по этому признаку бот здоровается в новой группе.
    @Transactional
    public boolean remember(long chatId, String title, String chatType) {
        if (Boolean.TRUE.equals(knownActive.get(chatId))) {
            return false;
        }

        Optional<TelegramChat> existing = repo.findById(chatId);
        boolean activated;

        if (existing.isPresent()) {
            TelegramChat chat = existing.get();
            activated = !Boolean.TRUE.equals(chat.getActive());
            chat.setActive(true);
            chat.setTitle(title);
            chat.setChatType(chatType);
            repo.save(chat);
        } else {
            repo.save(new TelegramChat(chatId, title, chatType));
            activated = true;
        }

        knownActive.put(chatId, true);
        if (activated) {
            log.info("Телеграм-чат {} ({}) подписан на уведомления", chatId, title);
        }
        return activated;
    }

    @Transactional
    public void forget(long chatId) {
        knownActive.remove(chatId);
        repo.findById(chatId).ifPresent(chat -> {
            chat.setActive(false);
            repo.save(chat);
            log.info("Телеграм-чат {} отписан от уведомлений", chatId);
        });
    }

    @Transactional(readOnly = true)
    public boolean isSubscribed(long chatId) {
        if (Boolean.TRUE.equals(knownActive.get(chatId))) {
            return true;
        }
        return repo.findById(chatId).map(chat -> Boolean.TRUE.equals(chat.getActive())).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<Long> subscribedChats() {
        return repo.findAllByActiveTrue().stream().map(TelegramChat::getId).toList();
    }
}
