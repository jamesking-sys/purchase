package com.gov.procurement.modules.budget.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.budget.dto.AttachmentVO;
import com.gov.procurement.modules.budget.dto.ImportResult;
import com.gov.procurement.modules.budget.dto.ImportResultVO;
import com.gov.procurement.modules.budget.service.BudgetImportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 预算模板导入接口：下载模板 / 附件留档 / 导入预算。均需 editor 角色。
 * 导入校验失败返回 HTTP 400 + Result(42201/42202, data=errorRows)；项目组不存在 → 40401（经全局处理器）。
 */
@RestController
@RequestMapping("/api/budget")
public class BudgetImportController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final BudgetImportService budgetImportService;

    public BudgetImportController(BudgetImportService budgetImportService) {
        this.budgetImportService = budgetImportService;
    }

    @SaCheckRole("editor")
    @PostMapping("/template/download")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename=\"budget-template.xlsx\"");
        budgetImportService.downloadTemplate(response.getOutputStream());
    }

    @SaCheckRole("editor")
    @PostMapping("/attachment")
    public Result<AttachmentVO> uploadAttachment(@RequestParam("file") MultipartFile file,
                                                 @RequestParam Long projectGroupId) {
        return Result.ok(budgetImportService.storeAttachment(file, projectGroupId));
    }

    @SaCheckRole("editor")
    @PostMapping("/import")
    public ResponseEntity<Result<?>> importBudget(@RequestParam Long projectGroupId,
                                                  @RequestParam String name,
                                                  @RequestParam(required = false) String sourceDocPath,
                                                  @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导入文件不能为空");
        }
        ImportResult result;
        try (InputStream in = file.getInputStream()) {
            result = budgetImportService.importBudget(projectGroupId, name, sourceDocPath, in);
        }
        if (!result.hasErrors()) {
            Result<?> ok = Result.ok(new ImportResultVO(result.budgetId(), result.importedRows()));
            return ResponseEntity.ok(ok);
        }
        Result<?> failure = new Result<>(result.errorCode(), ErrorCode.TEMPLATE_INVALID.message(),
                Map.of("errorRows", result.errorRows()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(failure);
    }
}
