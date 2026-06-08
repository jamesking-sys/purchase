package com.gov.procurement.modules.budget.dto;

import java.util.List;

/**
 * 单条科目路径的比对结果。
 *
 * @param path          原始科目名链
 * @param status        命中状态
 * @param subjectId     命中时为末级科目 id（缺失时为 null）
 * @param suggestedCode 缺失时给出的建议编码（命中时为 null）
 */
public record CompareResult(List<String> path, Status status, Long subjectId, String suggestedCode) {

    /** 比对命中状态。 */
    public enum Status {
        /** 库中已存在该路径科目。 */
        EXISTS,
        /** 库中缺失该路径科目。 */
        MISSING
    }
}
