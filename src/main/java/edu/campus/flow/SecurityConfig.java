package edu.campus.flow;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.jdbc.core.JdbcTemplate;
@Configuration
public class SecurityConfig {
 @Bean org.springframework.security.core.session.SessionRegistry sessionRegistry(){return new org.springframework.security.core.session.SessionRegistryImpl();}
 @Bean org.springframework.security.web.session.HttpSessionEventPublisher sessionEvents(){return new org.springframework.security.web.session.HttpSessionEventPublisher();}
 @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService users(JdbcTemplate db){return pin -> {
  var rows=db.queryForList("SELECT * FROM users WHERE pin=?",pin.trim().toUpperCase());
  if(rows.isEmpty())throw new UsernameNotFoundException("Invalid credentials");
  var u=rows.get(0);
  return User.withUsername((String)u.get("pin")).password((String)u.get("password"))
   .roles((String)u.get("role")).disabled(!"APPROVED".equals(u.get("status"))).build();
 };}
 @Bean SecurityFilterChain chain(HttpSecurity http,org.springframework.security.core.session.SessionRegistry sessions)throws Exception {
  return http.authorizeHttpRequests(a->a.requestMatchers("/","/index.html","/app.js","/style.css","/api/public/**","/error").permitAll().anyRequest().authenticated())
   .addFilterBefore(new LoginRateLimitFilter(),org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
   .sessionManagement(s->s.maximumSessions(5).sessionRegistry(sessions).expiredSessionStrategy(e->{e.getResponse().setStatus(401);e.getResponse().setContentType("application/json");e.getResponse().getWriter().write("{\"message\":\"Session expired. Please sign in again.\"}");}))
   .formLogin(f->f.loginProcessingUrl("/api/login").usernameParameter("pin")
    .successHandler((q,s,a)->{s.setContentType("application/json");s.getWriter().write("{\"ok\":true}");})
    .failureHandler((q,s,e)->{s.setStatus(401);s.setContentType("application/json");s.getWriter().write("{\"message\":\"Invalid PIN/password, or registration is awaiting approval.\"}");}))
   .logout(l->l.logoutUrl("/api/logout").logoutSuccessHandler((q,s,a)->s.setStatus(204)))
   .exceptionHandling(e->e.authenticationEntryPoint((q,s,x)->s.sendError(401)))
   .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; frame-ancestors 'none'")))
   .build();
 }
}
