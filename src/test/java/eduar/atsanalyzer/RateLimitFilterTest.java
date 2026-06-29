package eduar.atsanalyzer;
import eduar.atsanalyzer.infra.security.RateLimitFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {


    private final RateLimitFilter filter = new RateLimitFilter();
    private final FilterChain filterChain = mock(FilterChain.class);

    @Test
    @DisplayName("requisição dentro do limite → passa (chain.doFilter chamado)")
    void devePermitir_quandoDentroDoLimite() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }


    @Test
    @DisplayName("11ª requisição do mesmo IP → bloqueia com 429")
    void deveBloquear_quandoExcedeLimite() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        // consome as 10 permitidas
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilterInternal(request, resp, filterChain);
        }

        // 11ª requisição — deve bloquear
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(request, blocked, filterChain);

        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains("Limite de requisições excedido"));

        // filterChain chamado exatamente 10 vezes, não 11
        verify(filterChain, times(10)).doFilter(eq(request), any());
    }

    @Test
    @DisplayName("IPs diferentes têm buckets independentes")
    void deveIsolarBucketsPorIp() throws ServletException, IOException {
        // esgota o bucket do IP A
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("10.0.0.1");
            filter.doFilterInternal(req, new MockHttpServletResponse(), filterChain);
        }

        // IP B ainda deve passar
        MockHttpServletRequest reqB = new MockHttpServletRequest();
        reqB.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse respB = new MockHttpServletResponse();

        filter.doFilterInternal(reqB, respB, filterChain);

        assertEquals(200, respB.getStatus());
    }

}
