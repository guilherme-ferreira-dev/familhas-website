package br.com.familhas.admin.payment;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.common.PageResponse;
import jakarta.validation.Valid;

/** Planilha de pagamentos (docs/api/admin.yaml). */
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final AdminPaymentService paymentService;

    public AdminPaymentController(AdminPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public PageResponse<AdminPaymentResponse> findAll(@Valid @ModelAttribute AdminPaymentFilter filter,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public AdminPaymentResponse findById(@PathVariable UUID id) {
        return paymentService.findById(id);
    }
}
