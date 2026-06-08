package com.gov.procurement.modules.budget.dto;

import java.util.List;

/**
 * 模糊搜索命中项。{@code ancestorPath} 为「根 → … → 命中」的面包屑，便于前端在树中定位高亮。
 *
 * @param id           命中科目 id
 * @param name         名称
 * @param code         编码
 * @param level        层级
 * @param isLeaf       是否叶子
 * @param ancestorPath 祖先路径（含命中节点自身，根在前）
 */
public record SubjectHit(Long id, String name, String code, Integer level, Boolean isLeaf,
                         List<Ancestor> ancestorPath) {

    /**
     * 祖先节点（仅 id 与 name，供面包屑展示）。
     *
     * @param id   节点 id
     * @param name 节点名称
     */
    public record Ancestor(Long id, String name) {
    }
}
