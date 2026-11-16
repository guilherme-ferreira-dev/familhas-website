package br.com.familhas.crm.backup;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Formato do backup lógico do CRM (versão 1). Um arquivo JSON com as tabelas de negócio:
 * voos de clientes (com passageiros), grupos e histórico de pesquisa de voos, vouchers e roteiros.
 *
 * Fora do backup (JSON e ADR 0017): documentos e mídias (arquivos), posts do Instagram (dependem das mídias), logo,
 * equipe (Keycloak) e auditoria. Referências a arquivos (anexos de voo, origem de voucher, fotos de roteiro) não vão.
 * {@code sha256} cobre {@code tables}: arquivo alterado ou corrompido é recusado na restauração.
 */
public record BackupFile(String format, int version, Instant createdAt, String createdBy, Tables tables,
        String sha256) {

    public static final String FORMAT = "voe-familhas-crm-backup";
    public static final int VERSION = 1;

    public record Tables(List<ClientFlight> clientFlights, List<FlightSearchGroup> flightSearchGroups,
            List<FlightSearch> flightSearches, List<Voucher> vouchers, List<Itinerary> itineraries) {

        public int count() {
            return size(clientFlights) + size(flightSearchGroups) + size(flightSearches) + size(vouchers)
                    + size(itineraries);
        }

        private static int size(List<?> list) {
            return list == null ? 0 : list.size();
        }
    }

    public record ClientFlight(UUID id, String locator, String airline, String origin, String destination,
            LocalDateTime outboundAt, String outboundFlightNumber, LocalDateTime returnAt, String returnFlightNumber,
            String notes, Instant archivedAt, String createdBy, long version, Instant createdAt, Instant updatedAt,
            List<Traveler> travelers) {
    }

    public record Traveler(UUID id, int sortOrder, String fullName, String nameSearch, String cpf, String phone) {
    }

    public record FlightSearchGroup(UUID id, String name, String destinations, Instant createdAt, Instant updatedAt) {
    }

    public record FlightSearch(UUID id, String paramsHash, String params, String summary, String runBy,
            Instant lastRunAt, Instant createdAt, Instant updatedAt) {
    }

    public record Voucher(UUID id, String number, String type, String status, String clientName, String clientCpf,
            String clientPhone, String notes, String details, LocalDate serviceDate, String issuedBy,
            Instant issuedAt, Instant cancelledAt, long version, Instant createdAt, Instant updatedAt) {
    }

    public record Itinerary(UUID id, String destination, int days, String clientName, String clientPhone,
            String travelPeriod, String priceText, String paymentTerms, String observations, String sections,
            String internalAlerts, String createdBy, long version, Instant createdAt, Instant updatedAt) {
    }
}
