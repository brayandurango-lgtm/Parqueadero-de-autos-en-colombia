package com.autoscolombia.presentation.config;

import com.autoscolombia.domain.service.AuthService;
import com.autoscolombia.infrastructure.exception.AuthenticationException;
import com.autoscolombia.util.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock private AuthService authService;
    @Mock private JwtUtil jwtUtil;
    @Mock private FilterChain filterChain;
    @InjectMocks private JwtAuthenticationFilter filter;

    @Test
    void sinCabeceraAuthorizationContinuaCadenaSinAutenticar() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/registros/activos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authService, jwtUtil);
    }

    @Test
    void tokenValidoPueblaSecurityContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/registros/activos");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserDetails principal = User.withUsername("op@example.com").password("hash").authorities("ROLE_OPERADOR").build();
        when(jwtUtil.extractUsername("valid-token")).thenReturn("op@example.com");
        when(authService.validarToken("valid-token")).thenReturn(principal);
        when(jwtUtil.validateToken("valid-token", principal)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals("op@example.com", org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void tokenInvalidoNoPropagaExcepcionYContinuaCadena() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/registros/activos");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.extractUsername("invalid-token")).thenReturn("op@example.com");
        when(authService.validarToken("invalid-token")).thenThrow(new AuthenticationException("invalid"));

        assertDoesNotThrow(() -> filter.doFilterInternal(request, response, filterChain));
        verify(filterChain).doFilter(request, response);
        assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }
}
