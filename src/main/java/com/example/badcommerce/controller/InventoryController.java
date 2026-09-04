package com.example.badcommerce.controller;

import com.example.badcommerce.entity.Inventory;
import com.example.badcommerce.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @GetMapping("/{productId}")
    public Inventory getInventory(@PathVariable Long productId) {
        return inventoryService.getInventoryByProductId(productId);
    }

    @PutMapping("/{productId}")
    public Inventory updateInventory(
            @PathVariable Long productId,
            @RequestBody Map<String, Integer> updatePayload) {
        int quantity = updatePayload.getOrDefault("quantity", 0);
        int reserved = updatePayload.getOrDefault("reservedQuantity", 0);
        return inventoryService.updateInventory(productId, quantity, reserved);
    }
}

