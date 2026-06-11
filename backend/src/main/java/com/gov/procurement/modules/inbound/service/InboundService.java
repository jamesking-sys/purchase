package com.gov.procurement.modules.inbound.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.modules.inbound.dto.CreateInboundCmd;
import com.gov.procurement.modules.inbound.dto.CreateInboundVO;
import com.gov.procurement.modules.inbound.dto.InboundOrderVO;
import com.gov.procurement.modules.inbound.dto.PendingItemVO;

import java.util.List;

/**
 * 多次到货验收入库（U9）：单事务写入库单/明细 + 累加已收 + 库存记账 + 全收转 inbounded；以及入库记录查询。
 */
public interface InboundService {

    /**
     * 查某采购单各明细的待收情况（采购量/已收/待收），供仓管入库页选择实收（warehouse 可见，详设 U9 扩展）。
     * 采购单不存在 → 40401。
     *
     * @param purchaseOrderId 采购单 id
     * @return 待收明细列表
     */
    List<PendingItemVO> pendingItems(Long purchaseOrderId);

    /**
     * 入库：对 executing 采购单提交本次实收，单事务完成四表写入与库存 SoR 维护（详设 U9 §1/§3）。
     *
     * @param cmd 入库命令
     * @return 入库结果（入库单 id、采购单状态、各明细累计已收与库存项）
     */
    CreateInboundVO createInbound(CreateInboundCmd cmd);

    /**
     * 按采购单分页查询其多次入库记录（含明细）。
     *
     * @param purchaseOrderId 采购单 id
     * @param pageNum         页码（从 1 起）
     * @param size            每页大小
     * @return 入库记录分页
     */
    Page<InboundOrderVO> listByPurchaseOrder(Long purchaseOrderId, int pageNum, int size);
}
