package com.example.celcoinmock.dto;

public record AddressDto(
    String postalCode,
    String street,
    String number,
    String addressComplement,
    String neighborhood,
    String city,
    String state,
    String longitude,
    String latitude
) {}
