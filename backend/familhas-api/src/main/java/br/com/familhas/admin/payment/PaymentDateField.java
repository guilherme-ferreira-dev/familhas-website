package br.com.familhas.admin.payment;

/** Data usada no filtro de período da planilha. */
public enum PaymentDateField {
    CREATED("createdAt"),
    PAID("paidAt");

    private final String attribute;

    PaymentDateField(String attribute) {
        this.attribute = attribute;
    }

    String attribute() {
        return attribute;
    }
}
