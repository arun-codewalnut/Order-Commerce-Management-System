package com.example.commerce.inventory.internal;

import com.example.commerce.inventory.internal.Inventory;
import com.example.commerce.inventory.internal.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public Inventory getInventoryByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseGet(() -> {
                    Inventory inv = new Inventory();
                    inv.setProductId(productId);
                    inv.setQuantity(0);
                    inv.setReservedQuantity(0);
                    return inventoryRepository.save(inv);
                });
    }

    public Inventory updateInventory(Long productId, int quantity, int reservedQuantity) {
        Inventory inv = getInventoryByProductId(productId);
        inv.setQuantity(quantity);
        inv.setReservedQuantity(reservedQuantity);
        return inventoryRepository.save(inv);
    }
}
