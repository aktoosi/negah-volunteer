package ir.weekend;

import static ir.weekend.Models.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  final People people;
  final AttendanceService service;

  public ApiController(People people, AttendanceService service) {
    this.people = people;
    this.service = service;
  }

  private Person me(Principal p) {
    return people.me(p.getName());
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  public Person profile(Principal p) {
    return me(p);
  }

  @GetMapping("/teams")
  public List<Team> teams(Principal p) {
    return people.teams(me(p));
  }

  @PostMapping("/teams")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, UUID> team(Principal p, @Valid @RequestBody TeamInput body) {
    return Map.of("id", people.addTeam(me(p), body));
  }

  @PutMapping("/teams/{team}")
  public void updateTeam(Principal p, @PathVariable UUID team, @Valid @RequestBody TeamInput body) {
    people.updateTeam(me(p), team, body);
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, UUID> user(Principal p, @Valid @RequestBody UserInput body) {
    return Map.of("id", people.addUser(me(p), body));
  }

  @PutMapping("/me/password")
  public void password(Principal p, @Valid @RequestBody PasswordInput body) {
    people.changePassword(me(p), body);
  }

  @GetMapping("/teams/{team}/weeks/{date}")
  public WeekView week(Principal p, @PathVariable UUID team, @PathVariable LocalDate date) {
    return service.view(me(p), team, date);
  }

  @PutMapping("/me/weeks/{date}")
  public void submit(
      Principal p, @PathVariable LocalDate date, @Valid @RequestBody RequestInput body) {
    service.submit(me(p), date, body);
  }

  @PutMapping("/teams/{team}/weeks/{date}/requests/{request}")
  public void review(
      Principal p,
      @PathVariable UUID team,
      @PathVariable LocalDate date,
      @PathVariable UUID request,
      @Valid @RequestBody ReviewInput body) {
    service.review(me(p), team, date, request, body);
  }

  @PostMapping("/teams/{team}/weeks/{date}/finalize")
  public void finalizeWeek(Principal p, @PathVariable UUID team, @PathVariable LocalDate date) {
    service.finalizeWeek(me(p), team, date);
  }

  @PostMapping("/teams/{team}/weeks/{date}/retry-email")
  public void retry(Principal p, @PathVariable UUID team, @PathVariable LocalDate date) {
    service.retry(me(p), team, date);
  }

  @GetMapping("/teams/{team}/weeks/{date}/export.csv")
  public ResponseEntity<String> csv(
      Principal p, @PathVariable UUID team, @PathVariable LocalDate date) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance-" + date + ".csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(service.csv(me(p), team, date));
  }
}
