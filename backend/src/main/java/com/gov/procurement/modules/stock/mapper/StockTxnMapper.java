package com.gov.procurement.modules.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.stock.domain.StockTxn;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * stock_txn Mapper（流水仅追加，不更新不软删）。
 */
public interface StockTxnMapper extends BaseMapper<StockTxn> {

    /**
     * 某库存项全部流水的 qty_change 之和（对账用，详设 U10 §5.4 / INV-1）。无流水返回 0。
     *
     * @param stockItemId 库存项 id
     * @return Σ qty_change（应 == stock_item.quantity）
     */
    @Select("SELECT COALESCE(SUM(qty_change), 0) FROM stock_txn WHERE stock_item_id = #{stockItemId}")
    BigDecimal sumQtyChange(@Param("stockItemId") Long stockItemId);
}
