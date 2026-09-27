package edu.campus.flow;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("demo") @Transactional
class PortalServiceTest {
 @Autowired PortalService svc;
 @Autowired Api api;
 Map<String,Object> student(){return svc.user("24093-CM-228");}
 Map<String,Object> teacher(){return svc.user("FAC-CM-101");}
 Map<String,Object> hod(){return svc.user("HOD-CM-001");}
 @Test void attendanceCalculatedFromStoredRecords(){assertEquals(87.5,svc.attendance(PortalService.id(student(),"id")));}
 @Test void studentCannotSelfApproveRegistration(){var pending=svc.user("24093-CM-206");assertThrows(ResponseStatusException.class,()->svc.reviewRegistration(pending,PortalService.id(pending,"id"),new Api.Decision("APPROVE","Self approval")));}
 @Test void teacherApprovesOnlyStudentsAndHodApprovesTeacher(){
  var s=svc.user("24093-CM-206");svc.reviewRegistration(teacher(),PortalService.id(s,"id"),new Api.Decision("APPROVE","PIN verified"));assertEquals("APPROVED",svc.user("24093-CM-206").get("status"));
  var t=svc.user("FAC-CM-102");assertThrows(ResponseStatusException.class,()->svc.reviewRegistration(teacher(),PortalService.id(t,"id"),new Api.Decision("APPROVE","Verified")));
  svc.reviewRegistration(hod(),PortalService.id(t,"id"),new Api.Decision("APPROVE","Faculty verified"));assertEquals("APPROVED",svc.user("FAC-CM-102").get("status"));
 }
 @Test void overlapIsRejected(){assertThrows(ResponseStatusException.class,()->svc.apply(student(),new Api.LeaveForm(LocalDate.now().plusDays(2),LocalDate.now().plusDays(4),"Personal","Overlapping request test")));}
 @Test void longLeaveGoesToHodAndTeacherCannotApprove(){
  svc.apply(student(),new Api.LeaveForm(LocalDate.now().plusDays(15),LocalDate.now().plusDays(19),"Family","A family gathering outside town"));
  var l=svc.database().queryForMap("SELECT * FROM leaves WHERE user_id=? ORDER BY id DESC LIMIT 1",student().get("id"));assertEquals("PENDING_HOD",l.get("status"));
  assertThrows(ResponseStatusException.class,()->svc.decide(teacher(),PortalService.id(l,"id"),new Api.Decision("APPROVE","Looks fine")));
  svc.decide(hod(),PortalService.id(l,"id"),new Api.Decision("APPROVE","Reviewed supporting information"));
  assertEquals("APPROVED",svc.database().queryForObject("SELECT status FROM leaves WHERE id=?",String.class,l.get("id")));
 }
 @Test void decisionsAreFinalAndAudited(){var l=svc.database().queryForMap("SELECT * FROM leaves WHERE status='PENDING_TEACHER' LIMIT 1");long lid=PortalService.id(l,"id");svc.decide(teacher(),lid,new Api.Decision("APPROVE","Approved by mentor"));assertEquals(1,svc.database().queryForObject("SELECT COUNT(*) FROM leave_events WHERE leave_id=?",Integer.class,lid));assertThrows(ResponseStatusException.class,()->svc.decide(teacher(),lid,new Api.Decision("REJECT","Second review")));}
 @Test void studentsCannotMarkAttendance(){assertThrows(ResponseStatusException.class,()->svc.markAttendance(student(),new Api.AttendanceForm(LocalDate.now(),List.of(new Api.AttendanceEntry(PortalService.id(student(),"id"),"PRESENT")))));}
 @Test void attendanceUpsertAndLeaveValidation(){long uid=PortalService.id(student(),"id");svc.markAttendance(teacher(),new Api.AttendanceForm(LocalDate.now(),List.of(new Api.AttendanceEntry(uid,"PRESENT"))));svc.markAttendance(teacher(),new Api.AttendanceForm(LocalDate.now(),List.of(new Api.AttendanceEntry(uid,"ABSENT"))));assertEquals(1,svc.database().queryForObject("SELECT COUNT(*) FROM attendance WHERE student_id=? AND attendance_date=?",Integer.class,uid,LocalDate.now()));assertThrows(ResponseStatusException.class,()->svc.markAttendance(teacher(),new Api.AttendanceForm(LocalDate.now(),List.of(new Api.AttendanceEntry(uid,"LEAVE")))));}
 @Test void registrationHashesPasswordsAndWaitsForApproval(){svc.register(new Api.Register("24093-CM-299","New Student","+919000000099","SafePassword123","STUDENT","Computer Engineering",PortalService.id(teacher(),"id")));var u=svc.user("24093-CM-299");assertEquals("PENDING",u.get("status"));assertNotEquals("SafePassword123",u.get("password"));assertTrue(svc.passwordEncoder().matches("SafePassword123",u.get("password").toString()));}
 @Test void missingAttendanceRoutesToHod(){svc.database().update("DELETE FROM attendance WHERE student_id=?",student().get("id"));svc.apply(student(),new Api.LeaveForm(LocalDate.now().plusDays(20),LocalDate.now().plusDays(20),"Personal","Personal appointment scheduled"));assertEquals("PENDING_HOD",svc.database().queryForObject("SELECT status FROM leaves WHERE user_id=? ORDER BY id DESC LIMIT 1",String.class,student().get("id")));}
 @Test void calendarDatesAreNotConvertedToUtcInstants(){var dashboard=svc.dashboard(student());var leaves=(List<?>)dashboard.get("leaves");var first=(Map<?,?>)leaves.get(0);assertEquals(LocalDate.now().plusDays(2).toString(),first.get("start_date"));}
 @Test void passwordResetCodeIsSingleUse(){long uid=PortalService.id(student(),"id");svc.database().update("INSERT INTO reset_codes(user_id,code_hash,expires_at) VALUES (?,?,?)",uid,svc.passwordEncoder().encode("123456"),java.time.LocalDateTime.now().plusMinutes(5));api.reset(new Api.Reset("24093-CM-228","123456","ChangedPassword123"));assertTrue(svc.passwordEncoder().matches("ChangedPassword123",svc.user("24093-CM-228").get("password").toString()));assertThrows(ResponseStatusException.class,()->api.reset(new Api.Reset("24093-CM-228","123456","AnotherPassword123")));}
 @Test void resetLocksAfterFiveWrongAttempts(){long uid=PortalService.id(student(),"id");svc.database().update("INSERT INTO reset_codes(user_id,code_hash,expires_at) VALUES (?,?,?)",uid,svc.passwordEncoder().encode("123456"),java.time.LocalDateTime.now().plusMinutes(5));for(int i=0;i<5;i++)assertThrows(ResponseStatusException.class,()->api.reset(new Api.Reset("24093-CM-228","000000","ChangedPassword123")));assertThrows(ResponseStatusException.class,()->api.reset(new Api.Reset("24093-CM-228","123456","ChangedPassword123")));}
}
