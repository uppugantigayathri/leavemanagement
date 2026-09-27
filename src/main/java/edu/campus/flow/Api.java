package edu.campus.flow;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.support.TransactionTemplate;

@RestController @RequestMapping("/api")
public class Api {
 final PortalService svc; final TransactionTemplate tx; final org.springframework.security.core.session.SessionRegistry sessions;
 @Value("${app.demo}") boolean demo;
 final Map<String,Long> resetThrottle=new ConcurrentHashMap<>();
 public Api(PortalService svc,TransactionTemplate tx,org.springframework.security.core.session.SessionRegistry sessions){this.svc=svc;this.tx=tx;this.sessions=sessions;}
 public record Register(@NotBlank @Pattern(regexp="[A-Za-z0-9-]{5,40}") String pin,@NotBlank @Size(max=100) String name,
  @Pattern(regexp="\\+[1-9][0-9]{9,14}") @NotNull String mobile,@NotBlank @Size(min=10,max=64) String password,
  @NotNull String role,@NotBlank @Size(max=100) String department,Long mentorId){}
 public record LeaveForm(@NotNull LocalDate startDate,@NotNull LocalDate endDate,@NotBlank String kind,@NotBlank @Size(min=10,max=2000) String reason){}
 public record Decision(@NotBlank String action,@NotBlank @Size(max=500) String note){}
 public record AttendanceEntry(long studentId,@NotBlank String status){}
 public record AttendanceForm(@NotNull LocalDate date,@NotNull @Size(min=1,max=200) List<@Valid AttendanceEntry> entries){}
 public record Forgot(@NotBlank @Size(max=40) String pin,@NotBlank @Size(max=20) String mobile){}
 public record Reset(@NotBlank @Size(max=40) String pin,@Pattern(regexp="[0-9]{6}") @NotNull String code,@Size(min=10,max=64) @NotNull String password){}
 @GetMapping("/public/config") Object config(CsrfToken csrf){
  return Map.of("csrfToken",csrf.getToken(),"csrfHeader",csrf.getHeaderName(),"demo",demo,"smsEnabled",svc.messaging().configured(),
   "departments",svc.database().queryForList("SELECT DISTINCT department FROM users WHERE role='HOD' AND status='APPROVED'"),
   "mentors",svc.database().queryForList("SELECT id,name,department FROM users WHERE role='TEACHER' AND status='APPROVED'"));
 }
 @PostMapping("/public/register") Object register(@Valid @RequestBody Register r){svc.register(r);return Map.of("message","Registration submitted. Your reviewer must approve access before you can sign in.");}
 @GetMapping("/dashboard") Object dashboard(Principal p){return svc.dashboard(svc.user(p.getName()));}
 @PostMapping("/registrations/{id}/decision") Object review(Principal p,@PathVariable long id,@Valid @RequestBody Decision d){svc.reviewRegistration(svc.user(p.getName()),id,d);return Map.of("ok",true);}
 @PostMapping("/leaves") Object apply(Principal p,@Valid @RequestBody LeaveForm r){svc.apply(svc.user(p.getName()),r);return Map.of("ok",true);}
 @PostMapping("/leaves/{id}/decision") Object decide(Principal p,@PathVariable long id,@Valid @RequestBody Decision d){svc.decide(svc.user(p.getName()),id,d);return Map.of("ok",true);}
 @PostMapping("/attendance") Object attendance(Principal p,@Valid @RequestBody AttendanceForm r){svc.markAttendance(svc.user(p.getName()),r);return Map.of("ok",true);}
 @PostMapping("/notifications/read") Object read(Principal p){svc.database().update("UPDATE notifications SET is_read=TRUE WHERE user_id=?",PortalService.id(svc.user(p.getName()),"id"));return Map.of("ok",true);}
 @PostMapping("/public/forgot") Object forgot(@Valid @RequestBody Forgot r,HttpServletRequest request){
  PortalService.check(svc.messaging().configured(),"SMS recovery is not configured. Contact your department administrator.");
  String key=r.pin().toUpperCase();long now=System.currentTimeMillis();
  synchronized(resetThrottle){
   resetThrottle.entrySet().removeIf(e->now-e.getValue()>3600000);
   PortalService.check(now-resetThrottle.getOrDefault(key,0L)>60000 && now-resetThrottle.getOrDefault("ip:"+request.getRemoteAddr(),0L)>60000,"Please wait one minute before requesting another code.");
   resetThrottle.put(key,now);resetThrottle.put("ip:"+request.getRemoteAddr(),now);
  }
  var users=svc.database().queryForList("SELECT * FROM users WHERE pin=? AND mobile=? AND status='APPROVED'",key,r.mobile());
  if(!users.isEmpty()){
   long uid=PortalService.id(users.get(0),"id");String code=String.format("%06d",new SecureRandom().nextInt(1000000));
   tx.executeWithoutResult(s->{svc.database().update("DELETE FROM reset_codes WHERE user_id=?",uid);svc.database().update("INSERT INTO reset_codes(user_id,code_hash,expires_at) VALUES (?,?,?)",uid,svc.passwordEncoder().encode(code),LocalDateTime.now().plusMinutes(5));});
   if(!svc.messaging().send(r.mobile(),"CampusFlow password reset code: "+code+". Expires in 5 minutes. Do not share this code."))svc.database().update("DELETE FROM reset_codes WHERE user_id=?",uid);
  }
  return Map.of("message","If the PIN and mobile match an approved account, a code will be sent. It expires in 5 minutes.");
 }
 @PostMapping("/public/reset") Object reset(@Valid @RequestBody Reset r){
  Boolean valid=tx.execute(s->{
   var rows=svc.database().queryForList("SELECT c.* FROM reset_codes c JOIN users u ON u.id=c.user_id WHERE u.pin=? FOR UPDATE",r.pin().toUpperCase());
   if(rows.isEmpty())return false;var c=rows.get(0);long uid=PortalService.id(c,"user_id");
   if(((Number)c.get("attempts")).intValue()>=5||((java.sql.Timestamp)c.get("expires_at")).toLocalDateTime().isBefore(LocalDateTime.now()))return false;
   svc.database().update("UPDATE reset_codes SET attempts=attempts+1 WHERE user_id=?",uid);
   if(!svc.passwordEncoder().matches(r.code(),(String)c.get("code_hash")))return false;
   svc.database().update("UPDATE users SET password=? WHERE id=?",svc.passwordEncoder().encode(r.password()),uid);svc.database().update("DELETE FROM reset_codes WHERE user_id=?",uid);return true;
  });
  PortalService.check(Boolean.TRUE.equals(valid),"Invalid or expired code. Request another code if needed.");
  sessions.getAllPrincipals().stream().filter(p->p instanceof org.springframework.security.core.userdetails.UserDetails u&&u.getUsername().equalsIgnoreCase(r.pin())).forEach(p->sessions.getAllSessions(p,false).forEach(org.springframework.security.core.session.SessionInformation::expireNow));
  return Map.of("message","Password changed. Sign in with your new password.");
 }
}
