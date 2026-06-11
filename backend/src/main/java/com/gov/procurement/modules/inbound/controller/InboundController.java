package com.gov.procurement.modules.inbound.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.inbound.dto.CreateInboundCmd;
import com.gov.procurement.modules.inbound.dto.CreateInboundVO;
import com.gov.procurement.modules.inbound.dto.InboundOrderVO;
import com.gov.procurement.modules.inbound.dto.PendingItemVO;
import com.gov.procurement.modules.inbound.service.InboundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 验收入库接口（U9）：入库（创建入库单）/ 按采购单查入库记录。均限仓管员（warehouse）。
 */
@RestController
@RequestMapping("/api/inbounds")
public class InboundController {

    private final InboundService inboundService;

    public InboundController(InboundService inboundService) {
        this.inboundService = inboundService;
    }

    @SaCheckRole("warehouse")
    @PostMapping
    public Result<CreateInboundVO> create(@Valid @RequestBody CreateInboundCmd cmd) {
        return Result.ok(inboundService.createInbound(cmd));
    }

    @SaCheckRole("warehouse")
    @GetMapping("/pending-items")
    public Result<List<PendingItemVO>> pendingItems(@RequestParam Long purchaseOrderId) {
        return Result.ok(inboundService.pendingItems(purchaseOrderId));
    }

    @SaCheckRole("warehouse")
    @GetMapping
    public Result<Page<InboundOrderVO>> list(@RequestParam Long purchaseOrderId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(inboundService.listByPurchaseOrder(purchaseOrderId, page, size));
    }
}
