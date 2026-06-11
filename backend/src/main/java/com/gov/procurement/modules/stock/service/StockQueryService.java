package com.gov.procurement.modules.stock.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.modules.stock.dto.StockItemVO;
import com.gov.procurement.modules.stock.dto.StockTxnPageVO;

/**
 * 库存只读查询（M5/BC5 对外查询能力，详设 U10）。只读、无副作用：不写表、不持锁、不开写事务。
 * 与写入侧记账入口 {@link StockService} 分离——后者是 U9/U11/U12 的唯一记账口，本服务仅读取。
 */
public interface StockQueryService {

    /**
     * 库存分页查询：按项目组 / 部门 / 物料名（模糊）过滤，默认按 (project_group_id, material_name) 升序。
     * 过滤参数均可空，组合为 AND 交集；空过滤即全量分页（仍受 size 上限保护）。
     *
     * @param projectGroupId 归属项目组（可空，命中 idx_stock_pg）
     * @param departmentId   归属部门（可空）
     * @param materialName   物料名模糊（可空，ILIKE %kw%）
     * @param page           页码（≥1）
     * @param size           每页条数（1~200）
     * @return 库存项分页
     */
    Page<StockItemVO> pageStock(Long projectGroupId, Long departmentId, String materialName, int page, int size);

    /**
     * 库存流水分页查询：按 created_at 倒序（最新在前），含对账汇总 bookQty/txnSum（INV-1）。
     * 宿主库存项不存在 / 已删 → 40401（区别于流水为空的合法空集）。
     *
     * @param stockItemId 库存项 id（须存在且未删）
     * @param page        页码（≥1）
     * @param size        每页条数（1~200）
     * @return 流水分页 + 对账汇总
     */
    StockTxnPageVO pageTxn(Long stockItemId, int page, int size);
}
