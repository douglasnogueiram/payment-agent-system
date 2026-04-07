package com.example.celcoinmock.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pix_keys")
public class PixKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keyValue;

    @Column(nullable = false)
    private String keyType; // CPF, PHONE, EMAIL, EVP

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private String ownerDocument; // CPF/CNPJ

    @Column(nullable = false)
    private String ownerType; // NATURAL_PERSON, LEGAL_PERSON

    @Column(nullable = false)
    private String participantIspb; // bank ISPB code

    @Column(nullable = false)
    private String branch;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String accountType; // CACC (checking), SVGS (savings)

    public Long getId() { return id; }
    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }
    public String getKeyType() { return keyType; }
    public void setKeyType(String keyType) { this.keyType = keyType; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerDocument() { return ownerDocument; }
    public void setOwnerDocument(String ownerDocument) { this.ownerDocument = ownerDocument; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public String getParticipantIspb() { return participantIspb; }
    public void setParticipantIspb(String participantIspb) { this.participantIspb = participantIspb; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
}
