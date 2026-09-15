package petr.warehouse.inventory_management.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import petr.warehouse.inventory_management.dto.OperationDto;
import petr.warehouse.inventory_management.dto.OperationRequestDto;
import petr.warehouse.inventory_management.dto.StorageInfoDto;
import petr.warehouse.inventory_management.filter.OperationFilter;
import petr.warehouse.inventory_management.service.OperationService;
import petr.warehouse.inventory_management.service.StorageManagerService;
import petr.warehouse.inventory_management.dto.StorageDto;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@RestController()
@Validated
@RequestMapping("storage")
public class InventoryController {
    private final StorageManagerService storageService;
    private final OperationService operationService;

    @Autowired
    public InventoryController(StorageManagerService storageService, OperationService operationService){
        this.storageService = storageService;
        this.operationService = operationService;
    }

    @GetMapping
    ResponseEntity<List<StorageInfoDto>> getAllStorages(){
        return ResponseEntity.ok(storageService.getAllStorages());
    }

    //Получить данные склада по id
    @GetMapping("/{storageId}")
    public ResponseEntity<StorageDto> getStorage(
            @PathVariable @Positive Long storageId
    ){
        return ResponseEntity.ok(storageService.getStorageById(storageId));
    }

    //Добавить склад
    @PostMapping
    public ResponseEntity<Void> postNewStorage(
            @RequestParam @NotBlank @Size(max = 25) String name
    ){
        Long storageId = storageService.createStorage(name);
        URI location = URI.create("/storage/" + storageId);
        return ResponseEntity.created(location).build();
    }

    //Добавить новый продукт
    @PostMapping("/{storageId}/products")
    public ResponseEntity<Void> postNewProduct(
            @PathVariable @Positive Long storageId,
            @NotBlank @Size(max = 100) @RequestParam String productName,
            @RequestParam @Positive BigDecimal productCost
    ){
        storageService.addProduct(storageId, productName, productCost);
        URI location = URI.create("/storage/" + storageId + "/products/" + productName);
        return ResponseEntity.created(location).build();
    }

    //Удалить продукт
    @DeleteMapping("/{storageId}/products/{productId}")
    public ResponseEntity<?> deleteProduct(
            @PathVariable @Positive Long storageId,
            @PathVariable @Positive Long productId
    ){
        storageService.deleteProduct(storageId, productId);
        return ResponseEntity.noContent().build();
    }

    //Изменить продукт
    @PostMapping("/{storageId}/operation")
    public ResponseEntity<Void> executeOperation(
            @PathVariable @Positive Long storageId,
            @Valid @ParameterObject OperationRequestDto requestBody
    ){
        operationService.executeOperation(storageId, requestBody);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/operations")
    public Page<OperationDto> getOperations(
            @ParameterObject @Valid OperationFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "operationDateTime", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return operationService.getOperations(
                filter,
                pageable
        );
    }

    @DeleteMapping("/operations/{operationId}")
    public ResponseEntity<Void> cancelOperation(
            @PathVariable @Positive Long operationId
    ) {
        operationService.cancelOperation(operationId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
