package edu.campus.flow;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class Bootstrap implements CommandLineRunner {
 final PortalService svc;
 @Value("${app.demo}") boolean demo;
 @Value("${app.bootstrap.pin}") String pin;
 @Value("${app.bootstrap.password}") String password;
 @Value("${app.bootstrap.mobile}") String mobile;
 @Value("${app.bootstrap.email:}") String email;
 @Value("${app.bootstrap.name}") String name;
 Bootstrap(PortalService svc){this.svc=svc;}
 public void run(String... args){
  int userCount=svc.database().queryForObject("SELECT COUNT(*) FROM users",Integer.class);
  if(userCount>0){
   if(!demo&&svc.database().queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN'",Integer.class)==0&&!pin.isBlank()){
    validateAdmin();add(pin,name,email,mobile,password,"ADMIN","APPROVED","Central Administration","",null);
   }
   return;
  }
  if(!demo){
   validateAdmin();
   add(pin,name,email,mobile,password,"ADMIN","APPROVED","Central Administration","",null);return;
  }
  add("ADMIN-CM-001","Campus Administrator","admin@example.com","9000000001","Campus@2026","ADMIN","APPROVED","Central Administration","",null);
  String[] departments={"Computer Engineering","Information Technology","Electronics & Communication","Mechanical Engineering"};
  String[][] hods={
   {"HOD-CM-001","Dr. Kavitha Rao"},{"HOD-IT-001","Dr. Nikhil Rao"},
   {"HOD-EC-001","Dr. Meera Nair"},{"HOD-ME-001","Dr. Arjun Das"}};
  String[][] teachers={
   {"FAC-CM-101","Ananya Sharma"},{"FAC-IT-101","Rahul Verma"},
   {"FAC-EC-101","Priya Menon"},{"FAC-ME-101","Rohit Kumar"}};
  String[][][] students={
   {{"24093-CM-228","Meghana Reddy"},{"24093-CM-218","Arjun Kumar"},{"24093-CM-206","Sneha Patel"},{"24093-CM-252","Yandamuri Dhatri"}},
   {{"24093-IT-301","Aarav Shah"},{"24093-IT-302","Diya Kapoor"},{"24093-IT-303","Ishaan Roy"},{"24093-IT-304","Nisha Jain"}},
   {{"24093-EC-401","Aanya Rao"},{"24093-EC-402","Kabir Das"},{"24093-EC-403","Mira Sen"},{"24093-EC-404","Vihaan Paul"}},
   {{"24093-ME-501","Anika Bose"},{"24093-ME-502","Dev Malhotra"},{"24093-ME-503","Rhea Iyer"},{"24093-ME-504","Samar Gill"}}
  };
  long[] teacherIds=new long[departments.length];
  int mobileSuffix=2;
  for(int d=0;d<departments.length;d++){
   add(hods[d][0],hods[d][1],"hod"+(d+1)+"@example.com",mobileNumber(++mobileSuffix),"Campus@2026","HOD","APPROVED",departments[d],"",null);
   long hodId=PortalService.id(svc.user(hods[d][0]),"id");
   add(teachers[d][0],teachers[d][1],"teacher"+(d+1)+"@example.com",mobileNumber(++mobileSuffix),"Campus@2026","TEACHER","APPROVED",departments[d],"",hodId);
   teacherIds[d]=PortalService.id(svc.user(teachers[d][0]),"id");
   for(int s=0;s<4;s++){
    String section=String.valueOf((char)('A'+s));
    String studentPin=students[d][s][0];
    add(studentPin,students[d][s][1],"student"+studentPin.substring(studentPin.length()-3)+"@example.com",mobileNumber(++mobileSuffix),"Campus@2026","STUDENT","APPROVED",departments[d],section,teacherIds[d]);
    long studentId=PortalService.id(svc.user(studentPin),"id");
    for(int day=30;day>=1;day--){
     boolean present=studentPin.equals("24093-CM-252")?day>15:day%6!=0;
     svc.database().update("INSERT INTO attendance(student_id,attendance_date,status,marked_by) VALUES (?,?,?,?)",studentId,LocalDate.now().minusDays(day),present?"PRESENT":"ABSENT",teacherIds[d]);
    }
   }
  }
  // Sample requests demonstrate each review route, including a short leave escalated for low attendance.
  svc.apply(svc.user("24093-CM-228"),new Api.LeaveForm(LocalDate.now().plusDays(2),LocalDate.now().plusDays(3),"Medical","Scheduled medical appointment and recovery."));
  svc.apply(svc.user("24093-CM-252"),new Api.LeaveForm(LocalDate.now().plusDays(2),LocalDate.now().plusDays(3),"Family","Family function requiring travel."));
  svc.apply(svc.user("24093-IT-301"),new Api.LeaveForm(LocalDate.now().plusDays(5),LocalDate.now().plusDays(8),"Academic","Academic event requiring travel."));
  svc.apply(svc.user(hods[0][0]),new Api.LeaveForm(LocalDate.now().plusDays(10),LocalDate.now().plusDays(11),"Academic","Department development workshop."));
 }
 void validateAdmin(){
  if(pin.isBlank()||password.length()<10||!mobile.matches("[0-9]{10}")||!email.matches("(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$"))
   throw new IllegalStateException("First startup requires ADMIN_PIN, ADMIN_PASSWORD (10+ characters), ADMIN_MOBILE (10 digits), and ADMIN_EMAIL.");
 }
 static String mobileNumber(int n){return "900000"+String.format("%04d",n);}
 void add(String p,String n,String e,String m,String pass,String r,String s,String dept,String section,Long mentor){
  svc.database().update("INSERT INTO users(pin,name,email,mobile,password,role,status,department,section,mentor_id) VALUES (?,?,?,?,?,?,?,?,?,?)",p,n,e,m,svc.passwordEncoder().encode(pass),r,s,dept,section,mentor);
 }
}
