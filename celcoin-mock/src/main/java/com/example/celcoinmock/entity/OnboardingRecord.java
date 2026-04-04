package com.example.celcoinmock.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "celcoin_onboarding")
public class OnboardingRecord {

    @Id
    private String onboardingId; // UUID

    @Column(nullable = false, unique = true)
    private String clientCode;

    @Column(nullable = false, unique = true)
    private String documentNumber; // CPF

    @Column(nullable = false)
    private String fullName;

    private String socialName;
    private String email;
    private String phoneNumber;
    private String motherName;
    private String birthDate;

    // Address (flattened)
    private String postalCode;
    private String street;
    private String addressNumber;
    private String addressComplement;
    private String neighborhood;
    private String city;
    private String state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OnboardingStatus status = OnboardingStatus.PROCESSING;

    // Populated when status becomes CONFIRMED
    private String accountBranch;
    private String accountNumber;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public enum OnboardingStatus { PROCESSING, CONFIRMED, ERROR }

    // Getters/setters
    public String getOnboardingId() { return onboardingId; }
    public void setOnboardingId(String onboardingId) { this.onboardingId = onboardingId; }
    public String getClientCode() { return clientCode; }
    public void setClientCode(String clientCode) { this.clientCode = clientCode; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getSocialName() { return socialName; }
    public void setSocialName(String socialName) { this.socialName = socialName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getMotherName() { return motherName; }
    public void setMotherName(String motherName) { this.motherName = motherName; }
    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getAddressNumber() { return addressNumber; }
    public void setAddressNumber(String addressNumber) { this.addressNumber = addressNumber; }
    public String getAddressComplement() { return addressComplement; }
    public void setAddressComplement(String addressComplement) { this.addressComplement = addressComplement; }
    public String getNeighborhood() { return neighborhood; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public OnboardingStatus getStatus() { return status; }
    public void setStatus(OnboardingStatus status) { this.status = status; }
    public String getAccountBranch() { return accountBranch; }
    public void setAccountBranch(String accountBranch) { this.accountBranch = accountBranch; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
