package com.gov.procurement.modules.approval.dto;

import java.time.OffsetDateTime;

/**
 * 待办项视图：Flowable 活动任务 + approval 业务投影的合并展示（详设 U7 §6.2）。
 *
 * @param approvalId       审批单 id
 * @param taskId           Flowable 任务 id（处理时透传）
 * @param bizType          业务类型
 * @param bizId            业务单据 id
 * @param node             当前节点 code（purchase_mgr / dept_mgr）
 * @param budgetName       预算名称
 * @param projectGroupName 项目组名称
 * @param createdAt        任务创建时间
 */
public record TodoItemVO(
        Long approvalId,
        String taskId,
        String bizType,
        Long bizId,
        String node,
        String budgetName,
        String projectGroupName,
        OffsetDateTime createdAt) {
}
