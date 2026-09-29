package ir.weekend;

import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<?> invalid(IllegalArgumentException e) {
    return response(400, e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<?> conflict(IllegalStateException e) {
    return response(409, e.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> forbidden(AccessDeniedException e) {
    return response(403, "دسترسی به این عملیات ندارید");
  }

  @ExceptionHandler(EmptyResultDataAccessException.class)
  ResponseEntity<?> missing() {
    return response(404, "اطلاعات پیدا نشد");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> duplicate() {
    return response(409, "نام کاربری، کد پرسنلی یا نام تیم تکراری یا اطلاعات وابسته نامعتبر است");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation() {
    return response(400, "فیلدهای ورودی را بررسی کنید؛ رمز حداقل ۱۲ کاراکتر و ایمیل معتبر باشد");
  }

  private ResponseEntity<?> response(int status, String message) {
    return ResponseEntity.status(status).body(Map.of("message", message));
  }
}
