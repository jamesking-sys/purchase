package com.gov.procurement.modules.budget.dto;

/**
 * 导入错误行（前端按 rowNo 定位高亮）。
 *
 * @param rowNo       用户可见行号（数据首行从 1 起）
 * @param subjectPath 科目路径 / 编码（便于定位）
 * @param amount      原始金额串
 * @param reason      错误原因
 */
public record ErrorRow(int rowNo, String subjectPath, String amount, String reason) {
}
