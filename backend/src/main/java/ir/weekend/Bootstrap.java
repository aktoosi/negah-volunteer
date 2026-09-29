package ir.weekend;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements ApplicationRunner {
  final JdbcTemplate db;
  final PasswordEncoder encoder;
  final String password;
  final boolean demo;

  public Bootstrap(
      JdbcTemplate db,
      PasswordEncoder encoder,
      @Value("${app.bootstrap-password}") String password,
      @Value("${app.demo}") boolean demo) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
    this.demo = demo;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (db.queryForObject("select count(*) from app_users", Long.class) > 0) return;
    if (password.length() < 12)
      throw new IllegalStateException(
          "Set BOOTSTRAP_PASSWORD (at least 12 characters) for first start");
    UUID team = UUID.randomUUID();
    db.update(
        "insert into teams(id,name,coordinator_email) values(?,?,?)",
        team,
        "تیم توسعه",
        "coordinator@example.test");
    insert("admin", "مدیر سیستم", "A001", "ADMIN", team, password);
    if (demo) {
      insert("manager", "مدیر تیم", "M001", "MANAGER", team, password);
      for (int i = 1; i <= 9; i++)
        insert("member" + i, "همکار آزمایشی " + i, "E00" + i, "EMPLOYEE", team, password);
    }
  }

  private void insert(String name, String full, String code, String role, UUID team, String pass) {
    db.update(
        "insert into app_users(id,username,password_hash,full_name,personnel_code,role,team_id)"
            + " values(?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        name,
        encoder.encode(pass),
        full,
        code,
        role,
        team);
  }
}
