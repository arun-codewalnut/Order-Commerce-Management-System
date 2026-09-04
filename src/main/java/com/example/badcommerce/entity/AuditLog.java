package com.example.badcommerce.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entityName;

    private Long entityId;

    private String action;

    private String performedBy;

    @Column(length = 2000)
    private String details;

    private LocalDateTime timestamp;

    @PrePersist
    public void onPrePersist() {
        this.timestamp = LocalDateTime.now();
    }
}

