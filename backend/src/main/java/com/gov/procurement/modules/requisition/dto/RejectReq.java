package com.gov.procurement.modules.requisition.dto;

import jakarta.validation.constraints.Size;

/**
 * 驳回请求。意见必填校验在服务层（空白 → 42203），此处仅约束长度。
 *
 * @param opinion 驳回意见（必填、≤512，服务层校验非空白）
 */
public record RejectReq(
        @Size(max = 512, message = "驳回意见不超过 512 字") String opinion) {
}
