package com.gov.procurement.modules.budget.dto;

import java.math.BigDecimal;

/**
 * 按科目聚合的金额投影（预算 vs 实际两侧通用）。用类 + setter 承接，列名→属性靠 map-underscore-to-camel-case。
 */
public class SubjectAmountRow {

    private Long subjectId;
    private BigDecimal amount;

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
