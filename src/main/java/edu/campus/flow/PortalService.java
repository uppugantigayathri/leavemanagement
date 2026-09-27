package edu.campus.flow;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortalService {
 final JdbcTemplate db; final PasswordEncoder encoder; final SmsService sms;
 public PortalService(JdbcTemplate db,PasswordEncoder encoder,SmsService sms){this.db=db;this.encoder=encoder;this.sms=sms;}
 public JdbcTemplate database(){return db;}
 public PasswordEncoder passwordEncoder(){return encoder;}
 public SmsService messaging(){return sms;}
 static void check(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 static long id(Map<String,Object> m,String k){return ((Number)m.get(k)).longValue();}
 Map<String,Object> user(String pin){return db.queryForMap("SELECT * FROM users WHERE pin=?",pin);}
 Map<String,Object> safe(Map<String,Object> u){var copy=new LinkedHashMap<>(u);copy.remove("password");return copy;}
 boolean role(Map<String,Object> u,String r){return r.equals(u.get("role"));}
 boolean manages(Map<String,Object> actor,Map<String,Object> subject){
  return "APPROVED".equals(actor.get("status")) && Objects.equals(actor.get("department"),subject.get("department")) &&
   ((role(actor,"HOD")&&!role(subject,"HOD")) || (role(actor,"TEACHER")&&role(subject,"STUDENT")&&Objects.equals(id(actor,"id"),subject.get("mentor_id")==null?null:id(subject,"mentor_id"))));
 }
 void notifyUser(long uid,String message){
  db.update("INSERT INTO notifications(user_id,message) VALUES (?,?)",uid,message);
  if(sms.configured())db.update("INSERT INTO sms_outbox(user_id,message) VALUES (?,?)",uid,"CampusFlow: "+message);
 }
 void notifyReviewers(Map<String,Object> subject,String target,String message){
  if(target.equals("TEACHER")&&subject.get("mentor_id")!=null)notifyUser(id(subject,"mentor_id"),message);
  else db.queryForList("SELECT id FROM users WHERE role='HOD' AND status='APPROVED' AND department=?",subject.get("department")).forEach(h->notifyUser(id(h,"id"),message));
 }
 @Transactional public void register(Api.Register r){
  check(Set.of("STUDENT","TEACHER").contains(r.role()),"Select student or teacher.");
  Long mentor=null;
  if(r.role().equals("STUDENT")){
   check(r.mentorId()!=null,"Choose a mentor.");
   var found=db.queryForList("SELECT * FROM users WHERE id=? AND role='TEACHER' AND status='APPROVED' AND department=?",r.mentorId(),r.department());
   check(!found.isEmpty(),"Choose an approved mentor in your department.");mentor=r.mentorId();
  }
  check(!db.queryForList("SELECT id FROM users WHERE role='HOD' AND status='APPROVED' AND department=?",r.department()).isEmpty(),"Department is not configured.");
  check(db.queryForObject("SELECT COUNT(*) FROM users WHERE pin=? OR mobile=?",Integer.class,r.pin().toUpperCase(),r.mobile())==0,"PIN or mobile number is already registered.");
  db.update("INSERT INTO users(pin,name,mobile,password,role,status,department,mentor_id) VALUES (?,?,?,?,?,'PENDING',?,?)",r.pin().toUpperCase(),r.name(),r.mobile(),encoder.encode(r.password()),r.role(),r.department(),mentor);
  var u=user(r.pin().toUpperCase());notifyReviewers(u,r.role().equals("STUDENT")?"TEACHER":"HOD",r.name()+" registered and needs your review.");
 }
 @Transactional public void reviewRegistration(Map<String,Object> actor,long uid,Api.Decision d){
  var u=db.queryForMap("SELECT * FROM users WHERE id=? FOR UPDATE",uid);
  check((role(actor,"TEACHER")&&role(u,"STUDENT")||role(actor,"HOD")&&role(u,"TEACHER"))&&manages(actor,u),"You cannot review this registration.");
  check("PENDING".equals(u.get("status")),"Registration was already reviewed.");
  check(Set.of("APPROVE","REJECT").contains(d.action()),"Invalid decision.");
  db.update("UPDATE users SET status=?,review_note=? WHERE id=?",d.action().equals("APPROVE")?"APPROVED":"REJECTED",d.note(),uid);
  notifyUser(uid,"Your registration was "+(d.action().equals("APPROVE")?"approved":"rejected")+". "+d.note());
 }
 double attendance(long uid){
  int total=db.queryForObject("SELECT COUNT(*) FROM attendance WHERE student_id=?",Integer.class,uid);
  if(total==0)return -1;
  int present=db.queryForObject("SELECT COUNT(*) FROM attendance WHERE student_id=? AND status='PRESENT'",Integer.class,uid);
  return Math.round(1000.0*present/total)/10.0;
 }
 @Transactional public void apply(Map<String,Object> actor,Api.LeaveForm r){
  check(!role(actor,"HOD"),"HOD accounts review leave.");
  db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE",id(actor,"id"));
  check(!r.startDate().isBefore(LocalDate.now())&&!r.endDate().isBefore(r.startDate()),"Select valid current or future dates.");
  long days=ChronoUnit.DAYS.between(r.startDate(),r.endDate())+1;check(days<=30,"One request may cover at most 30 calendar days.");
  check(Set.of("Medical","Personal","Family","Academic").contains(r.kind()),"Invalid leave type.");
  check(db.queryForObject("SELECT COUNT(*) FROM leaves WHERE user_id=? AND status NOT IN ('REJECTED','CANCELLED') AND start_date<=? AND end_date>=?",Integer.class,id(actor,"id"),r.endDate(),r.startDate())==0,"These dates overlap an existing request.");
  double pct=attendance(id(actor,"id"));
  int recent=db.queryForObject("SELECT COUNT(*) FROM leaves WHERE user_id=? AND created_at>=? AND status<>'CANCELLED'",Integer.class,id(actor,"id"),LocalDateTime.now().minusDays(30));
  boolean hod=role(actor,"TEACHER")||pct<75||days>3||recent>=3;
  String recommendation=hod?"HOD_REVIEW":"APPROVE";
  String analysis=(role(actor,"TEACHER")?"Teacher leave requires HOD review. ":pct<0?"No attendance records yet; HOD review required. ":"Recorded attendance: "+pct+"%. ")+days+" calendar day(s). "+recent+" prior request(s) in 30 days. "+(hod?"Policy requires HOD review.":"Within the 75% attendance and 3-day policy.");
  db.update("INSERT INTO leaves(user_id,kind,start_date,end_date,reason,status,recommendation,analysis) VALUES (?,?,?,?,?,?,?,?)",id(actor,"id"),r.kind(),r.startDate(),r.endDate(),r.reason(),hod?"PENDING_HOD":"PENDING_TEACHER",recommendation,analysis);
  notifyReviewers(actor,hod?"HOD":"TEACHER",actor.get("name")+" submitted a leave request for "+r.startDate()+".");
  notifyUser(id(actor,"id"),"Your leave request has been submitted for "+(hod?"HOD":"mentor")+" review.");
 }
 boolean canReview(Map<String,Object> actor,Map<String,Object> l){
  var owner=db.queryForMap("SELECT * FROM users WHERE id=?",l.get("user_id"));
  return manages(actor,owner)&&((role(actor,"HOD")&&"PENDING_HOD".equals(l.get("status")))||(role(actor,"TEACHER")&&"PENDING_TEACHER".equals(l.get("status"))));
 }
 @Transactional public void decide(Map<String,Object> actor,long lid,Api.Decision d){
  var l=db.queryForMap("SELECT * FROM leaves WHERE id=? FOR UPDATE",lid);
  if(d.action().equals("CANCEL"))check(id(l,"user_id")==id(actor,"id")&&l.get("status").toString().startsWith("PENDING"),"Only your pending requests can be cancelled.");
  else check(canReview(actor,l),"This request is not awaiting your review.");
  String next=switch(d.action()){case "APPROVE"->"APPROVED";case "REJECT"->"REJECTED";case "CANCEL"->"CANCELLED";case "ESCALATE"->{check(role(actor,"TEACHER"),"Only mentors can escalate.");yield "PENDING_HOD";}default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid decision.");};
  db.update("UPDATE leaves SET status=? WHERE id=?",next,lid);
  db.update("INSERT INTO leave_events(leave_id,actor_id,action,note) VALUES (?,?,?,?)",lid,id(actor,"id"),d.action(),d.note());
  notifyUser(id(l,"user_id"),"Leave #"+lid+": "+next.replace('_',' ')+". "+d.note());
  var owner=db.queryForMap("SELECT * FROM users WHERE id=?",l.get("user_id"));
  if(next.equals("PENDING_HOD"))notifyReviewers(owner,"HOD","Leave #"+lid+" was escalated to you.");
 }
 @Transactional public void markAttendance(Map<String,Object> actor,Api.AttendanceForm form){
  check(role(actor,"TEACHER"),"Only teachers can mark attendance.");
  check(!form.date().isAfter(LocalDate.now())&&!form.date().isBefore(LocalDate.now().minusDays(7)),"Attendance may be entered for today or the previous 7 days.");
  check(form.entries().size()<=200,"Maximum 200 students per submission.");
  db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE",id(actor,"id"));
  for(var entry:form.entries()){
   var student=db.queryForMap("SELECT * FROM users WHERE id=?",entry.studentId());
   check(manages(actor,student)&&"APPROVED".equals(student.get("status")),"Student is not in your approved roster.");
   check(Set.of("PRESENT","ABSENT","LEAVE").contains(entry.status()),"Invalid attendance status.");
   if(entry.status().equals("LEAVE"))check(db.queryForObject("SELECT COUNT(*) FROM leaves WHERE user_id=? AND status='APPROVED' AND start_date<=? AND end_date>=?",Integer.class,entry.studentId(),form.date(),form.date())>0,"Leave marking requires an approved leave for that date.");
   int updated=db.update("UPDATE attendance SET status=?,marked_by=?,updated_at=CURRENT_TIMESTAMP WHERE student_id=? AND attendance_date=?",entry.status(),id(actor,"id"),entry.studentId(),form.date());
   if(updated==0)db.update("INSERT INTO attendance(student_id,attendance_date,status,marked_by) VALUES (?,?,?,?)",entry.studentId(),form.date(),entry.status(),id(actor,"id"));
  }
 }
 public Map<String,Object> dashboard(Map<String,Object> actor){
  var result=new LinkedHashMap<String,Object>();result.put("user",safe(actor));
  String scope=role(actor,"STUDENT")?"u.id=?":role(actor,"TEACHER")?"(u.id=? OR u.mentor_id=?)":"u.department=?";
  Object[] args=role(actor,"STUDENT")?new Object[]{id(actor,"id")}:role(actor,"TEACHER")?new Object[]{id(actor,"id"),id(actor,"id")}:new Object[]{actor.get("department")};
  var leaves=db.queryForList("SELECT l.*,u.name,u.pin,u.role FROM leaves l JOIN users u ON u.id=l.user_id WHERE "+scope+" ORDER BY l.created_at DESC",args);
  leaves.forEach(l->{l.put("can_review",canReview(actor,l));l.put("events",db.queryForList("SELECT e.action,e.note,e.created_at,u.name FROM leave_events e JOIN users u ON u.id=e.actor_id WHERE e.leave_id=? ORDER BY e.id",l.get("id")));});
  result.put("leaves",leaves);
  result.put("people",db.queryForList("SELECT u.id,u.pin,u.name,u.mobile,u.role,u.status,u.department,u.mentor_id,u.review_note FROM users u WHERE "+scope+" ORDER BY u.name",args));
  result.put("attendance",db.queryForList("SELECT a.*,u.name,u.pin FROM attendance a JOIN users u ON u.id=a.student_id WHERE "+scope+" ORDER BY a.attendance_date DESC",args));
  result.put("attendancePercent",attendance(id(actor,"id")));
  result.put("notifications",db.queryForList("SELECT * FROM notifications WHERE user_id=? ORDER BY id DESC LIMIT 50",id(actor,"id")));
  result.replaceAll((key,value)->jsonValue(value));
  return result;
 }
 private static Object jsonValue(Object value){
  if(value instanceof java.sql.Date d)return d.toLocalDate().toString();
  if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime().toString();
  if(value instanceof Map<?,?> m){var out=new LinkedHashMap<String,Object>();m.forEach((k,v)->out.put(k.toString(),jsonValue(v)));return out;}
  if(value instanceof List<?> l)return l.stream().map(PortalService::jsonValue).toList();
  return value;
 }
}
