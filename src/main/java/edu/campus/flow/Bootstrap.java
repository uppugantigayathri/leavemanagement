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
  String[][] studentPins={
   {"24093-CM-228","24093-CM-201","24093-CM-202","24093-CM-203","24093-CM-218","24093-CM-204","24093-CM-205","24093-CM-207","24093-CM-206","24093-CM-208","24093-CM-209","24093-CM-210","24093-CM-252","24093-CM-211","24093-CM-212","24093-CM-213"},
   {"24093-IT-301","24093-IT-305","24093-IT-306","24093-IT-307","24093-IT-302","24093-IT-308","24093-IT-309","24093-IT-310","24093-IT-303","24093-IT-311","24093-IT-312","24093-IT-313","24093-IT-304","24093-IT-314","24093-IT-315","24093-IT-316"},
   {"24093-EC-401","24093-EC-405","24093-EC-406","24093-EC-407","24093-EC-402","24093-EC-408","24093-EC-409","24093-EC-410","24093-EC-403","24093-EC-411","24093-EC-412","24093-EC-413","24093-EC-404","24093-EC-414","24093-EC-415","24093-EC-416"},
   {"24093-ME-501","24093-ME-505","24093-ME-506","24093-ME-507","24093-ME-502","24093-ME-508","24093-ME-509","24093-ME-510","24093-ME-503","24093-ME-511","24093-ME-512","24093-ME-513","24093-ME-504","24093-ME-514","24093-ME-515","24093-ME-516"}
  };
  String[][] studentNames={
   {"Meghana Reddy","Ira Nair","Karthik Rao","Sana Iyer","Arjun Kumar","Rohan Das","Pooja Sen","Nikhil Jain","Sneha Patel","Kavya Bose","Aditya Roy","Tara Menon","Yandamuri Dhatri","Vivek Shah","Mira Gill","Aarav Paul"},
   {"Aarav Shah","Anaya Kapoor","Ishaan Roy","Nisha Jain","Diya Kapoor","Kabir Das","Mira Sen","Vihaan Paul","Riya Nair","Dev Rao","Tanya Bose","Neil Iyer","Sia Menon","Arjun Gill","Aditi Verma","Rohan Patel"},
   {"Aanya Rao","Kabir Das","Mira Sen","Vihaan Paul","Ishita Nair","Reyansh Shah","Tara Kapoor","Manav Jain","Kiara Roy","Ayaan Bose","Myra Iyer","Neil Menon","Zoya Gill","Rudra Patel","Aarohi Verma","Yash Paul"},
   {"Anika Bose","Dev Malhotra","Rhea Iyer","Samar Gill","Aarav Menon","Diya Shah","Kabir Nair","Tara Rao","Ishan Kapoor","Mira Patel","Rohan Paul","Sana Verma","Neel Das","Aditi Roy","Kavya Jain","Vivaan Sen"}
  };
  long[] teacherIds=new long[departments.length];
  int mobileSuffix=2;
  for(int d=0;d<departments.length;d++){
   add(hods[d][0],hods[d][1],"hod"+(d+1)+"@example.com",mobileNumber(++mobileSuffix),"Campus@2026","HOD","APPROVED",departments[d],"",null);
   long hodId=PortalService.id(svc.user(hods[d][0]),"id");
   add(teachers[d][0],teachers[d][1],"teacher"+(d+1)+"@example.com",mobileNumber(++mobileSuffix),"Campus@2026","TEACHER","APPROVED",departments[d],"",hodId);
   teacherIds[d]=PortalService.id(svc.user(teachers[d][0]),"id");
   for(int s=0;s<4;s++)for(int seat=0;seat<4;seat++){
    String section=String.valueOf((char)('A'+s));
    int studentIndex=s*4+seat;
    String studentPin=studentPins[d][studentIndex];
    add(studentPin,studentNames[d][studentIndex],"student"+studentPin.substring(studentPin.length()-3)+"@example.com",mobileNumber(++mobileSuffix),"Campus@2026","STUDENT","APPROVED",departments[d],section,teacherIds[d]);
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

