package com.fuma.hiselectors.settlement.repository;

import com.fuma.hiselectors.settlement.model.SettlementAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementAccountRepository extends JpaRepository<SettlementAccount, Long> {

    Optional<SettlementAccount> findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(Long selectorsId);
}
