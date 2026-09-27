package edu.campus.flow;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
@Service
public class SmsService {
 @Value("${app.sms.sid}") String sid;
 @Value("${app.sms.token}") String token;
 @Value("${app.sms.from}") String from;
 boolean configured(){return !sid.isBlank()&&!token.isBlank()&&!from.isBlank();}
 public boolean send(String mobile,String message){
  if(!configured())return false;
  try {
   String body="To="+enc(mobile)+"&From="+enc(from)+"&Body="+enc(message);
   var req=HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/"+sid+"/Messages.json"))
    .timeout(Duration.ofSeconds(10)).header("Authorization","Basic "+Base64.getEncoder().encodeToString((sid+":"+token).getBytes(StandardCharsets.UTF_8)))
    .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
   int status=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(req,HttpResponse.BodyHandlers.discarding()).statusCode();
   return status>=200&&status<300;
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return false;}
 }
 String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
}
