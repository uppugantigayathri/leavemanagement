package edu.campus.flow;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortalService {
 static final List<String> DEPARTMENTS=List.of("Computer Engineering","Information Technology","Electronics & Communication","Mechanical Engineering");
 final JdbcTemplate db; final PasswordEncoder encoder; final SmsService sms; final EmailService email;
 public PortalService(JdbcTemplate db,PasswordEncoder encoder,SmsService sms,EmailService email){this.db=db;this.encoder=encoder;this.sms=sms;this.email=email;}
 public JdbcTemplate database(){return db;}
 public PasswordEncoder passwordEncoder(){return encoder;}
 public SmsService messaging(){return sms;}
 public EmailService email(){return email;}
 static void check(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 static long id(Map<String,Object> m,String k){return ((Number)m.get(k)).longValue();}
 Map<String,Object> user(String pin){return db.queryForMap("SELECT * FROM users WHERE pin=?",pin);}
 Map<String,Object> safe(Map<String,Object> u){var copy=new LinkedHashMap<>(u);copy.remove("password");return copy;}
 boolean role(Map<String,Object> u,String r){return r.equals(u.get("role"));}
 boolean manages(Map<String,Object> actor,Map<String,Object> subject){
  if(!"APPROVED".equals(actor.get("status")))return false;
  if(role(actor,"ADMIN"))return role(subject,"HOD");
  if(!Objects.equals(actor.get("department"),subject.get("department")))return false;
  if(role(actor,"HOD"))return role(subject,"STUDENT")||role(subject,"TEACHER");
  return role(actor,"TEACHER")&&role(subject,"STUDENT")&&Objects.equals(id(actor,"id"),subject.get("mentor_id")==null?null:id(subject,"mentor_id"));
 }
 void notifyUser(long uid,String message){
  db.update("INSERT INTO notifications(user_id,message) VALUES (?,?)",uid,message);
  if(sms.configured())db.update("INSERT INTO sms_outbox(user_id,message) VALUES (?,?)",uid,"CampusFlow: "+message);
  if(email.configured())db.queryForList("SELECT email,name FROM users WHERE id=?",uid).forEach(u->email.send((String)u.get("email"),"CampusFlow account update","Hello "+u.get("name")+",\n\n"+message+"\n\nCampusFlow"));
 }
 void notifyReviewers(Map<String,Object> subject,String target,String message){
  if(target.equals("TEACHER")&&subject.get("mentor_id")!=null)notifyUser(id(subject,"mentor_id"),message);
  else if(target.equals("HOD")&&role(subject,"TEACHER")&&subject.get("mentor_id")!=null)notifyUser(id(subject,"mentor_id"),message);
  else if(target.equals("HOD"))db.queryForList("SELECT id FROM users WHERE role='HOD' AND status='APPROVED' AND department=?",subject.get("department")).forEach(h->notifyUser(id(h,"id"),message));
  else db.queryForList("SELECT id FROM users WHERE role='ADMIN' AND status='APPROVED'").forEach(a->notifyUser(id(a,"id"),message));
 }
 @Transactional public void register(Api.Register r){
  check(Set.of("STUDENT","TEACHER","HOD").contains(r.role()),"Select student, teacher, or head of department.");
  check(DEPARTMENTS.contains(r.department()),"Choose a listed department.");
  String section=r.role().equals("STUDENT")?Objects.toString(r.section(),""):"";
  if(r.role().equals("STUDENT"))check(Set.of("A","B","C","D").contains(section),"Choose a class section.");
  Long mentor=null;
  if(r.role().equals("STUDENT")||r.role().equals("TEACHER")){
   check(r.mentorId()!=null,r.role().equals("STUDENT")?"Choose a teacher mentor.":"Choose your head of department.");
   String requiredRole=r.role().equals("STUDENT")?"TEACHER":"HOD";
   var found=db.queryForList("SELECT id FROM users WHERE id=? AND role=? AND status='APPROVED' AND department=?",r.mentorId(),requiredRole,r.department());
   check(!found.isEmpty(),"Choose an approved "+(requiredRole.equals("TEACHER")?"teacher":"HOD")+" in your department.");mentor=r.mentorId();
  }
  String normalizedEmail=r.email().trim().toLowerCase(Locale.ROOT);
  String normalizedPin=r.pin().trim().toUpperCase(Locale.ROOT);
  check(db.queryForObject("SELECT COUNT(*) FROM users WHERE pin=? OR mobile=? OR LOWER(email)=?",Integer.class,normalizedPin,r.mobile(),normalizedEmail)==0,"PIN, mobile number, or email is already registered.");
  db.update("INSERT INTO users(pin,name,email,mobile,password,role,status,department,section,mentor_id) VALUES (?,?,?,?,?,?, 'PENDING',?,?,?)",normalizedPin,r.name(),normalizedEmail,r.mobile(),encoder.encode(r.password()),r.role(),r.department(),section,mentor);
  var u=user(normalizedPin);String target=switch(r.role()){case "STUDENT"->"TEACHER";case "TEACHER"->"HOD";default->"ADMIN";};
  notifyReviewers(u,target,r.name()+" registered and needs your review.");
  email.send(normalizedEmail,"CampusFlow registration received","Hello "+r.name()+",\n\nYour CampusFlow registration has been received and is waiting for approval. You will receive another email when it is reviewed.\n\nCampusFlow");
 }
 @Transactional public void reviewRegistration(Map<String,Object> actor,long uid,Api.Decision d){
  var u=db.queryForMap("SELECT * FROM users WHERE id=? FOR UPDATE",uid);
  boolean correctReviewer=(role(actor,"TEACHER")&&role(u,"STUDENT"))||(role(actor,"HOD")&&role(u,"TEACHER"))||(role(actor,"ADMIN")&&role(u,"HOD"));
  check(correctReviewer&&manages(actor,u),"You cannot review this registration.");
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
  check(!role(actor,"ADMIN"),"Administrator accounts review HOD leave and cannot apply for leave.");
  db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE",id(actor,"id"));
  check(!r.startDate().isBefore(LocalDate.now())&&!r.endDate().isBefore(r.startDate()),"Select valid current or future dates.");
  long days=ChronoUnit.DAYS.between(r.startDate(),r.endDate())+1;check(days<=30,"One request may cover at most 30 calendar days.");
  check(Set.of("Medical","Personal","Family","Academic").contains(r.kind()),"Invalid leave type.");
  check(db.queryForObject("SELECT COUNT(*) FROM leaves WHERE user_id=? AND status NOT IN ('REJECTED','CANCELLED') AND start_date<=? AND end_date>=?",Integer.class,id(actor,"id"),r.endDate(),r.startDate())==0,"These dates overlap an existing request.");
  boolean student=role(actor,"STUDENT"),teacher=role(actor,"TEACHER"),hodApplicant=role(actor,"HOD");
  double pct=student?attendance(id(actor,"id")):-1;
  boolean lowAttendance=student&&(pct<0||pct<75);
  boolean hodReview=hodApplicant||teacher||days>=4||lowAttendance;
  String status=hodApplicant?"PENDING_ADMIN":hodReview?"PENDING_HOD":"PENDING_TEACHER";
  String recommendation=hodApplicant?"ADMIN_REVIEW":hodReview?"HOD_REVIEW":"TEACHER_REVIEW";
  String attendanceText=!student?"Not applicable to this role. ":pct<0?"Student attendance: no days recorded; below the 75% threshold. ":"Student attendance: "+pct+"% (75% threshold). ";
  String routeText=hodApplicant?"HOD leave is routed to the Admin.":teacher?"Teacher leave is routed to the HOD.":hodReview?"HOD review is required.":"Teacher review is allowed under the 3-day policy.";
  String analysis=attendanceText+days+" calendar day(s). "+(days>=4?"Duration is 4 or more days. ":"")+(lowAttendance?"Attendance is below 75%, so this short request is also routed to the HOD. ":"")+routeText;
  db.update("INSERT INTO leaves(user_id,kind,start_date,end_date,reason,status,recommendation,analysis) VALUES (?,?,?,?,?,?,?,?)",id(actor,"id"),r.kind(),r.startDate(),r.endDate(),r.reason(),status,recommendation,analysis);
  String reviewer=hodApplicant?"Admin":hodReview?"HOD":"teacher";
  notifyReviewers(actor,hodApplicant?"ADMIN":hodReview?"HOD":"TEACHER",actor.get("name")+" submitted a leave request for "+r.startDate()+".");
  notifyUser(id(actor,"id"),"Your leave request has been submitted for "+reviewer+" review.");
 }
 boolean canReview(Map<String,Object> actor,Map<String,Object> l){
  var owner=db.queryForMap("SELECT * FROM users WHERE id=?",l.get("user_id"));
  return role(actor,"ADMIN")?role(owner,"HOD")&&"PENDING_ADMIN".equals(l.get("status"))&&manages(actor,owner):
   role(actor,"HOD")?"PENDING_HOD".equals(l.get("status"))&&manages(actor,owner):
   role(actor,"TEACHER")&&"PENDING_TEACHER".equals(l.get("status"))&&manages(actor,owner);
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
  String peopleScope;Object[] peopleArgs;
  if(role(actor,"STUDENT")){peopleScope="u.id=?";peopleArgs=new Object[]{id(actor,"id")};}
  else if(role(actor,"TEACHER")){peopleScope="u.id=? OR (u.role='STUDENT' AND u.mentor_id=?)";peopleArgs=new Object[]{id(actor,"id"),id(actor,"id")};}
  else if(role(actor,"HOD")){peopleScope="u.department=? AND u.role IN ('STUDENT','TEACHER','HOD')";peopleArgs=new Object[]{actor.get("department")};}
  else {peopleScope="u.role='HOD'";peopleArgs=new Object[]{};}
  String leaveScope;Object[] leaveArgs;
  if(role(actor,"STUDENT")){leaveScope="u.id=?";leaveArgs=new Object[]{id(actor,"id")};}
  else if(role(actor,"TEACHER")){leaveScope="u.id=? OR (u.role='STUDENT' AND u.mentor_id=?)";leaveArgs=new Object[]{id(actor,"id"),id(actor,"id")};}
  else if(role(actor,"HOD")){leaveScope="(u.department=? AND ((u.role='STUDENT' AND (DATEDIFF(l.end_date,l.start_date)+1>=4 OR l.recommendation='HOD_REVIEW' OR l.status='PENDING_HOD')) OR (u.role='TEACHER' AND l.status='PENDING_HOD'))) OR u.id=?";leaveArgs=new Object[]{actor.get("department"),id(actor,"id")};}
  else {leaveScope="u.role='HOD'";leaveArgs=new Object[]{};}
  var leaves=db.queryForList("SELECT l.*,u.name,u.pin,u.role,u.department,u.section FROM leaves l JOIN users u ON u.id=l.user_id WHERE "+leaveScope+" ORDER BY l.created_at DESC",leaveArgs);
  leaves.forEach(l->{l.put("can_review",canReview(actor,l));l.put("events",db.queryForList("SELECT e.action,e.note,e.created_at,u.name FROM leave_events e JOIN users u ON u.id=e.actor_id WHERE e.leave_id=? ORDER BY e.id",l.get("id")));});
  result.put("leaves",leaves);
  result.put("people",db.queryForList("SELECT u.id,u.pin,u.name,u.mobile,u.role,u.status,u.department,u.section,u.mentor_id,u.review_note FROM users u WHERE "+peopleScope+" ORDER BY u.name",peopleArgs));
  String attendanceScope;Object[] attendanceArgs;
  if(role(actor,"STUDENT")){attendanceScope="u.id=?";attendanceArgs=new Object[]{id(actor,"id")};}
  else if(role(actor,"TEACHER")){attendanceScope="u.role='STUDENT' AND u.mentor_id=?";attendanceArgs=new Object[]{id(actor,"id")};}
  else if(role(actor,"HOD")){attendanceScope="u.role='STUDENT' AND u.department=?";attendanceArgs=new Object[]{actor.get("department")};}
  else {attendanceScope="1=0";attendanceArgs=new Object[]{};}
  result.put("attendance",db.queryForList("SELECT a.*,u.name,u.pin,u.department,u.section FROM attendance a JOIN users u ON u.id=a.student_id WHERE "+attendanceScope+" ORDER BY a.attendance_date DESC",attendanceArgs));
  result.put("attendancePercent",role(actor,"STUDENT")?attendance(id(actor,"id")):-1);
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
