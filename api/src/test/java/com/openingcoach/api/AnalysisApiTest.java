package com.openingcoach.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests with an uploaded PGN (no network). Runs against the real reference
 * index in ../data if present, otherwise against an empty reference book.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisApiTest {

    private static final String PGN = """
            [White "riya"]
            [Black "a"]
            [Result "0-1"]

            1. e4 d5 2. exd5 Qxd5 3. Nc3 Qa5 4. Bc4 0-1

            [White "riya"]
            [Black "b"]
            [Result "0-1"]

            1. e4 d5 2. exd5 Qxd5 3. Nc3 Qa5 4. Bc4 0-1

            [White "riya"]
            [Black "c"]
            [Result "0-1"]

            1. e4 d5 2. exd5 Qxd5 3. Nc3 Qa5 4. Bc4 0-1

            [White "riya"]
            [Black "d"]
            [Result "1-0"]

            1. e4 e5 2. Nf3 Nc6 3. Bc4 1-0

            [White "someone"]
            [Black "riya"]
            [Result "1-0"]

            1. d4 d5 1-0
            """;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String create() throws Exception {
        String body = json.writeValueAsString(Map.of("source", "pgn", "username", "Riya", "colour", "white", "pgnText", PGN));
        String res = mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.games").value(4))
                .andExpect(jsonPath("$.summary.importReport.otherPlayerOrColour").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(res);
        return node.get("id").asText();
    }

    @Test
    void fullWorkflowWorks() throws Exception {
        String id = create();
        mvc.perform(get("/api/analyses/" + id + "/tree").param("moves", "e4,d5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.yourGames").value(3))
                .andExpect(jsonPath("$.result.fen").exists());
        mvc.perform(get("/api/analyses/" + id + "/weak-lines").param("k", "3").param("minGames", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm.name").exists());
        mvc.perform(get("/api/analyses/" + id + "/out-of-book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.gamesAnalysed").value(4));
        mvc.perform(post("/api/analyses/" + id + "/study-plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetMinutes\": 30, \"minGames\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.mode").value("budget"))
                .andExpect(jsonPath("$.algorithm.approximate").value(true));
        mvc.perform(get("/api/reference")).andExpect(status().isOk()).andExpect(jsonPath("$.available").exists());
    }

    @Test
    void invalidInputsGiveProblemDetails() throws Exception {
        mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"pgn\",\"username\":\"x\",\"colour\":\"red\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("colour")));
        mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"pgn\",\"username\":\"riya\",\"colour\":\"white\",\"pgnText\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/analyses/missing/tree")).andExpect(status().isNotFound());

        String id = create();
        mvc.perform(get("/api/analyses/" + id + "/weak-lines").param("k", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analyses/" + id + "/tree").param("moves", "e4,e4")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/analyses/" + id + "/study-plan").contentType(MediaType.APPLICATION_JSON)
                .content("{\"budgetMinutes\": -1}")).andExpect(status().isBadRequest());
    }
}
