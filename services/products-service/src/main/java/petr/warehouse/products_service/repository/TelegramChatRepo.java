package petr.warehouse.products_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import petr.warehouse.products_service.model.TelegramChat;

import java.util.List;

@Repository
public interface TelegramChatRepo extends JpaRepository<TelegramChat, Long> {
    List<TelegramChat> findAllByActiveTrue();
}
