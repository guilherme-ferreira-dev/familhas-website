package br.com.familhas.travelPackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.familhas.common.PageResponse;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.travelPackage.dto.TravelPackageFilter;
import br.com.familhas.travelPackage.dto.TravelPackageResponse;

@WebMvcTest(TravelPackageController.class)
@Import(SecurityConfig.class)
class TravelPackageControllerTests {

    private static final UUID ID = UUID.fromString("bac00000-0000-4000-8000-000000000001");
    private static final UUID DESTINATION = UUID.fromString("c1770000-0000-4000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelPackageService travelPackageService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void findAllBindsFiltersAndPaginationAndReturnsPageEnvelope() throws Exception {
        when(travelPackageService.findAll(any(), any())).thenReturn(new PageResponse<>(
                List.of(response()), 1, 5, 6, 2));

        mockMvc.perform(get("/travel-packages")
                        .param("destinationCityId", DESTINATION.toString())
                        .param("maxPrice", "3000")
                        .param("q", "resort")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.content[0].currency").value("BRL"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));

        ArgumentCaptor<TravelPackageFilter> filter = ArgumentCaptor.forClass(TravelPackageFilter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(travelPackageService).findAll(filter.capture(), pageable.capture());
        assertThat(filter.getValue().destinationCityId()).isEqualTo(DESTINATION);
        assertThat(filter.getValue().maxPrice()).isEqualByComparingTo("3000");
        assertThat(filter.getValue().q()).isEqualTo("resort");
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void findAllCapsPageSizeAtConfiguredMaximum() throws Exception {
        when(travelPackageService.findAll(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        mockMvc.perform(get("/travel-packages").param("size", "500")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(travelPackageService).findAll(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void findAllRejectsNegativePriceFilter() throws Exception {
        mockMvc.perform(get("/travel-packages").param("maxPrice", "-1"))
                .andExpect(status().isBadRequest());

        verify(travelPackageService, never()).findAll(any(), any());
    }

    @Test
    void deleteWithoutTokenReturns401() throws Exception {
        mockMvc.perform(delete("/travel-packages/{id}", ID)).andExpect(status().isUnauthorized());

        verify(travelPackageService, never()).delete(any());
    }

    @Test
    void deleteAsUserReturns403() throws Exception {
        mockMvc.perform(delete("/travel-packages/{id}", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        verify(travelPackageService, never()).delete(any());
    }

    @Test
    void deleteAsAdminCallsServiceAndReturns204() throws Exception {
        mockMvc.perform(delete("/travel-packages/{id}", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());

        verify(travelPackageService).delete(eq(ID));
    }

    @Test
    void deleteBlockedByRelatedRecordsReturns409WithoutSqlDetails() throws Exception {
        doThrow(new DataIntegrityViolationException("ORA-02292: integrity constraint violated"))
                .when(travelPackageService).delete(ID);

        mockMvc.perform(delete("/travel-packages/{id}", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Operation conflicts with related records"));
    }

    @Test
    void createRejectsComponentWithInvalidQuantity() throws Exception {
        String body = """
                {
                  "title": "Pacote",
                  "price": 3000.00,
                  "status": "ACTIVE",
                  "flightId": "f1170000-0000-4000-8000-000000000001",
                  "categoryId": "ca7e0000-0000-4000-8000-000000000001",
                  "originCityId": "c1770000-0000-4000-8000-000000000001",
                  "destinationCityId": "c1770000-0000-4000-8000-000000000002",
                  "components": [
                    { "type": "HOTEL_STAY", "roomOfferId": "ee2e0000-0000-4000-8000-000000000001", "quantity": 0 }
                  ]
                }
                """;

        mockMvc.perform(post("/travel-packages")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['components[0].quantity']").exists());

        verify(travelPackageService, never()).create(any());
    }

    @Test
    void createRejectsLowercaseCurrency() throws Exception {
        String body = """
                {
                  "title": "Pacote",
                  "price": 3000.00,
                  "currency": "brl",
                  "status": "ACTIVE",
                  "flightId": "f1170000-0000-4000-8000-000000000001",
                  "categoryId": "ca7e0000-0000-4000-8000-000000000001",
                  "originCityId": "c1770000-0000-4000-8000-000000000001",
                  "destinationCityId": "c1770000-0000-4000-8000-000000000002"
                }
                """;

        mockMvc.perform(post("/travel-packages")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    private static TravelPackageResponse response() {
        return new TravelPackageResponse(ID, "Maceio Resort", new BigDecimal("3299.90"), new BigDecimal("2799.90"),
                "BRL", EntityStatus.ACTIVE, null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                DESTINATION, List.of());
    }
}
