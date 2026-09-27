package edu.campus.flow;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
@Component
public class Bootstrap implements CommandLineRunner {
 final PortalService svc;
 @Value("${app.demo}") boolean demo;
 @Value("${app.bootstrap.pin}") String pin;
 @Value("${app.bootstrap.password}") String password;
 @Value("${app.bootstrap.mobile}") String mobile;
 @Value("${app.bootstrap.name}") String name;
 @Value("${app.bootstrap.department}") String department;
 Bootstrap(PortalService svc){this.svc=svc;}
 public void run(String... args){
  if(svc.database().queryForObject("SELECT COUNT(*) FROM users",Integer.class)>0)return;
  if(!demo){
   if(pin.isBlank()||password.length()<10||!mobile.matches("\\+[1-9][0-9]{9,14}"))throw new IllegalStateException("First startup requires HOD_PIN, HOD_PASSWORD (10+ characters), HOD_MOBILE (international format).");
   add(pin,name,mobile,password,"HOD","APPROVED",null);return;
  }
  add("HOD-CM-001","Dr. Kavitha Rao","+919000000001","Campus@2026","HOD","APPROVED",null);
  add("FAC-CM-101","Ananya Sharma","+919000000002","Campus@2026","TEACHER","APPROVED",null);
  long mentor=PortalService.id(svc.user("FAC-CM-101"),"id");
  add("24093-CM-228","Meghana Reddy","+919000000003","Campus@2026","STUDENT","APPROVED",mentor);
  add("24093-CM-218","Arjun Kumar","+919000000004","Campus@2026","STUDENT","APPROVED",mentor);
  add("24093-CM-206","Sneha Patel","+919000000005","Campus@2026","STUDENT","PENDING",mentor);
  add("FAC-CM-102","Rahul Verma","+919000000006","Campus@2026","TEACHER","PENDING",null);
  for(String student:new String[]{"24093-CM-228","24093-CM-218"}){
   long uid=PortalService.id(svc.user(student),"id");
   for(int i=1;i<=24;i++)svc.database().update("INSERT INTO attendance(student_id,attendance_date,status,marked_by) VALUES (?,?,?,?)",uid,LocalDate.now().minusDays(i),i%8==0?"ABSENT":"PRESENT",mentor);
  }
  svc.apply(svc.user("24093-CM-228"),new Api.LeaveForm(LocalDate.now().plusDays(2),LocalDate.now().plusDays(3),"Medical","Scheduled medical appointment and recovery."));
  svc.apply(svc.user("24093-CM-218"),new Api.LeaveForm(LocalDate.now().plusDays(4),LocalDate.now().plusDays(8),"Family","Family function requiring travel to my hometown."));
  svc.apply(svc.user("FAC-CM-101"),new Api.LeaveForm(LocalDate.now().plusDays(10),LocalDate.now().plusDays(11),"Academic","Attending a faculty development workshop."));
 }
 void add(String p,String n,String m,String pass,String r,String s,Long mentor){svc.database().update("INSERT INTO users(pin,name,mobile,password,role,status,department,mentor_id) VALUES (?,?,?,?,?,?,?,?)",p,n,m,svc.passwordEncoder().encode(pass),r,s,department,mentor);}
}
