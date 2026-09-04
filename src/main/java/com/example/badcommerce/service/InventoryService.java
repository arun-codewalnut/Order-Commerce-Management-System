package com.example.badcommerce.service;

import com.example.badcommerce.entity.Inventory;
import com.example.badcommerce.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

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

