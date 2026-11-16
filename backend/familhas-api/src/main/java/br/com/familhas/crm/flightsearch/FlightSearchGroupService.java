package br.com.familhas.crm.flightsearch;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.api.BusinessException;

/** Grupos de destinos da agência (até 7 destinos cada). */
@Service
public class FlightSearchGroupService {

    private final FlightSearchGroupRepository repository;

    public FlightSearchGroupService(FlightSearchGroupRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<FlightSearchGroupResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(FlightSearchGroupResponse::of).toList();
    }

    @Transactional
    public FlightSearchGroupResponse create(FlightSearchGroupRequest request) {
        if (repository.existsByNameIgnoreCase(request.name().strip())) {
            throw duplicated();
        }
        FlightSearchGroupEntity group = new FlightSearchGroupEntity();
        apply(group, request);
        return FlightSearchGroupResponse.of(repository.save(group));
    }

    @Transactional
    public FlightSearchGroupResponse update(UUID id, FlightSearchGroupRequest request) {
        FlightSearchGroupEntity group = find(id);
        if (repository.existsByNameIgnoreCaseAndIdNot(request.name().strip(), id)) {
            throw duplicated();
        }
        apply(group, request);
        return FlightSearchGroupResponse.of(repository.save(group));
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(find(id));
    }

    private static void apply(FlightSearchGroupEntity group, FlightSearchGroupRequest request) {
        List<String> destinations = request.destinations().stream().distinct().toList();
        group.setName(request.name().strip());
        group.setDestinations(String.join(",", destinations));
    }

    private FlightSearchGroupEntity find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                "GROUP_NOT_FOUND", "Grupo nao encontrado."));
    }

    private static BusinessException duplicated() {
        return new BusinessException(HttpStatus.CONFLICT, "GROUP_NAME_TAKEN", "Ja existe um grupo com esse nome.");
    }
}
