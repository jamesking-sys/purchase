package com.gov.procurement.modules.budget.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 确认新增请求：仅对前端勾选确认的「缺失」科目路径写库，逐项自上而下补建缺失各级。
 *
 * @param items 已确认的缺失项
 */
public record ConfirmAddReq(@NotEmpty(message = "确认新增项不能为空") List<ConfirmAddItem> items) {

    /**
     * 单条确认项。
     *
     * @param path  科目名链（自根到末级）
     * @param codes 各级编码（与 path 等长，可选；缺省由服务端生成占位编码）
     */
    public record ConfirmAddItem(@NotEmpty(message = "科目路径不能为空") List<String> path, List<String> codes) {
    }
}
