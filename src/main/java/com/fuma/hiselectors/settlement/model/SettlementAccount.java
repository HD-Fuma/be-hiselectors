package com.fuma.hiselectors.settlement.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "settlement_account")
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

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    @Column(name = "business_number", length = 50)
    private String businessNumber;

    @Column(name = "settlement_type", length = 50)
    private String settlementType;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
}
