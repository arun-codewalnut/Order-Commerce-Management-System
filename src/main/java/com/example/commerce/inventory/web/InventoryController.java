package com.example.commerce.inventory.web;

import com.example.commerce.inventory.internal.Inventory;
import com.example.commerce.inventory.internal.InventoryService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

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
