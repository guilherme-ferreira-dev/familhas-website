package br.com.familhas.admin.payment.export;

import java.io.IOException;
import java.io.OutputStream;

import br.com.familhas.admin.payment.AdminPaymentResponse;

/** Escreve a planilha linha a linha, sem guardar as linhas em memória. */
public interface PaymentExportWriter extends AutoCloseable {

    void writeRow(AdminPaymentResponse payment) throws IOException;

    /** Termina o arquivo (rodapé, compactação) e libera recursos temporários. */
    @Override
    void close() throws IOException;

    static PaymentExportWriter open(PaymentExportFormat format, OutputStream out) throws IOException {
        return switch (format) {
            case CSV -> new CsvPaymentExportWriter(out);
            case XLSX -> new XlsxPaymentExportWriter(out);
        };
    }
}
