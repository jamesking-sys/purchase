package com.gov.procurement.modules.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.stock.domain.StockItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * stock_item Mapper：原子 upsert（按聚合键累加库存量），并发安全。
 */
public interface StockItemMapper extends BaseMapper<StockItem> {

    /**
     * 按聚合键 (material_name, project_group_id) 原子 upsert：命中未删库存项则 quantity += qty，否则新建。
     * 借助部分唯一索引 uk_stock 的 ON CONFLICT 处理并发新建/并发累加竞态（详设 U9 §5.2 / AC-7），返回库存项 id。
     *
     * @param materialName   物料名（聚合键）
     * @param projectGroupId 项目组 id（聚合键）
     * @param departmentId   部门 id（新建时冗余写入）
     * @param qty            本次增量（> 0）
     * @return 库存项 id
     */
    @Select("INSERT INTO stock_item (material_name, project_group_id, department_id, quantity) "
            + "VALUES (#{materialName}, #{projectGroupId}, #{departmentId}, #{qty}) "
            + "ON CONFLICT (material_name, project_group_id) WHERE is_deleted = 0 "
            + "DO UPDATE SET quantity = stock_item.quantity + EXCLUDED.quantity "
            + "RETURNING id")
    Long upsertAddQuantity(@Param("materialName") String materialName,
                           @Param("projectGroupId") Long projectGroupId,
                           @Param("departmentId") Long departmentId,
                           @Param("qty") BigDecimal qty);

    /**
     * 扣减库存量（出库）。调用方须先以 FOR UPDATE 锁行 + 校验 quantity>=qty 后再调用；
     * CHECK(quantity>=0) 为最终兜底（绕过应用层的负库存写入被 DB 拒绝）。
     *
     * @param id  库存项 id
     * @param qty 扣减量（> 0）
     * @return 影响行数
     */
    @Update("UPDATE stock_item SET quantity = quantity - #{qty} WHERE id = #{id}")
    int deductQuantity(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /**
     * 绝对值置数（盘点确认校正）。调用方须先以 FOR UPDATE 锁行；CHECK(quantity>=0) 为最终兜底
     * （盘点实盘数已校验 ≥0，详设 U12 §5.2 / INV-2）。
     *
     * @param id  库存项 id
     * @param qty 目标库存量（= 实盘数，≥ 0）
     * @return 影响行数
     */
    @Update("UPDATE stock_item SET quantity = #{qty} WHERE id = #{id}")
    int setQuantity(@Param("id") Long id, @Param("qty") BigDecimal qty);
}
