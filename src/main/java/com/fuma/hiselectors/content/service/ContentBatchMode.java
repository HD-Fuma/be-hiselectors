package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.util.Map;
import java.util.Objects;

public enum ContentBatchMode {

    STANDARD(Map.of()),
    FAST(Map.of(
            SnsPlatform.YOUTUBE, "UCD2RQE52TloxzZxZ2fyq8HQ",
            SnsPlatform.INSTAGRAM, "hi_selectors"));

    private final Map<SnsPlatform, String> targetAccountIds;

    ContentBatchMode(Map<SnsPlatform, String> targetAccountIds) {
        this.targetAccountIds = Map.copyOf(targetAccountIds);
    }

    public boolean includes(SelectorsSnsAccount account) {
        Objects.requireNonNull(account, "SNS 계정은 필수입니다.");
        if (targetAccountIds.isEmpty()) {
            return true;
        }
        String targetAccountId = targetAccountIds.get(account.getSnsCode());
        if (targetAccountId == null || account.getAccountId() == null) {
            return false;
        }
        String accountId = account.getAccountId().trim();
        if (accountId.startsWith("@")) {
            accountId = accountId.substring(1);
        }
        return account.getSnsCode() == SnsPlatform.INSTAGRAM
                ? targetAccountId.equalsIgnoreCase(accountId)
                : targetAccountId.equals(accountId);
    }

    public Map<SnsPlatform, String> targetAccountIds() {
        return targetAccountIds;
    }
}
