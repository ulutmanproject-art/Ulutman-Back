package com.ulutman.security.jwt;

import com.ulutman.security.UserDetailsServiceImpl;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String tokenHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (tokenHeader != null && tokenHeader.startsWith("Bearer ")) {
            String jwt = tokenHeader.substring(7);
            try {
                String userName = jwtUtil.getUserNameFromToken(jwt);
                if (userName != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails user = userDetailsService.loadUserByUsername(userName);
                    Collection<? extends GrantedAuthority> role = user.getAuthorities();
                    if (jwtUtil.tokenIsValidation(jwt, user)) {
                        UsernamePasswordAuthenticationToken token =
                                new UsernamePasswordAuthenticationToken(user, null, role);
                        token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(token);
                    }
                }
            } catch (JwtException | UsernameNotFoundException e) {
                log.warn("Invalid JWT token: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}


