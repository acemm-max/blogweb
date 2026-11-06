package com.ziv.user.security;

import org.springframework.beans.factory.annotation.Autowired;
import com.ziv.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

@Component
public class UserSessionManager {
    @Autowired
    private SecurityContextRepository contexts;
    @Autowired
    private CsrfTokenRepository csrf;


    public void create(UserResponse user, HttpServletRequest request, HttpServletResponse response) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
        new CsrfAuthenticationStrategy(csrf).onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }

    public void delete(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        csrf.saveToken(null, request, response);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
}
