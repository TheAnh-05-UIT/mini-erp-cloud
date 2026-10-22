package com.minierp.platform.catalog.application;

import java.util.UUID;

/**
 * Public future-module contract for category reassignment safety. / Hop dong
 * module tuong lai ve an toan doi danh muc.
 */
public interface ProductTransactionHistoryChecker {
    HistoryState historyState(UUID productId);

    enum HistoryState {
        NO_HISTORY, HAS_HISTORY, UNKNOWN
    }
}
