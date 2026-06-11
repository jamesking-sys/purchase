package com.gov.procurement.modules.requisition.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.modules.requisition.dto.ApproveOutboundVO;
import com.gov.procurement.modules.requisition.dto.CreateRequisitionCmd;
import com.gov.procurement.modules.requisition.dto.CreateRequisitionVO;
import com.gov.procurement.modules.requisition.dto.RejectReq;
import com.gov.procurement.modules.requisition.dto.RejectVO;
import com.gov.procurement.modules.requisition.dto.RequisitionDetailVO;
import com.gov.procurement.modules.requisition.dto.RequisitionTodoVO;
import com.gov.procurement.modules.requisition.dto.RequisitionVO;
import com.gov.procurement.modules.requisition.dto.StockOptionVO;

import java.util.List;

/**
 * 领用 + 仓管审批出库（U11）：发起领用 / 待办 / 审批出库（单事务行锁防超发）/ 驳回 / 查询。
 */
public interface RequisitionService {

    /**
     * 某项目组的可领用库存项（id/物料名/当前量），供领用人在领用页挑选（requester 可见，详设 U11 扩展）。
     * 项目组不存在 → 40401。
     *
     * @param projectGroupId 项目组 id
     * @return 库存项选项列表
     */
    List<StockOptionVO> stockOptions(Long projectGroupId);

    /**
     * 发起领用：写 requisition + requisition_item，状态 pending_warehouse。
     *
     * @param cmd 领用命令
     * @return 领用单 id 与状态
     */
    CreateRequisitionVO create(CreateRequisitionCmd cmd);

    /**
     * 仓管待办：分页返回 pending_warehouse 领用单，含明细当前库存与是否充足。
     *
     * @param projectGroupId 项目组过滤（可空）
     * @param pageNum        页码
     * @param size           每页大小
     * @return 待办分页
     */
    Page<RequisitionTodoVO> todo(Long projectGroupId, int pageNum, int size);

    /**
     * 审批出库：单事务行锁防超发——锁库存项→校验→扣减→流水→出库单/明细→状态 outbound。任一步失败整体回滚。
     *
     * @param requisitionId 领用单 id
     * @return 领用单与出库单 id、状态
     */
    ApproveOutboundVO approveOutbound(Long requisitionId);

    /**
     * 驳回：意见必填，状态 rejected 并记录意见。
     *
     * @param requisitionId 领用单 id
     * @param req           驳回请求
     * @return 领用单 id 与状态
     */
    RejectVO reject(Long requisitionId, RejectReq req);

    /**
     * 领用单分页列表（可按状态 / 项目组 / 申请人过滤）。
     *
     * @param status         状态过滤（可空）
     * @param projectGroupId 项目组过滤（可空）
     * @param applicantId    申请人过滤（可空）
     * @param pageNum        页码
     * @param size           每页大小
     * @return 列表分页
     */
    Page<RequisitionVO> list(String status, Long projectGroupId, Long applicantId, int pageNum, int size);

    /**
     * 领用单详情：领用单 + 明细 + 若已出库含出库单/明细。
     *
     * @param requisitionId 领用单 id
     * @return 详情视图
     */
    RequisitionDetailVO getDetail(Long requisitionId);
}
