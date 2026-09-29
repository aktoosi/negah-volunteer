package ir.weekend;

import static ir.weekend.Models.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowTest {
  @Autowired AttendanceService service;
  @Autowired People people;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired TransactionTemplate tx;
  Person employee, manager, admin;
  LocalDate date;

  @BeforeEach
  void setup() {
    db.update("delete from email_outbox");
    db.update("delete from attendance_requests");
    db.update("delete from attendance_weeks");
    employee = people.me("member1");
    manager = people.me("manager");
    admin = people.me("admin");
    date = AttendanceService.today().with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
  }

  @Test
  void employeeCannotReadOtherMembersOrManage() {
    assertThat(service.view(employee, employee.teamId(), date).members()).hasSize(1);
    assertThatThrownBy(() -> service.finalizeWeek(employee, employee.teamId(), date))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> people.addTeam(employee, new TeamInput("forbidden", "x@example.test")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void crossTeamAccessIsDenied() {
    UUID other =
        people.addTeam(admin, new TeamInput("Other-" + UUID.randomUUID(), "other@example.test"));
    assertThatThrownBy(() -> service.view(manager, other, date))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.finalizeWeek(manager, other, date))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.retry(manager, other, date))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void submissionIsIdempotentAndPendingCanBeEdited() {
    service.submit(employee, date, new RequestInput(true, true, "first"));
    service.submit(employee, date, new RequestInput(false, true, "edited"));
    assertThat(db.queryForObject("select count(*) from attendance_requests", Integer.class))
        .isEqualTo(1);
    Member row = service.view(employee, employee.teamId(), date).members().get(0);
    assertThat(row.thursdayStatus()).isEqualTo("NOT_ATTENDING");
    assertThat(row.fridayStatus()).isEqualTo("PENDING");
  }

  @Test
  void finalizationRejectsPendingAndEmptyLists() {
    assertThatThrownBy(() -> service.finalizeWeek(manager, manager.teamId(), date))
        .isInstanceOf(IllegalStateException.class);
    service.submit(employee, date, new RequestInput(true, false, ""));
    assertThatThrownBy(() -> service.finalizeWeek(manager, manager.teamId(), date))
        .isInstanceOf(IllegalStateException.class);
    assertThat(db.queryForObject("select count(*) from email_outbox", Integer.class)).isZero();
  }

  @Test
  void reviewedRequestsLockAndFinalizationQueuesExactlyOneSnapshot() {
    approve();
    assertThatThrownBy(() -> service.submit(employee, date, new RequestInput(false, false, "")))
        .isInstanceOf(IllegalStateException.class);
    service.finalizeWeek(manager, manager.teamId(), date);
    service.finalizeWeek(manager, manager.teamId(), date);
    assertThat(db.queryForObject("select count(*) from email_outbox", Integer.class)).isEqualTo(1);
    assertThat(service.view(manager, manager.teamId(), date).status()).isEqualTo("FINALIZED");
    assertThatThrownBy(
            () -> service.submit(people.me("member2"), date, new RequestInput(true, true, "")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.csv(manager, manager.teamId(), date))
        .contains(employee.fullName())
        .doesNotContain("جمعه\r\n");
    String snapshot = db.queryForObject("select body from email_outbox", String.class);
    assertThat(snapshot).contains(employee.personnelCode()).contains(date.toString());
  }

  @Test
  void nonRequestedDayCannotBeApproved() {
    service.submit(employee, date, new RequestInput(true, false, ""));
    UUID request = service.view(employee, employee.teamId(), date).members().get(0).requestId();
    assertThatThrownBy(
            () ->
                service.review(
                    manager,
                    manager.teamId(),
                    date,
                    request,
                    new ReviewInput(Decision.APPROVED, Decision.APPROVED, "")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidWeekAndPastDateAreRejected() {
    assertThatThrownBy(
            () -> service.submit(employee, date.plusDays(1), new RequestInput(true, true, "")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.submit(employee, date.minusWeeks(1), new RequestInput(true, true, "")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void csrfAndAuthenticationAreRequired() throws Exception {
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    mvc.perform(
            put("/api/me/weeks/" + date)
                .with(user("member1"))
                .contentType("application/json")
                .content("{\"thursday\":true,\"friday\":false}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/me/weeks/" + date)
                .with(user("member1"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"thursday\":true,\"friday\":false}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/teams")
                .with(user("member1"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"bad\",\"coordinatorEmail\":\"c@example.test\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void actualLoginUsesStoredPassword() throws Exception {
    mvc.perform(
            post("/api/login").with(csrf()).param("username", "member1").param("password", "wrong"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/login")
                .with(csrf())
                .param("username", "member1")
                .param("password", "Testing-only-2026!"))
        .andExpect(status().isOk());
  }

  @Test
  void failedEmailDoesNotUndoFinalizationAndCanRetry() {
    approve();
    service.finalizeWeek(manager, manager.teamId(), date);
    JavaMailSender sender = mock(JavaMailSender.class);
    doThrow(new MailSendException("offline")).when(sender).send(any(SimpleMailMessage.class));
    MailWorker worker = new MailWorker(db, sender, tx, true, "test@example.test");
    worker.deliver();
    assertThat(service.view(manager, manager.teamId(), date).mail().status()).isEqualTo("FAILED");
    assertThat(service.view(manager, manager.teamId(), date).status()).isEqualTo("FINALIZED");
    service.retry(manager, manager.teamId(), date);
    reset(sender);
    worker.deliver();
    worker.deliver();
    verify(sender, times(1)).send(any(SimpleMailMessage.class));
    assertThat(service.view(manager, manager.teamId(), date).mail().attempts()).isEqualTo(2);
    assertThat(service.view(manager, manager.teamId(), date).mail().status()).isEqualTo("SENT");
  }

  @Test
  void concurrentFinalizeCreatesOneMail() throws Exception {
    approve();
    var executor = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      Callable<Void> task =
          () -> {
            start.await();
            service.finalizeWeek(manager, manager.teamId(), date);
            return null;
          };
      var a = executor.submit(task);
      var b = executor.submit(task);
      start.countDown();
      a.get(10, TimeUnit.SECONDS);
      b.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
    assertThat(db.queryForObject("select count(*) from email_outbox", Integer.class)).isEqualTo(1);
  }

  private void approve() {
    service.submit(employee, date, new RequestInput(true, false, "task"));
    UUID id = service.view(employee, employee.teamId(), date).members().get(0).requestId();
    service.review(
        manager,
        manager.teamId(),
        date,
        id,
        new ReviewInput(Decision.APPROVED, Decision.NOT_ATTENDING, "ok"));
  }
}
