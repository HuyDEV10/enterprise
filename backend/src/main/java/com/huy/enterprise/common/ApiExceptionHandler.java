package com.huy.enterprise.common;
import jakarta.servlet.http.HttpServletRequest; import org.springframework.dao.DataIntegrityViolationException; import org.springframework.http.*; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*; import java.time.OffsetDateTime; import java.util.*; import java.util.stream.Collectors;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(ResourceNotFoundException.class) ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException e,HttpServletRequest r){return response(HttpStatus.NOT_FOUND,e.getMessage(),r,Map.of());}
 @ExceptionHandler({BusinessException.class,IllegalArgumentException.class}) ResponseEntity<ApiErrorResponse> badRequest(RuntimeException e,HttpServletRequest r){return response(HttpStatus.BAD_REQUEST,e.getMessage(),r,Map.of());}
 @ExceptionHandler({ConflictException.class,DataIntegrityViolationException.class}) ResponseEntity<ApiErrorResponse> conflict(Exception e,HttpServletRequest r){return response(HttpStatus.CONFLICT,e instanceof ConflictException?e.getMessage():"Data conflicts with an existing record",r,Map.of());}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException e,HttpServletRequest r){var errors=e.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(x->x.getField(),x->Objects.toString(x.getDefaultMessage(),"Invalid value"),(a,b)->a,LinkedHashMap::new));return response(HttpStatus.BAD_REQUEST,"Request validation failed",r,errors);}
 @ExceptionHandler(Exception.class) ResponseEntity<ApiErrorResponse> unexpected(Exception e,HttpServletRequest r){return response(HttpStatus.INTERNAL_SERVER_ERROR,"An unexpected error occurred",r,Map.of());}
 private ResponseEntity<ApiErrorResponse> response(HttpStatus s,String m,HttpServletRequest r,Map<String,String> errors){return ResponseEntity.status(s).body(new ApiErrorResponse(OffsetDateTime.now(),s.value(),s.getReasonPhrase(),m,r.getRequestURI(),errors));}
}
