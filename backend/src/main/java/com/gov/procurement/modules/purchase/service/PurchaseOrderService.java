package com.gov.procurement.modules.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.modules.purchase.dto.CreatePurchaseOrderReq;
import com.gov.procurement.modules.purchase.dto.PurchaseOrderDetailVO;
import com.gov.procurement.modules.purchase.dto.PurchaseOrderVO;

/**
 * 采购单服务（U8）：创建（校验预算 approved + 科目叶子，单事务写主单+明细）、详情、分页列表。
 */
public interface PurchaseOrderService {

    /**
     * 创建采购单及明细。来源预算须存在且 approved；明细科目须命中已有叶子级科目。
     *
     * @param req 创建请求
     * @return 采购单视图（含明细）
     */
    PurchaseOrderVO createOrder(CreatePurchaseOrderReq req);

    /**
     * 采购单详情：主单 + 明细 + 到货单列表。
     *
     * @param orderId 采购单 id
     * @return 详情视图
     */
    PurchaseOrderDetailVO getDetail(Long orderId);

    /**
     * 采购单分页列表（可按状态 / 项目组过滤），列表项为摘要（不含明细）。
     *
     * @param status         状态过滤（可空）
     * @param projectGroupId 项目组过滤（可空）
     * @param pageNum        页码（从 1 起）
     * @param size           每页大小
     * @return 采购单分页
     */
    Page<PurchaseOrderVO> listOrders(String status, Long projectGroupId, int pageNum, int size);
}
