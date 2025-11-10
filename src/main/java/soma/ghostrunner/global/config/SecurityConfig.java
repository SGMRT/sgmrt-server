package soma.ghostrunner.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import soma.ghostrunner.global.security.jwt.handler.GhostRunAccessDeniedHandler;
import soma.ghostrunner.global.security.jwt.handler.GhostRunAuthenticationEntryPoint;
import soma.ghostrunner.global.security.jwt.JwtAuthFilter;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] AUTH_BLACKLIST = {"/v1/runs", "/v1/runs/**", "/v1/courses", "/v1/courses/**",
            "/v1/members", "/v1/members/**", "/v1/auth/reissue", "/v1/auth/logout", "/v1/admin/**", "/v1/notifications/**",
            "/v1/notices", "/v1/notices/**", "/v2/notices/**", "/v1/push/**"};

    private final GhostRunAuthenticationEntryPoint authenticationEntryPoint;
    private final GhostRunAccessDeniedHandler accessDeniedHandler;
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AUTH_BLACKLIST).authenticated()
                        .anyRequest().permitAll())

                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
