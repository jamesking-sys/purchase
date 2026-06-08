package com.gov.procurement.modules.budget.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 科目比对请求。每条路径为有序科目名链（如 ["耗材","试剂"]）。
 *
 * @param paths 待比对的科目路径列表
 */
public record CompareReq(@NotEmpty(message = "比对路径不能为空") List<List<String>> paths) {
}
