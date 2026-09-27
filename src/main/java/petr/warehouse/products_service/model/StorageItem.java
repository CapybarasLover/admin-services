package petr.warehouse.products_service.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@NoArgsConstructor
@Setter
@Getter
@Table(name = "Item", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"item", "storage_id"})
})
@Entity
public class StorageItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_id")
    private Storage storage;

    @Column(name = "item")
    private String itemName;

    @Column(name = "count")
    private Integer itemCount;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ItemStatus itemStatus;

    @Column(name = "cost")
    private BigDecimal cost;

    public StorageItem(String itemName, Storage storage, BigDecimal cost){
        this.itemName = itemName;
        this.storage = storage;
        itemCount = 0;
        this.cost = cost;
        this.itemStatus = ItemStatus.OUT;
    }

    public void addCount(Integer itemCount){
        this.itemCount += itemCount;
        changeStatus();
    }

    private void changeStatus(){
        if(itemCount == 0){
            itemStatus = ItemStatus.OUT;
        } else if(itemCount < 10){
            itemStatus = ItemStatus.FEW;
        }
        else {
            itemStatus = ItemStatus.ENOUGH;
        }
    }

    public void subtractCount(Integer minusCount) {
        if(minusCount > itemCount){
            throw new RuntimeException("Невозможно списать столько продукта");
        }
        itemCount -= minusCount;
        changeStatus();
    }
}
