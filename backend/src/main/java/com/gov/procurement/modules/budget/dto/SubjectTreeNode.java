package com.gov.procurement.modules.budget.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 科目树节点（可变，便于自底向上组装与金额后序汇总）。
 * {@code amount}：叶子=自身预算金额（无则 0），非叶=子树叶子之和——查询时计算，不落库。
 * {@code lazy} 取数时 children 为空，前端按 parentId 再拉单层。
 */
public class SubjectTreeNode {

    private Long id;
    private Long parentId;
    private String name;
    private String code;
    private Integer level;
    private Boolean isLeaf;
    private BigDecimal amount;
    private List<SubjectTreeNode> children = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Boolean getIsLeaf() {
        return isLeaf;
    }

    public void setIsLeaf(Boolean isLeaf) {
        this.isLeaf = isLeaf;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public List<SubjectTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<SubjectTreeNode> children) {
        this.children = children;
    }
}
