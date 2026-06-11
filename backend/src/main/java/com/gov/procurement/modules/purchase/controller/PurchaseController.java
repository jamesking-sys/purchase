package com.gov.procurement.modules.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.purchase.dto.CreatePurchaseOrderReq;
import com.gov.procurement.modules.purchase.dto.DeliveryNoteVO;
import com.gov.procurement.modules.purchase.dto.DownloadFile;
import com.gov.procurement.modules.purchase.dto.PurchaseOrderDetailVO;
import com.gov.procurement.modules.purchase.dto.PurchaseOrderVO;
import com.gov.procurement.modules.purchase.service.DeliveryNoteService;
import com.gov.procurement.modules.purchase.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 采购执行接口（U8）：创建采购单含明细 / 查询详情 + 列表 / 上传到货单（多文件）/ 查询与下载到货单。均需 editor 角色。
 */
@RestController
@RequestMapping("/api/purchase")
public class PurchaseController {

    private final PurchaseOrderService purchaseOrderService;
    private final DeliveryNoteService deliveryNoteService;

    public PurchaseController(PurchaseOrderService purchaseOrderService, DeliveryNoteService deliveryNoteService) {
        this.purchaseOrderService = purchaseOrderService;
        this.deliveryNoteService = deliveryNoteService;
    }

    @SaCheckRole("editor")
    @PostMapping("/orders")
    public Result<PurchaseOrderVO> create(@Valid @RequestBody CreatePurchaseOrderReq req) {
        return Result.ok(purchaseOrderService.createOrder(req));
    }

    @SaCheckRole("editor")
    @GetMapping("/orders/{id}")
    public Result<PurchaseOrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(purchaseOrderService.getDetail(id));
    }

    @SaCheckRole("editor")
    @GetMapping("/orders")
    public Result<Page<PurchaseOrderVO>> list(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long projectGroupId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(purchaseOrderService.listOrders(status, projectGroupId, page, size));
    }

    @SaCheckRole("editor")
    @PostMapping("/orders/{id}/delivery-notes")
    public Result<List<DeliveryNoteVO>> upload(@PathVariable Long id,
                                               @RequestParam(value = "files", required = false)
                                               List<MultipartFile> files) {
        return Result.ok(deliveryNoteService.upload(id, files));
    }

    @SaCheckRole("editor")
    @GetMapping("/orders/{id}/delivery-notes")
    public Result<List<DeliveryNoteVO>> listNotes(@PathVariable Long id) {
        return Result.ok(deliveryNoteService.listByOrder(id));
    }

    @SaCheckRole("editor")
    @GetMapping("/delivery-notes/{noteId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long noteId) {
        DownloadFile file = deliveryNoteService.download(noteId);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.fileName() + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file.resource());
    }
}
