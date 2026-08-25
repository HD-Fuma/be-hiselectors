-- 정산 식별번호와 계좌번호의 AES-GCM 암호문을 저장할 공간을 확보한다.
ALTER TABLE settlement_account
    MODIFY COLUMN account_number VARCHAR(255) NULL,
    MODIFY COLUMN business_number VARCHAR(255) NULL;
