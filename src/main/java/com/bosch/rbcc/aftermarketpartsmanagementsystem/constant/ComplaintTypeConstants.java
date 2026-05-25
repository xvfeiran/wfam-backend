package com.bosch.rbcc.aftermarketpartsmanagementsystem.constant;

import java.util.Set;

public final class ComplaintTypeConstants {

    /** BA40（field product）和 BA41（field campaign）是售后件，其余全部视为0km。 */
    public static final Set<String> AFTERMARKET_TYPES = Set.of("BA40", "BA41");

    private ComplaintTypeConstants() {}

    /** complaintType 不在 AFTERMARKET_TYPES 内（且非空）即为0km。 */
    public static boolean isZeroKm(String complaintType) {
        return complaintType != null
                && !complaintType.isBlank()
                && !AFTERMARKET_TYPES.contains(complaintType);
    }
}
