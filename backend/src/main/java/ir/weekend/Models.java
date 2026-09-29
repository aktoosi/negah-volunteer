package ir.weekend;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class Models {
  private Models() {}

  public record Person(
      UUID id, String username, String fullName, String personnelCode, String role, UUID teamId) {}

  public record Team(UUID id, String name, String coordinatorEmail) {}

  public record Member(
      UUID userId,
      String fullName,
      String personnelCode,
      UUID requestId,
      String thursdayStatus,
      String fridayStatus,
      String note,
      String reviewNote,
      String reviewedBy,
      Instant reviewedAt) {}

  public record Mail(String status, int attempts, String error) {}

  public record WeekView(
      UUID id, LocalDate thursday, String status, List<Member> members, Mail mail) {}

  public record RequestInput(boolean thursday, boolean friday, @Size(max = 500) String note) {}

  public record ReviewInput(
      @NotNull Decision thursday, @NotNull Decision friday, @Size(max = 500) String note) {}

  public enum Decision {
    APPROVED,
    REJECTED,
    NOT_ATTENDING
  }

  public record TeamInput(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Email @Size(max = 254) String coordinatorEmail) {}

  public record UserInput(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,80}") String username,
      @NotBlank @Size(min = 12, max = 64) String password,
      @NotBlank @Size(max = 100) String fullName,
      @NotBlank @Size(max = 40) String personnelCode,
      @NotNull Role role,
      @NotNull UUID teamId) {}

  public enum Role {
    ADMIN,
    MANAGER,
    EMPLOYEE
  }

  public record PasswordInput(
      @NotBlank String currentPassword, @NotBlank @Size(min = 12, max = 64) String newPassword) {}
}
