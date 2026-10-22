package com.minierp.platform.catalog.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Fails closed until a transaction module can prove no product history. / Tu
 * choi an toan khi chua co module chung minh khong co lich su.
 */
@Component
public class FailClosedProductTransactionHistoryChecker implements ProductTransactionHistoryChecker {
    public HistoryState historyState(UUID productId) {
        return HistoryState.UNKNOWN;
    }
}
