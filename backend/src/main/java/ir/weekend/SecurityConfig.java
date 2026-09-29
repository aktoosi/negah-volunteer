package ir.weekend;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService users(JdbcTemplate db) {
    return username ->
        db
            .query(
                "select * from app_users where username=? and active=true",
                (r, n) ->
                    User.withUsername(r.getString("username"))
                        .password(r.getString("password_hash"))
                        .roles(r.getString("role"))
                        .build(),
                username)
            .stream()
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/csrf", "/api/health", "/api/login")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/login")
                    .successHandler(
                        (q, r, a) -> {
                          r.setContentType("application/json");
                          r.getWriter().write("{\"ok\":true}");
                        })
                    .failureHandler((q, r, e) -> r.sendError(401)))
        .logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler((q, r, a) -> r.setStatus(204)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((q, r, x) -> r.sendError(401))
                    .accessDeniedHandler((q, r, x) -> r.sendError(403)))
        .requestCache(c -> c.disable())
        .build();
  }
}
