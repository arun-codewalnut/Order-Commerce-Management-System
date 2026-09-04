package com.example.badcommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponse<T> {
    private T data;
    private String message;
    private String timestamp;

    public static <T> GenericResponse<T> of(T data) {
        return new GenericResponse<>(data, "SUCCESS", java.time.LocalDateTime.now().toString());
    }

    public static <T> GenericResponse<T> of(T data, String message) {
        return new GenericResponse<>(data, message, java.time.LocalDateTime.now().toString());
    }
}

