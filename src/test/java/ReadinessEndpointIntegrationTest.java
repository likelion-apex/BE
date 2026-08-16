import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import global.health.ReadinessController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ApexBeApplication.class)
@AutoConfigureMockMvc
class ReadinessEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsUnauthenticatedChecksAfterApplicationStartup() throws Exception {
        mockMvc.perform(get(ReadinessController.PATH))
                .andExpect(status().isNoContent());
    }
}
