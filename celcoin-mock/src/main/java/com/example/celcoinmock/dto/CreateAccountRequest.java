package com.example.celcoinmock.dto;

public record CreateAccountRequest(
    String clientCode,
    String accountOnboardingType,
    String documentNumber,
    String phoneNumber,
    String email,
    String motherName,
    String fullName,
    String socialName,
    String birthDate,
    AddressDto address,
    Boolean isPoliticallyExposedPerson,
    Boolean cadastraChavePix
) {}
