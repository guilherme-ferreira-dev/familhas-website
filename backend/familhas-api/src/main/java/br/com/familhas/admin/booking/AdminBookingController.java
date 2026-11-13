package br.com.familhas.admin.booking;

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

/** Reservas no Admin (docs/api/admin.yaml). */
@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final AdminBookingService bookingService;

    public AdminBookingController(AdminBookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public PageResponse<AdminBookingSummaryResponse> findAll(@Valid @ModelAttribute AdminBookingFilter filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public AdminBookingDetailResponse findById(@PathVariable UUID id) {
        return bookingService.findById(id);
    }
}
