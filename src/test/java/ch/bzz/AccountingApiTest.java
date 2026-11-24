package ch.bzz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the API against an in-memory H2 database (no PostgreSQL needed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-secret-that-is-long-enough-for-hs256-0123456789"
})
class AccountingApiTest {

    private static final String LOGIN = "{\"project_name\":\"Testverein\",\"password\":\"Testpasswort\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void fullFlow() throws Exception {
        // register + duplicate + login
        mvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isCreated());
        mvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isConflict());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"project_name\":\"Testverein\",\"password\":\"falsch123\"}"))
                .andExpect(status().isUnauthorized());

        String body = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(body);
        String auth = "Bearer " + json.get("access_token").asText();

        // no token -> 401
        mvc.perform(get("/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/accounts").header("Authorization", "Bearer kaputt")).andExpect(status().isUnauthorized());

        // Testfall 1: account CRUD
        putAccounts(auth, "[{\"number\":9000,\"name\":\"Eröffnungsbilanz\"}]");
        mvc.perform(get("/accounts").header("Authorization", auth))
                .andExpect(jsonPath("$[0].number").value(9000))
                .andExpect(jsonPath("$[0].name").value("Eröffnungsbilanz"));
        putAccounts(auth, "[{\"number\":9000,\"name\":\"Eröffnungsbilanzkonto\"}]");
        mvc.perform(get("/accounts").header("Authorization", auth))
                .andExpect(jsonPath("$[0].name").value("Eröffnungsbilanzkonto"));
        putAccounts(auth, "[{\"number\":9000,\"name\":null}]");
        mvc.perform(get("/accounts").header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(0));

        // bookings: create, update, delete
        putAccounts(auth, "[{\"number\":1000,\"name\":\"Kasse\"},{\"number\":1020,\"name\":\"Bank\"}]");
        putBookings(auth, "[{\"id\":0,\"date\":\"2025-03-15\",\"text\":\"Bargeld abgehoben\","
                + "\"debit\":1000,\"credit\":1020,\"amount\":200.0}]");
        mvc.perform(get("/bookings").header("Authorization", auth))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].debit").value(1000))
                .andExpect(jsonPath("$[0].amount").value(200.0));

        // account used in a booking cannot be deleted
        mvc.perform(put("/accounts").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accounts\":[{\"number\":1000,\"name\":null}]}"))
                .andExpect(status().isConflict());

        putBookings(auth, "[{\"id\":1,\"date\":\"2025-03-16\",\"text\":\"Korrigiert\","
                + "\"debit\":1000,\"credit\":1020,\"amount\":250.5}]");
        mvc.perform(get("/bookings").header("Authorization", auth))
                .andExpect(jsonPath("$[0].text").value("Korrigiert"))
                .andExpect(jsonPath("$[0].amount").value(250.5));

        putBookings(auth, "[{\"id\":1,\"date\":null,\"text\":null,\"debit\":null,\"credit\":null,\"amount\":null}]");
        String bookings = mvc.perform(get("/bookings").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, mapper.readTree(bookings).size());
    }

    @Test
    void otherProjectCannotSeeData() throws Exception {
        String a = registerAndLogin("VereinA");
        String b = registerAndLogin("VereinB");
        putAccounts(a, "[{\"number\":1000,\"name\":\"Kasse A\"}]");
        mvc.perform(get("/accounts").header("Authorization", b))
                .andExpect(jsonPath("$.length()").value(0));
        // B cannot book on A's account
        mvc.perform(put("/bookings").header("Authorization", b).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"id\":0,\"date\":\"2025-01-01\",\"text\":\"x\","
                                + "\"debit\":1000,\"credit\":1000,\"amount\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"project_name\":\"Kurz\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    private String registerAndLogin(String name) throws Exception {
        String login = "{\"project_name\":\"" + name + "\",\"password\":\"Passwort123\"}";
        mvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("access_token").asText();
    }

    private void putAccounts(String auth, String accounts) throws Exception {
        mvc.perform(put("/accounts").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accounts\":" + accounts + "}"))
                .andExpect(status().isOk());
    }

    private void putBookings(String auth, String entries) throws Exception {
        mvc.perform(put("/bookings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":" + entries + "}"))
                .andExpect(status().isOk());
    }
}
