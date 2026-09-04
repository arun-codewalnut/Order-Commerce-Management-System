package com.example.badcommerce.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "addresses")
@Data
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;

    private String street;

    private String city;

    private String state;

    private String zipCode;

    private String country;
}

