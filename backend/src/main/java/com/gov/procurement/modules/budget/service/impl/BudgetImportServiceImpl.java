package com.gov.procurement.modules.budget.service.impl;

import cn.idev.excel.FastExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.budget.domain.Budget;
import com.gov.procurement.modules.budget.domain.BudgetItem;
import com.gov.procurement.modules.budget.domain.BudgetSubject;
import com.gov.procurement.modules.budget.dto.AttachmentVO;
import com.gov.procurement.modules.budget.dto.BudgetRow;
import com.gov.procurement.modules.budget.dto.ErrorRow;
import com.gov.procurement.modules.budget.dto.ImportResult;
import com.gov.procurement.modules.budget.mapper.BudgetItemMapper;
import com.gov.procurement.modules.budget.mapper.BudgetMapper;
import com.gov.procurement.modules.budget.mapper.BudgetSubjectMapper;
import com.gov.procurement.modules.budget.service.BudgetImportService;
import com.gov.procurement.modules.org.mapper.ProjectGroupMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 预算导入服务实现。FastExcel 流式解析 + 行级校验，零落库原则；校验通过才进入单事务落库。
 */
@Service
public class BudgetImportServiceImpl implements BudgetImportService {

    private static final Logger log = LoggerFactory.getLogger(BudgetImportServiceImpl.class);

    private static final String TEMPLATE_SHEET = "预算模板";
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "xls", "xlsx", "png", "jpg");
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BudgetSubjectMapper subjectMapper;
    private final BudgetMapper budgetMapper;
    private final BudgetItemMapper budgetItemMapper;
    private final ProjectGroupMapper projectGroupMapper;
    private final String uploadDir;

    public BudgetImportServiceImpl(BudgetSubjectMapper subjectMapper, BudgetMapper budgetMapper,
                                   BudgetItemMapper budgetItemMapper, ProjectGroupMapper projectGroupMapper,
                                   @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.subjectMapper = subjectMapper;
        this.budgetMapper = budgetMapper;
        this.budgetItemMapper = budgetItemMapper;
        this.projectGroupMapper = projectGroupMapper;
        this.uploadDir = uploadDir;
    }

    @Override
    public void downloadTemplate(OutputStream out) {
        BudgetRow example = new BudgetRow("设备采购 / 服务器 / 计算节点", "SB-FWQ-JS01", "120000.00");
        FastExcel.write(out, BudgetRow.class).sheet(TEMPLATE_SHEET).doWrite(List.of(example));
    }

    @Override
    public AttachmentVO storeAttachment(MultipartFile file, Long projectGroupId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "附件不能为空");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "附件大小超过上限（20MB）");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = original.replaceAll("[\\\\/]", "_").replace("..", "_");
        String ext = extensionOf(safeName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的附件类型：" + ext);
        }
        String relativePath = String.format("budget/%d/%s/%s-%s",
                projectGroupId, LocalDate.now().format(DATE_DIR), UUID.randomUUID(), safeName);
        try {
            Path target = Paths.get(uploadDir).resolve(relativePath);
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            log.error("附件存储失败 path={}", relativePath, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件存储失败");
        }
        return new AttachmentVO(relativePath, original, file.getSize());
    }

    @Override
    @Transactional
    public ImportResult importBudget(Long projectGroupId, String name, String sourceDocPath, InputStream excelStream) {
        if (projectGroupMapper.selectById(projectGroupId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目组不存在");
        }
        SubjectResolver resolver = SubjectResolver.from(
                subjectMapper.selectList(new LambdaQueryWrapper<BudgetSubject>()));
        BudgetExcelListener listener = new BudgetExcelListener(resolver);
        try {
            FastExcel.read(excelStream).head(BudgetRow.class).registerReadListener(listener).sheet().doRead();
        } catch (RuntimeException e) {
            log.warn("预算模板解析失败", e);
            return ImportResult.failed(ErrorCode.TEMPLATE_INVALID.code(),
                    List.of(new ErrorRow(0, null, null, "文件格式非法或无法解析（需 .xlsx）")));
        }

        if (!listener.isHeaderValid()) {
            return ImportResult.failed(ErrorCode.TEMPLATE_INVALID.code(),
                    List.of(new ErrorRow(0, null, null, "缺少必填列：" + String.join("、", listener.getMissingHeaders()))));
        }
        if (!listener.getErrorRows().isEmpty()) {
            int code = listener.hasNonLeafError()
                    ? ErrorCode.AMOUNT_NOT_LEAF.code()
                    : ErrorCode.TEMPLATE_INVALID.code();
            return ImportResult.failed(code, listener.getErrorRows());
        }
        List<BudgetItem> items = listener.getValidItems();
        if (items.isEmpty()) {
            return ImportResult.failed(ErrorCode.TEMPLATE_INVALID.code(),
                    List.of(new ErrorRow(0, null, null, "模板无有效明细行")));
        }

        Budget budget = new Budget();
        budget.setProjectGroupId(projectGroupId);
        budget.setName(name);
        budget.setSourceDocPath(sourceDocPath);
        budget.setVersion(1);
        budget.setStatus("draft");
        budgetMapper.insert(budget);

        for (BudgetItem item : items) {
            item.setBudgetId(budget.getId());
        }
        budgetItemMapper.insertBatch(items);
        return ImportResult.ok(budget.getId(), items.size());
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot < 0 || dot == fileName.length() - 1) ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
