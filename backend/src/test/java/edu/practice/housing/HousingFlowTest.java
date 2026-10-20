package edu.practice.housing;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HousingFlowTest {
    @Autowired MockMvc mvc;

    private MockHttpSession login(String username, String password) throws Exception {
        HttpSession session = mvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession();
        return (MockHttpSession) session;
    }

    @Test
    void studentRepairAndAdminWorkflow() throws Exception {
        MockHttpSession student = login("2026001", "Student123!");
        mvc.perform(get("/api/students").session(student)).andExpect(status().isForbidden());
        mvc.perform(post("/api/repairs").session(student).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"水龙头漏水\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(get("/api/repairs").session(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("水龙头漏水"));

        MockHttpSession admin = login("admin", "Admin123!");
        mvc.perform(get("/api/students").session(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/rooms").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"building\":\"东区 2 栋\",\"number\":\"201\",\"capacity\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.number").value("201"));
    }

    @Test
    void anonymousRequestNeedsLogin() throws Exception {
        mvc.perform(get("/api/notices")).andExpect(status().isUnauthorized());
    }
}
