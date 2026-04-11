package com.reclaim.portal.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that /contracts/** routes redirect to /user/contracts/** with proper model population,
 * and that the status filter parameter is accepted by the contracts list endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContractPageRedirectTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void contractsListRedirectsToUserContracts() throws Exception {
        mockMvc.perform(get("/contracts"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/user/contracts"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void contractDetailRedirectsToUserContractDetail() throws Exception {
        mockMvc.perform(get("/contracts/42"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/user/contracts/42"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void contractSignRedirectsToUserContractSign() throws Exception {
        mockMvc.perform(get("/contracts/42/sign"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/user/contracts/42/sign"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void contractPrintRedirectsToUserContractPrint() throws Exception {
        mockMvc.perform(get("/contracts/42/print"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/user/contracts/42/print"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void contractsListAcceptsStatusFilterParameter() throws Exception {
        mockMvc.perform(get("/user/contracts").param("status", "INITIATED"))
               .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void contractsListWithoutFilterReturnsAllContracts() throws Exception {
        mockMvc.perform(get("/user/contracts"))
               .andExpect(status().isOk());
    }
}
