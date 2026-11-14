package br.com.familhas.admin.payment.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import br.com.familhas.admin.payment.AdminPaymentResponse;
import br.com.familhas.admin.payment.export.PaymentExportColumns.Column;

/**
 * CSV (RFC 4180): UTF-8 com BOM (o Excel reconhece os acentos), vírgula, CRLF, decimal com ponto.
 * Célula de texto que começa com =, +, -, @, tab ou CR ganha um apóstrofo na frente: abrir o arquivo numa
 * planilha nunca executa fórmula vinda de um nome de cliente (CSV injection).
 */
class CsvPaymentExportWriter implements PaymentExportWriter {

    private static final String BOM = "﻿";

    private final Writer writer;

    CsvPaymentExportWriter(OutputStream out) throws IOException {
        this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.write(BOM);
        writeLine(PaymentExportColumns.COLUMNS.stream().map(Column::header).toList());
    }

    @Override
    public void writeRow(AdminPaymentResponse payment) throws IOException {
        writeLine(PaymentExportColumns.COLUMNS.stream().map(column -> {
            String text = PaymentExportColumns.text(column.value().apply(payment));
            return column.kind() == PaymentExportColumns.Kind.TEXT ? neutralizeFormula(text) : text;
        }).toList());
    }

    @Override
    public void close() throws IOException {
        writer.flush();
    }

    private void writeLine(List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(quote(cells.get(i)));
        }
        writer.write("\r\n");
    }

    static String neutralizeFormula(String text) {
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return text;
    }

    static String quote(String text) {
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
