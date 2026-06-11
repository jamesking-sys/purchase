package com.gov.procurement.modules.stocktake.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.stocktake.dto.ConfirmStocktakeVO;
import com.gov.procurement.modules.stocktake.dto.CreateStocktakeCmd;
import com.gov.procurement.modules.stocktake.dto.CreateStocktakeVO;
import com.gov.procurement.modules.stocktake.dto.SaveActualsCmd;
import com.gov.procurement.modules.stocktake.dto.SaveActualsVO;
import com.gov.procurement.modules.stocktake.service.StocktakeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 盘点接口（U12）：发起盘点（快照账面）/ 录入实盘 / 确认调整（调库存 + 记 gain/loss 流水）。三接口均限仓管员（warehouse）。
 */
@RestController
@RequestMapping("/api/stocktakes")
public class StocktakeController {

    private static final String ROLE_WAREHOUSE = "warehouse";

    private final StocktakeService stocktakeService;

    public StocktakeController(StocktakeService stocktakeService) {
        this.stocktakeService = stocktakeService;
    }

    @SaCheckRole(ROLE_WAREHOUSE)
    @PostMapping
    public Result<CreateStocktakeVO> create(@Valid @RequestBody CreateStocktakeCmd cmd) {
        return Result.ok(stocktakeService.createStocktake(cmd));
    }

    @SaCheckRole(ROLE_WAREHOUSE)
    @PutMapping("/{id}/items")
    public Result<SaveActualsVO> saveActuals(@PathVariable Long id, @Valid @RequestBody SaveActualsCmd cmd) {
        return Result.ok(stocktakeService.saveActuals(id, cmd));
    }

    @SaCheckRole(ROLE_WAREHOUSE)
    @PostMapping("/{id}/confirm")
    public Result<ConfirmStocktakeVO> confirm(@PathVariable Long id) {
        return Result.ok(stocktakeService.confirm(id));
    }
}
