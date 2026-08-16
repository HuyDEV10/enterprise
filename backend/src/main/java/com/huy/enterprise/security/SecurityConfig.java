package com.huy.enterprise.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/users/**", "/api/roles/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/risk-monitoring/run")
                        .hasAnyRole("ADMIN", "MANAGER", "ANALYST")
                        .requestMatchers(HttpMethod.GET, "/api/risk-monitoring/**")
                        .hasAnyRole("ADMIN", "MANAGER", "ANALYST", "STAFF")
                        .requestMatchers("/api/risk-monitoring/**")
                        .hasAnyRole("ADMIN", "MANAGER", "ANALYST")
                        .requestMatchers("/api/risk-events/**").hasAnyRole("ADMIN", "MANAGER", "ANALYST")
                        .requestMatchers(HttpMethod.GET, "/api/alerts/**")
                        .hasAnyRole("ADMIN", "MANAGER", "ANALYST")
                        .requestMatchers(HttpMethod.POST, "/api/alerts/**")
                        .hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/alerts/**")
                        .hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/alerts/**").hasAnyRole("ADMIN", "MANAGER", "ANALYST")
                        .requestMatchers(HttpMethod.GET, "/api/companies/**", "/api/departments/**", "/api/employees/**")
                        .hasAnyRole("ADMIN", "MANAGER", "ANALYST")
                        .requestMatchers("/api/companies/**", "/api/departments/**", "/api/employees/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyRole("ADMIN", "MANAGER", "STAFF", "ANALYST")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "MANAGER", "STAFF")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
