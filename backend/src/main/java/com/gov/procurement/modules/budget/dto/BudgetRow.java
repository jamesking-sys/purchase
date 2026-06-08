package com.gov.procurement.modules.budget.dto;

import cn.idev.excel.annotation.ExcelProperty;

/**
 * 预算模板行（FastExcel 读写映射）。金额按字符串读入，便于原样回报错误行；解析校验在监听器内进行。
 * 列头：科目路径 | 科目编码 | 金额。
 */
public class BudgetRow {

    @ExcelProperty("科目路径")
    private String subjectPath;

    @ExcelProperty("科目编码")
    private String subjectCode;

    @ExcelProperty("金额")
    private String amount;

    public BudgetRow() {
    }

    public BudgetRow(String subjectPath, String subjectCode, String amount) {
        this.subjectPath = subjectPath;
        this.subjectCode = subjectCode;
        this.amount = amount;
    }

    public String getSubjectPath() {
        return subjectPath;
    }

    public void setSubjectPath(String subjectPath) {
        this.subjectPath = subjectPath;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }
}
