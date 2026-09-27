package edu.campus.flow;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
@Component @EnableScheduling
public class SmsDispatcher {
 private final JdbcTemplate db;private final SmsService sms;
 SmsDispatcher(JdbcTemplate db,SmsService sms){this.db=db;this.sms=sms;}
 @Scheduled(fixedDelay=60000,initialDelay=30000)
 public void deliver(){
  if(!sms.configured())return;
  for(var row:db.queryForList("SELECT s.*,u.mobile FROM sms_outbox s JOIN users u ON u.id=s.user_id WHERE s.status='QUEUED' AND s.attempts<3 ORDER BY s.id LIMIT 20")){
   boolean sent=sms.send((String)row.get("mobile"),(String)row.get("message"));
   int attempts=((Number)row.get("attempts")).intValue()+1;
   db.update("UPDATE sms_outbox SET attempts=?,status=? WHERE id=?",attempts,sent?"SENT":attempts>=3?"FAILED":"QUEUED",row.get("id"));
  }
 }
}
