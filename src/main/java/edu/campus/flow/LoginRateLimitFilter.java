package edu.campus.flow;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;
/** Single-node login throttling. Reverse proxies must preserve a trusted client address. */
public class LoginRateLimitFilter extends OncePerRequestFilter {
 private final Map<String,Deque<Long>> attempts=new HashMap<>();
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
  if(request.getMethod().equals("POST")&&request.getRequestURI().equals("/api/login")){
   synchronized(attempts){
    long now=System.currentTimeMillis();
    attempts.values().forEach(q->{while(!q.isEmpty()&&q.peekFirst()<now-60000)q.removeFirst();});attempts.entrySet().removeIf(e->e.getValue().isEmpty());
    var q=attempts.computeIfAbsent(request.getRemoteAddr(),k->new ArrayDeque<>());
    if(q.size()>=20){response.setStatus(429);response.setHeader("Retry-After","60");response.setContentType("application/json");response.getWriter().write("{\"message\":\"Too many sign-in attempts. Please wait a minute.\"}");return;}
    q.addLast(now);
   }
  }
  chain.doFilter(request,response);
 }
}
