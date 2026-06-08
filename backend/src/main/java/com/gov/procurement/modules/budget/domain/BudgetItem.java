package com.gov.procurement.modules.budget.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 预算明细（budget_item）。金额仅挂叶子科目；(budget_id, subject_id) 唯一。
 */
@TableName("budget_item")
public class BudgetItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long budgetId;
    private Long subjectId;
    private BigDecimal amount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public BudgetItem() {
    }

    public BudgetItem(Long budgetId, Long subjectId, BigDecimal amount) {
        this.budgetId = budgetId;
        this.subjectId = subjectId;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(Long budgetId) {
        this.budgetId = budgetId;
    }

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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
