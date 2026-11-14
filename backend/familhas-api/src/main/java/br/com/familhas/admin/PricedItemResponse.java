package br.com.familhas.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.familhas.booking.BookingItemEntity;
import br.com.familhas.pricing.PriceItemType;
import br.com.familhas.quote.QuoteItemEntity;

/** Item do snapshot visto pelo Admin: custo unitário e subtotal sem margem. */
public record PricedItemResponse(PriceItemType type, String description, LocalDate startDate, LocalDate endDate,
        int quantity, BigDecimal unitPrice, String currency, BigDecimal subtotal, String provider) {

    public static PricedItemResponse of(BookingItemEntity i) {
        return new PricedItemResponse(i.getItemType(), i.getDescription(), i.getStartDate(), i.getEndDate(),
                i.getQuantity(), i.getUnitPrice(), i.getCurrency(), i.getSubtotal(), i.getProvider());
    }

    public static PricedItemResponse of(QuoteItemEntity i) {
        return new PricedItemResponse(i.getItemType(), i.getDescription(), i.getStartDate(), i.getEndDate(),
                i.getQuantity(), i.getUnitPrice(), i.getCurrency(), i.getSubtotal(), i.getProvider());
    }
}
