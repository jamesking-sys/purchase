package com.gov.procurement.modules.budget.service;

import com.gov.procurement.modules.budget.dto.AttachmentVO;
import com.gov.procurement.modules.budget.dto.ImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 预算模板导入服务：下载模板、附件留档、解析导入（校验 + 落库）。
 */
public interface BudgetImportService {

    /** 写出标准预算模板 .xlsx（列头 + 1 行示例）。 */
    void downloadTemplate(OutputStream out);

    /** 立项文档留档（仅存储不解析），返回相对路径等元信息。 */
    AttachmentVO storeAttachment(MultipartFile file, Long projectGroupId);

    /**
     * 解析预算明细并导入：校验通过则生成 budget(draft) + budget_item，否则返回错误行清单（零落库）。
     *
     * @param projectGroupId 目标项目组（须存在未删，否则抛 40401）
     * @param name           预算名称
     * @param sourceDocPath  立项文档留档路径（可空）
     * @param excelStream    预算明细 .xlsx 输入流
     * @return 导入结果（成功或错误清单）
     */
    ImportResult importBudget(Long projectGroupId, String name, String sourceDocPath, InputStream excelStream);
}
