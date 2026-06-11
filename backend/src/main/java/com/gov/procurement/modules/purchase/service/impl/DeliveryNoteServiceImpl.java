package com.gov.procurement.modules.purchase.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.common.storage.FileStorage;
import com.gov.procurement.modules.purchase.domain.DeliveryNote;
import com.gov.procurement.modules.purchase.domain.PurchaseOrder;
import com.gov.procurement.modules.purchase.dto.DeliveryNoteRow;
import com.gov.procurement.modules.purchase.dto.DeliveryNoteVO;
import com.gov.procurement.modules.purchase.dto.DownloadFile;
import com.gov.procurement.modules.purchase.mapper.DeliveryNoteMapper;
import com.gov.procurement.modules.purchase.mapper.PurchaseOrderMapper;
import com.gov.procurement.modules.purchase.service.DeliveryNoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.List;

import static com.gov.procurement.modules.purchase.constant.PurchaseConst.ALLOWED_EXTENSIONS;
import static com.gov.procurement.modules.purchase.constant.PurchaseConst.DELIVERY_SUBDIR_TEMPLATE;
import static com.gov.procurement.modules.purchase.constant.PurchaseConst.DOWNLOAD_URL_TEMPLATE;
import static com.gov.procurement.modules.purchase.constant.PurchaseConst.MAX_FILE_BYTES;
import static com.gov.procurement.modules.purchase.constant.PurchaseConst.ST_EXECUTING;

/**
 * 到货单服务实现。上传整体事务（任一文件校验/落库失败回滚）；下载经 FileStorage 抽象读取。
 */
@Service
public class DeliveryNoteServiceImpl implements DeliveryNoteService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryNoteServiceImpl.class);

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final DeliveryNoteMapper deliveryNoteMapper;
    private final FileStorage fileStorage;

    public DeliveryNoteServiceImpl(PurchaseOrderMapper purchaseOrderMapper, DeliveryNoteMapper deliveryNoteMapper,
                                   FileStorage fileStorage) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.deliveryNoteMapper = deliveryNoteMapper;
        this.fileStorage = fileStorage;
    }

    @Override
    @Transactional
    public List<DeliveryNoteVO> upload(Long orderId, List<MultipartFile> files) {
        long userId = StpUtil.getLoginIdAsLong();
        PurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购单不存在");
        }
        if (!ST_EXECUTING.equals(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "采购单非执行中，不可上传到货单");
        }
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未选择到货单文件");
        }

        String subDir = String.format(DELIVERY_SUBDIR_TEMPLATE, orderId);
        for (MultipartFile file : files) {
            validateFile(file);
            String path = fileStorage.store(file, subDir);
            DeliveryNote note = new DeliveryNote();
            note.setPurchaseOrderId(orderId);
            note.setFilePath(path);
            note.setUploadedBy(userId);
            deliveryNoteMapper.insert(note);
        }
        log.info("上传到货单 orderId={}, count={}, userId={}", orderId, files.size(), userId);
        return listByOrder(orderId);
    }

    @Override
    public List<DeliveryNoteVO> listByOrder(Long orderId) {
        return deliveryNoteMapper.selectRowsByOrder(orderId).stream()
                .map(DeliveryNoteServiceImpl::toVO)
                .toList();
    }

    @Override
    public DownloadFile download(Long noteId) {
        DeliveryNote note = deliveryNoteMapper.selectById(noteId);
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "到货单不存在");
        }
        Resource resource = fileStorage.load(note.getFilePath());
        return new DownloadFile(resource, fileNameOf(note.getFilePath()));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存在空文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件大小超过上限（20MB）");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的文件类型：" + ext);
        }
    }

    private static DeliveryNoteVO toVO(DeliveryNoteRow row) {
        return new DeliveryNoteVO(
                row.getId(),
                fileNameOf(row.getFilePath()),
                row.getUploadedBy(),
                row.getUploadedByName(),
                row.getCreatedAt(),
                String.format(DOWNLOAD_URL_TEMPLATE, row.getId()));
    }

    private static String fileNameOf(String filePath) {
        return filePath == null ? "" : Paths.get(filePath).getFileName().toString();
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return (dot < 0 || dot == fileName.length() - 1) ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
