package uy.edu.um.luminalabs.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// CU-17: la API publica de punta a punta (seguridad, JSON y errores), sobre H2 con los datos de demo
// (DemoDataInitializer: emprendimientos aprobados con actividades y horarios desde manana)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Sin sesion: la API es publica
    @Test
    void listsApprovedBusinessesWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/v1/businesses"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].identifier").exists())
                .andExpect(jsonPath("$[0].bankAccountNumber").doesNotExist());
    }

    @Test
    void verifiesABusinessByIdentifier() throws Exception {
        mockMvc.perform(get("/api/v1/businesses/verification").param("identifier", "21.999999.0011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier").value("219999990011"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.business.name").value("Costa Este Experiencias"));

        // 3b
        mockMvc.perform(get("/api/v1/businesses/verification").param("identifier", "111111111111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.business").value(nullValue()));
    }

    // Los horarios de demo empiezan manana y se repiten cada 3 dias: en los proximos 3 dias hay alguno en Rocha
    @Test
    void activitiesOfADayInADepartment() throws Exception {
        boolean found = false;
        for (int days = 1; days <= 3 && !found; days++) {
            String body = mockMvc.perform(get("/api/v1/activities/day")
                            .param("date", LocalDate.now().plusDays(days).toString()).param("department", "rocha"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.department.code").value("ROCHA"))
                    .andReturn().getResponse().getContentAsString();
            found = body.contains("\"slots\":[{");
        }
        org.junit.jupiter.api.Assertions.assertTrue(found, "hay actividades con horarios en Rocha en los proximos dias");
    }

    @Test
    void searchesByKeyword() throws Exception {
        mockMvc.perform(get("/api/v1/activities/search").param("q", "kayak"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activity.name", containsStringIgnoringCase("kayak")))
                .andExpect(jsonPath("$[0].activity.url", startsWith("http")));
    }

    // Sin valoraciones en ese mes: lista vacia (3a), no error
    @Test
    void topOfAMonthWithoutRatingsIsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/activities/top").param("department", "SALTO").param("month", "1").param("year", "2020"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(1))
                .andExpect(jsonPath("$.activities", empty()));
    }

    // 2a: errores en JSON, con un mensaje que explica el problema
    @Test
    void invalidParametersAreExplained() throws Exception {
        mockMvc.perform(get("/api/v1/activities/day").param("department", "ROCHA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("date")));
        mockMvc.perform(get("/api/v1/activities/day").param("date", "mañana").param("department", "ROCHA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("AAAA-MM-DD")));
        mockMvc.perform(get("/api/v1/activities/top").param("department", "Narnia"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("TREINTA_Y_TRES")));
        mockMvc.perform(get("/api/v1/activities/search").param("q", "a"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/businesses/verification"))
                .andExpect(status().isBadRequest());
    }

    // RNF-05: la especificacion OpenAPI documenta las 5 operaciones (y solo la API, no el portal)
    @Test
    void apiIsDocumented() throws Exception {
        mockMvc.perform(get("/api/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API de Uruguay Mnatural"))
                .andExpect(jsonPath("$.paths", aMapWithSize(5)))
                .andExpect(jsonPath("$.paths['/api/v1/activities/top']").exists());
    }
}
