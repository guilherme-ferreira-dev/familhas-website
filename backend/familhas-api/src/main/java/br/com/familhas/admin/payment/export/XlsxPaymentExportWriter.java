package br.com.familhas.admin.payment.export;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import br.com.familhas.admin.payment.AdminPaymentResponse;
import br.com.familhas.admin.payment.export.PaymentExportColumns.Column;

/**
 * XLSX em streaming (Apache POI SXSSF): só {@link #WINDOW} linhas ficam em memória; as anteriores vão para um
 * arquivo temporário compactado, apagado no close. Valores como número e data de verdade (filtráveis no Excel),
 * nunca como fórmula.
 */
class XlsxPaymentExportWriter implements PaymentExportWriter {

    static final int WINDOW = 100;

    private final OutputStream out;
    private final SXSSFWorkbook workbook;
    private final SXSSFSheet sheet;
    private final CellStyle money;
    private final CellStyle dateTime;
    private int nextRow;

    XlsxPaymentExportWriter(OutputStream out) {
        this.out = out;
        this.workbook = new SXSSFWorkbook(WINDOW);
        workbook.setCompressTempFiles(true);
        this.sheet = workbook.createSheet("Pagamentos");
        this.money = workbook.createCellStyle();
        money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        this.dateTime = workbook.createCellStyle();
        dateTime.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));

        CellStyle header = workbook.createCellStyle();
        Font bold = workbook.createFont();
        bold.setBold(true);
        header.setFont(bold);
        Row row = sheet.createRow(nextRow++);
        for (int i = 0; i < PaymentExportColumns.COLUMNS.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(PaymentExportColumns.COLUMNS.get(i).header());
            cell.setCellStyle(header);
            sheet.setColumnWidth(i, 20 * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    @Override
    public void writeRow(AdminPaymentResponse payment) {
        Row row = sheet.createRow(nextRow++);
        for (int i = 0; i < PaymentExportColumns.COLUMNS.size(); i++) {
            Column column = PaymentExportColumns.COLUMNS.get(i);
            Object value = column.value().apply(payment);
            if (value == null) {
                continue;
            }
            Cell cell = row.createCell(i);
            switch (column.kind()) {
                case MONEY -> {
                    cell.setCellValue(((BigDecimal) value).doubleValue());
                    cell.setCellStyle(money);
                }
                case INTEGER -> cell.setCellValue(((Number) value).doubleValue());
                case DATE_TIME -> {
                    cell.setCellValue((LocalDateTime) value);
                    cell.setCellStyle(dateTime);
                }
                case TEXT -> cell.setCellValue(value.toString());
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(nextRow - 1, 0), 0,
                    PaymentExportColumns.COLUMNS.size() - 1));
            workbook.write(out);
            out.flush();
        } finally {
            workbook.close();
        }
    }
}
