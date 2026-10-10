package petr.warehouse.products_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

//Чат, куда бот шлёт уведомления. id назначает Telegram, свой не генерируем.
@Getter
@Setter
@NoArgsConstructor
@Table(name = "telegram_chat")
@Entity
public class TelegramChat {
    @Id
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "chat_type")
    private String chatType;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "registered_at", columnDefinition = "timestamptz")
    private Instant registeredAt;

    public TelegramChat(Long id, String title, String chatType) {
        this.id = id;
        this.title = title;
        this.chatType = chatType;
        this.active = true;
        this.registeredAt = Instant.now();
    }
}
