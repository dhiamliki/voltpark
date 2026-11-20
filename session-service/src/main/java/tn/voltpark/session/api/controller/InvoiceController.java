package tn.voltpark.session.api.controller;

import org.springframework.web.bind.annotation.*;
import tn.voltpark.session.api.dto.InvoiceResponse;
import tn.voltpark.session.domain.service.SessionManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final SessionManagementService sessionService;

    public InvoiceController(SessionManagementService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{id}")
    public InvoiceResponse getInvoice(@PathVariable Long id) {
        return sessionService.getInvoice(id);
    }

    @GetMapping
    public List<InvoiceResponse> getInvoices(@RequestParam(required = false) Long driverId) {
        return sessionService.getInvoices(driverId);
    }
}
