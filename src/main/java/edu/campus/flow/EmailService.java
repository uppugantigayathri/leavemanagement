package edu.campus.flow;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
 private final JavaMailSender sender;
 @Value("${app.mail.enabled:false}") private boolean enabled;
 @Value("${app.mail.from:}") private String from;
 @Value("${spring.mail.username:}") private String username;
 @Value("${spring.mail.password:}") private String password;

 EmailService(JavaMailSender sender){this.sender=sender;}

 public boolean configured(){return enabled&&!from.isBlank()&&!username.isBlank()&&!password.isBlank();}

 public boolean send(String to,String subject,String body){
  if(!configured()||to==null||to.isBlank())return false;
  try{
   var message=new SimpleMailMessage();message.setFrom(from);message.setTo(to);
   message.setSubject(subject);message.setText(body);sender.send(message);return true;
  }catch(RuntimeException ex){return false;}
 }
}
