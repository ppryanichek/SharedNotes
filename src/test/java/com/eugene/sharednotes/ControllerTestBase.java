package com.eugene.sharednotes;

import com.eugene.sharednotes.auth.jwt.JwtService;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.security.JwtPrincipal;
import com.eugene.sharednotes.security.SecurityConfig;
import com.eugene.sharednotes.security.SecurityResponseWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@WebMvcTest
@Import({SecurityConfig.class,
        SecurityResponseWriter.class})
public abstract class ControllerTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected JwtService jwtService;

    protected static RequestPostProcessor authAs(Long userId, Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        ));
    }
}
