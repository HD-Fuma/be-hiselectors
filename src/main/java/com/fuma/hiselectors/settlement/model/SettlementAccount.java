package com.fuma.hiselectors.settlement.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "settlement_account", uniqueConstraints = @UniqueConstraint(
        name = "uk_settlement_account_selectors",
        columnNames = "selectors_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_account_id")
    private Long id;

    @Column(name = "selectors_id", nullable = false)
    private Long selectorsId;

    @Column(name = "bank_name", length = 20)
    private String bankName;

    @Column(name = "account_number", length = 255)
    private String accountNumberEncrypted;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    @Column(name = "business_number", length = 255)
    private String businessNumberEncrypted;

    @Column(name = "settlement_type", length = 50)
    private String settlementType;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Builder
    private SettlementAccount(Long selectorsId, String bankName, String accountNumberEncrypted,
                              String accountHolder, String businessNumberEncrypted,
                              String settlementType) {
        this.selectorsId = selectorsId;
        this.bankName = bankName;
        this.accountNumberEncrypted = accountNumberEncrypted;
        this.accountHolder = accountHolder;
        this.businessNumberEncrypted = businessNumberEncrypted;
        this.settlementType = settlementType;
        this.deleted = false;
    }

    public void update(String bankName, String accountNumberEncrypted, String accountHolder) {
        this.bankName = bankName;
        this.accountNumberEncrypted = accountNumberEncrypted;
        this.accountHolder = accountHolder;
    }

    public void registerIdentity(SettlementType settlementType, String businessNumberEncrypted) {
        this.settlementType = settlementType.name();
        this.businessNumberEncrypted = businessNumberEncrypted;
    }

    public void updateBusinessNumber(String businessNumberEncrypted) {
        this.businessNumberEncrypted = businessNumberEncrypted;
    }
}
