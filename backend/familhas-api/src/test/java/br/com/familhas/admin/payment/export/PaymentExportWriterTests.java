package br.com.familhas.admin.payment.export;

import static br.com.familhas.admin.payment.PaymentFixtures.payment;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class PaymentExportWriterTests {

    @Test
    void csvHasBomHeaderAndSaoPauloTimes() throws Exception {
        String csv = csv(payment("Ana Souza", "7830.16"));

        assertThat(csv).startsWith("﻿Pagamento,Reserva,Cliente,E-mail,Metodo,Status,Parcelas,Valor,");
        String row = csv.split("\r\n")[1];
        assertThat(row).contains(",Ana Souza,ana@example.com,CREDIT_CARD,SUCCEEDED,1,7830.16,0.00,BRL,")
                .contains("2027-01-10 12:30:00"); // 15:30 UTC = 12:30 em São Paulo
    }

    @Test
    void csvQuotesSeparatorsAndNeutralizesFormulas() throws Exception {
        String row = csv(payment("=HYPERLINK(\"http://x\",\"a, b\")", "10.00")).split("\r\n")[1];

        assertThat(row).contains(",\"'=HYPERLINK(\"\"http://x\"\",\"\"a, b\"\")\",");
        assertThat(CsvPaymentExportWriter.neutralizeFormula("-10")).isEqualTo("'-10");
        assertThat(CsvPaymentExportWriter.neutralizeFormula("@cmd")).isEqualTo("'@cmd");
        assertThat(CsvPaymentExportWriter.neutralizeFormula("Ana")).isEqualTo("Ana");
    }

    @Test
    void xlsxHasRealNumbersAndDatesAndNoFormulas() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PaymentExportWriter writer = PaymentExportWriter.open(PaymentExportFormat.XLSX, out)) {
            for (int i = 0; i < 250; i++) { // mais que a janela do SXSSF (100)
                writer.writeRow(payment(i == 0 ? "=1+1" : "Cliente " + i, "7830.16"));
            }
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheet("Pagamentos");
            assertThat(sheet.getLastRowNum()).isEqualTo(250);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Pagamento");
            Row first = sheet.getRow(1);
            assertThat(first.getCell(2).getCellType()).isEqualTo(CellType.STRING);
            assertThat(first.getCell(2).getStringCellValue()).isEqualTo("=1+1");
            assertThat(first.getCell(7).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(first.getCell(7).getNumericCellValue()).isEqualTo(7830.16);
            assertThat(first.getCell(10).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2027, 1, 10, 12, 30));
            assertThat(first.getCell(11)).as("falha vazia fica sem célula").isNull();
        }
    }

    @Test
    void formatParsesTheQueryValue() {
        assertThat(PaymentExportFormat.from("XLSX")).isEqualTo(PaymentExportFormat.XLSX);
        assertThat(PaymentExportFormat.from("csv")).isEqualTo(PaymentExportFormat.CSV);
    }

    private static String csv(br.com.familhas.admin.payment.AdminPaymentResponse payment) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PaymentExportWriter writer = PaymentExportWriter.open(PaymentExportFormat.CSV, out)) {
            writer.writeRow(payment);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
