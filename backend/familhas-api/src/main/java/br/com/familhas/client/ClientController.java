package br.com.familhas.client;

import br.com.familhas.client.dto.ClientFilter;
import br.com.familhas.client.dto.ClientRequest;
import br.com.familhas.client.dto.ClientResponse;
import br.com.familhas.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    /** Filtros: status, q (nome ou e-mail). Ordenação: name, email, createdAt, updatedAt. */
    @GetMapping
    public PageResponse<ClientResponse> findAll(
            @Valid @ModelAttribute ClientFilter filter,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return clientService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public ClientResponse findById(@PathVariable UUID id) {
        return clientService.findById(id);
    }

    @PostMapping
    public ResponseEntity<ClientResponse> create(@RequestBody @Valid ClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request));
    }

    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable UUID id, @RequestBody @Valid ClientRequest request) {
        return clientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
