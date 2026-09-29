package ir.weekend;

import static ir.weekend.Models.*;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class People {
  final JdbcTemplate db;
  final PasswordEncoder encoder;

  public People(JdbcTemplate db, PasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  public Person me(String username) {
    return db.queryForObject(
        "select * from app_users where username=? and active=true",
        (r, n) ->
            new Person(
                r.getObject("id", UUID.class),
                r.getString("username"),
                r.getString("full_name"),
                r.getString("personnel_code"),
                r.getString("role"),
                r.getObject("team_id", UUID.class)),
        username);
  }

  public List<Team> teams(Person p) {
    String sql =
        "select * from teams" + (p.role().equals("ADMIN") ? "" : " where id=?") + " order by name";
    Object[] params = p.role().equals("ADMIN") ? new Object[] {} : new Object[] {p.teamId()};
    return db.query(
        sql,
        (r, n) ->
            new Team(
                r.getObject("id", UUID.class),
                r.getString("name"),
                r.getString("coordinator_email")),
        params);
  }

  public void checkTeam(Person p, UUID team, boolean manager) {
    if (p.role().equals("ADMIN")) return;
    if (!p.teamId().equals(team) || (manager && !p.role().equals("MANAGER")))
      throw new AccessDeniedException("دسترسی ندارید");
  }

  public void admin(Person p) {
    if (!p.role().equals("ADMIN")) throw new AccessDeniedException("دسترسی مدیر سیستم لازم است");
  }

  @Transactional
  public UUID addTeam(Person p, TeamInput input) {
    admin(p);
    UUID id = UUID.randomUUID();
    db.update(
        "insert into teams(id,name,coordinator_email) values(?,?,?)",
        id,
        input.name().trim(),
        input.coordinatorEmail().trim());
    return id;
  }

  @Transactional
  public void updateTeam(Person p, UUID team, TeamInput input) {
    admin(p);
    db.queryForObject("select id from teams where id=? for update", UUID.class, team);
    db.update(
        "update teams set name=?,coordinator_email=? where id=?",
        input.name().trim(),
        input.coordinatorEmail().trim(),
        team);
  }

  @Transactional
  public UUID addUser(Person p, UserInput input) {
    admin(p);
    db.queryForObject("select id from teams where id=? for update", UUID.class, input.teamId());
    UUID id = UUID.randomUUID();
    db.update(
        "insert into app_users(id,username,password_hash,full_name,personnel_code,role,team_id)"
            + " values(?,?,?,?,?,?,?)",
        id,
        input.username(),
        encoder.encode(input.password()),
        input.fullName().trim(),
        input.personnelCode().trim(),
        input.role().name(),
        input.teamId());
    return id;
  }

  @Transactional
  public void changePassword(Person p, PasswordInput input) {
    String old =
        db.queryForObject(
            "select password_hash from app_users where id=? for update", String.class, p.id());
    if (!encoder.matches(input.currentPassword(), old))
      throw new IllegalArgumentException("رمز فعلی صحیح نیست");
    db.update(
        "update app_users set password_hash=? where id=?",
        encoder.encode(input.newPassword()),
        p.id());
  }
}
