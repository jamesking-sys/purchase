package com.gov.procurement.modules.stock.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.stock.dto.StockItemVO;
import com.gov.procurement.modules.stock.dto.StockTxnPageVO;
import com.gov.procurement.modules.stock.service.StockQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存查询接口（U10）：库存分页查询 / 某库存项流水倒序分页。均只读、限仓管或管理类角色（warehouse|admin）。
 */
@RestController
@RequestMapping("/api/stocks")
public class StockQueryController {

    private static final String ROLE_WAREHOUSE = "warehouse";
    private static final String ROLE_ADMIN = "admin";

    private final StockQueryService stockQueryService;

    public StockQueryController(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
    }

    @SaCheckRole(value = {ROLE_WAREHOUSE, ROLE_ADMIN}, mode = SaMode.OR)
    @GetMapping
    public Result<Page<StockItemVO>> list(@RequestParam(required = false) Long projectGroupId,
                                          @RequestParam(required = false) Long departmentId,
                                          @RequestParam(required = false) String materialName,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(stockQueryService.pageStock(projectGroupId, departmentId, materialName, page, size));
    }

    @SaCheckRole(value = {ROLE_WAREHOUSE, ROLE_ADMIN}, mode = SaMode.OR)
    @GetMapping("/{id}/txns")
    public Result<StockTxnPageVO> txns(@PathVariable Long id,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return Result.ok(stockQueryService.pageTxn(id, page, size));
    }
}
