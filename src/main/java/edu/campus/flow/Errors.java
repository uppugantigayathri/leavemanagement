package edu.campus.flow;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataAccessException;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> business(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Request could not be completed.":e.getReason()));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){var f=e.getBindingResult().getFieldErrors().get(0);return ResponseEntity.badRequest().body(Map.of("message",f.getField()+": "+f.getDefaultMessage()));}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<?> database(DataAccessException e){return ResponseEntity.badRequest().body(Map.of("message","The record is unavailable or conflicts with an existing record. Refresh and try again."));}
}
