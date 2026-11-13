package br.com.familhas.favorite;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.api.BusinessException;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.client.ClientRepository;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.travelPackage.TravelPackageEntity;
import br.com.familhas.travelPackage.TravelPackageRepository;
import br.com.familhas.travelPackage.dto.PackageSummaryResponse;

/**
 * Favoritos do cliente autenticado (ADR 0010, D6), na mesma tabela client_favorites do CRUD admin.
 * PUT e DELETE são idempotentes; só pacote ativo pode entrar nos favoritos.
 */
@Service
@Transactional
public class FavoriteService {

    private final ClientRepository clientRepository;
    private final TravelPackageRepository travelPackageRepository;

    public FavoriteService(ClientRepository clientRepository, TravelPackageRepository travelPackageRepository) {
        this.clientRepository = clientRepository;
        this.travelPackageRepository = travelPackageRepository;
    }

    /** Em ordem de título. Pacotes que saíram do catálogo continuam na lista, com active=false. */
    @Transactional(readOnly = true)
    public List<PackageSummaryResponse> findForClient(UUID clientId) {
        return getClient(clientId).getFavorites().stream()
                .sorted(Comparator.comparing(TravelPackageEntity::getTitle, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(TravelPackageEntity::getId))
                .map(PackageSummaryResponse::of)
                .toList();
    }

    public void add(UUID clientId, UUID travelPackageId) {
        ClientEntity client = getClient(clientId);
        if (isFavorite(client, travelPackageId)) {
            return;
        }
        TravelPackageEntity travelPackage = travelPackageRepository.findById(travelPackageId)
                .filter(p -> p.getStatus() == EntityStatus.ACTIVE)
                .orElseThrow(() -> packageNotFound(travelPackageId));
        client.getFavorites().add(travelPackage);
    }

    public void remove(UUID clientId, UUID travelPackageId) {
        getClient(clientId).getFavorites().removeIf(p -> p.getId().equals(travelPackageId));
    }

    public static BusinessException packageNotFound(UUID travelPackageId) {
        return new BusinessException(HttpStatus.NOT_FOUND, "PACKAGE_NOT_FOUND",
                "Pacote nao encontrado: " + travelPackageId);
    }

    private static boolean isFavorite(ClientEntity client, UUID travelPackageId) {
        return client.getFavorites().stream().anyMatch(p -> p.getId().equals(travelPackageId));
    }

    /** O cliente vem do token e acabou de ser sincronizado: não existir é erro interno, não 404 do recurso. */
    private ClientEntity getClient(UUID clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }
}
