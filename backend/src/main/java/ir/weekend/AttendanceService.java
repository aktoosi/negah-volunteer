package ir.weekend;

import static ir.weekend.Models.*;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
  private final JdbcTemplate db;
  private final People people;

  public AttendanceService(JdbcTemplate db, People people) {
    this.db = db;
    this.people = people;
  }

  public static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Tehran"));
  }

  private void dateCheck(LocalDate date) {
    if (date.getDayOfWeek() != DayOfWeek.THURSDAY)
      throw new IllegalArgumentException("تاریخ شروع باید پنجشنبه باشد");
  }

  private void writable(LocalDate date) {
    dateCheck(date);
    if (date.isBefore(today()))
      throw new IllegalArgumentException("مهلت تغییر این هفته تمام شده است");
    if (date.isAfter(today().plusWeeks(12)))
      throw new IllegalArgumentException("حداکثر تا ۱۲ هفته آینده قابل ثبت است");
  }

  // All mutations lock the team first: submission, review and finalization cannot race.
  private void lock(UUID team) {
    db.queryForObject("select id from teams where id=? for update", UUID.class, team);
  }

  private UUID openWeek(UUID team, LocalDate date) {
    List<UUID> ids =
        db.queryForList(
            "select id from attendance_weeks where team_id=? and thursday=?",
            UUID.class,
            team,
            date);
    if (ids.isEmpty()) {
      UUID id = UUID.randomUUID();
      db.update(
          "insert into attendance_weeks(id,team_id,thursday,status) values(?,?,?,'OPEN')",
          id,
          team,
          date);
      return id;
    }
    UUID id = ids.get(0);
    if (!"OPEN"
        .equals(
            db.queryForObject("select status from attendance_weeks where id=?", String.class, id)))
      throw new IllegalStateException("فهرست نهایی شده و قابل تغییر نیست");
    return id;
  }

  @Transactional(readOnly = true)
  public WeekView view(Person actor, UUID team, LocalDate date) {
    people.checkTeam(actor, team, false);
    dateCheck(date);
    var weeks =
        db.queryForList(
            "select id,status from attendance_weeks where team_id=? and thursday=?", team, date);
    UUID id = weeks.isEmpty() ? null : (UUID) weeks.get(0).get("id");
    String state = weeks.isEmpty() ? "OPEN" : (String) weeks.get(0).get("status");
    String sql =
        "select u.id,u.full_name,u.personnel_code,r.id"
            + " request_id,r.thursday_status,r.friday_status,r.note,r.review_note,reviewer.full_name"
            + " reviewed_by,r.reviewed_at from app_users u left join attendance_requests r on"
            + " r.user_id=u.id and r.week_id=? left join app_users reviewer on"
            + " reviewer.id=r.reviewed_by where u.team_id=? and u.active=true and u.role<>'ADMIN'";
    List<Object> params = new ArrayList<>();
    params.add(id);
    params.add(team);
    if (actor.role().equals("EMPLOYEE")) {
      sql += " and u.id=?";
      params.add(actor.id());
    }
    sql += " order by u.full_name";
    List<Member> members =
        db.query(
            sql,
            (r, n) ->
                new Member(
                    r.getObject("id", UUID.class),
                    r.getString("full_name"),
                    r.getString("personnel_code"),
                    r.getObject("request_id", UUID.class),
                    Objects.requireNonNullElse(r.getString("thursday_status"), "UNDECLARED"),
                    Objects.requireNonNullElse(r.getString("friday_status"), "UNDECLARED"),
                    Objects.requireNonNullElse(r.getString("note"), ""),
                    Objects.requireNonNullElse(r.getString("review_note"), ""),
                    r.getString("reviewed_by"),
                    r.getTimestamp("reviewed_at") == null
                        ? null
                        : r.getTimestamp("reviewed_at").toInstant()),
            params.toArray());
    List<Mail> mail =
        db.query(
            "select status,attempts,last_error from email_outbox where week_id=?",
            (r, n) -> new Mail(r.getString(1), r.getInt(2), r.getString(3)),
            id);
    return new WeekView(
        id,
        date,
        state,
        members,
        actor.role().equals("EMPLOYEE") || mail.isEmpty() ? null : mail.get(0));
  }

  @Transactional
  public void submit(Person actor, LocalDate date, RequestInput input) {
    if (actor.role().equals("ADMIN"))
      throw new IllegalArgumentException("برای ثبت حضور از حساب عضو تیم استفاده کنید");
    writable(date);
    lock(actor.teamId());
    UUID week = openWeek(actor.teamId(), date);
    var current =
        db.queryForList(
            "select * from attendance_requests where week_id=? and user_id=?", week, actor.id());
    if (!current.isEmpty() && current.get(0).get("reviewed_by") != null)
      throw new IllegalStateException("درخواست بررسی شده و قابل ویرایش نیست");
    String th = input.thursday() ? "PENDING" : "NOT_ATTENDING",
        fr = input.friday() ? "PENDING" : "NOT_ATTENDING",
        note = Objects.requireNonNullElse(input.note(), "");
    if (current.isEmpty())
      db.update(
          "insert into"
              + " attendance_requests(id,week_id,user_id,thursday_status,friday_status,note,updated_at)"
              + " values(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
          UUID.randomUUID(),
          week,
          actor.id(),
          th,
          fr,
          note);
    else
      db.update(
          "update attendance_requests set"
              + " thursday_status=?,friday_status=?,note=?,updated_at=CURRENT_TIMESTAMP where"
              + " week_id=? and user_id=?",
          th,
          fr,
          note,
          week,
          actor.id());
  }

  @Transactional
  public void review(Person actor, UUID team, LocalDate date, UUID request, ReviewInput input) {
    people.checkTeam(actor, team, true);
    writable(date);
    lock(team);
    UUID week = openWeek(team, date);
    var row =
        db.queryForMap("select * from attendance_requests where id=? and week_id=?", request, week);
    ensureDecision((String) row.get("thursday_status"), input.thursday());
    ensureDecision((String) row.get("friday_status"), input.friday());
    db.update(
        "update attendance_requests set"
            + " thursday_status=?,friday_status=?,review_note=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP"
            + " where id=?",
        input.thursday().name(),
        input.friday().name(),
        Objects.requireNonNullElse(input.note(), ""),
        actor.id(),
        request);
  }

  private void ensureDecision(String original, Decision decision) {
    if (original.equals("NOT_ATTENDING") != (decision == Decision.NOT_ATTENDING))
      throw new IllegalArgumentException("فقط روزهای درخواست‌شده قابل تأیید یا رد هستند");
  }

  @Transactional
  public void finalizeWeek(Person actor, UUID team, LocalDate date) {
    people.checkTeam(actor, team, true);
    writable(date);
    lock(team);
    if (db.queryForObject(
            "select count(*) from attendance_weeks where team_id=? and thursday=? and"
                + " status='FINALIZED'",
            Integer.class,
            team,
            date)
        > 0) return;
    UUID week = openWeek(team, date);
    if (db.queryForObject(
            "select count(*) from attendance_requests where week_id=? and"
                + " (thursday_status='PENDING' or friday_status='PENDING')",
            Integer.class,
            week)
        > 0) throw new IllegalStateException("ابتدا تمام درخواست‌های در انتظار را بررسی کنید");
    if (db.queryForObject(
            "select count(*) from attendance_requests where week_id=? and"
                + " (thursday_status='APPROVED' or friday_status='APPROVED')",
            Integer.class,
            week)
        == 0) throw new IllegalStateException("حداقل یک حضور تأییدشده لازم است");
    String teamName = db.queryForObject("select name from teams where id=?", String.class, team);
    String recipient =
        db.queryForObject("select coordinator_email from teams where id=?", String.class, team);
    StringBuilder body =
        new StringBuilder("با سلام و احترام\nفهرست حضور تأییدشده تیم ")
            .append(teamName)
            .append("\nپنجشنبه ")
            .append(date)
            .append(" و جمعه ")
            .append(date.plusDays(1))
            .append("\nشناسه فهرست: ")
            .append(week)
            .append("\n\n");
    for (Member m : view(actor, team, date).members()) {
      if (m.thursdayStatus().equals("APPROVED") || m.fridayStatus().equals("APPROVED"))
        body.append(m.fullName())
            .append(" | کد پرسنلی: ")
            .append(m.personnelCode())
            .append(" | پنجشنبه: ")
            .append(m.thursdayStatus().equals("APPROVED") ? "حضور" : "—")
            .append(" | جمعه: ")
            .append(m.fridayStatus().equals("APPROVED") ? "حضور" : "—")
            .append("\n");
    }
    body.append("\nتأییدکننده: ")
        .append(actor.fullName())
        .append("\nاین فهرست درخواست هماهنگی ورود است و به‌تنهایی مجوز شرکت محسوب نمی‌شود.");
    db.update(
        "update attendance_weeks set"
            + " status='FINALIZED',finalized_by=?,finalized_at=CURRENT_TIMESTAMP where id=?",
        actor.id(),
        week);
    db.update(
        "insert into email_outbox(id,week_id,recipient,subject,body,status)"
            + " values(?,?,?,?,?,'QUEUED')",
        UUID.randomUUID(),
        week,
        recipient,
        "فهرست حضور آخر هفته — " + teamName + " — " + date,
        body.toString());
  }

  @Transactional
  public void retry(Person actor, UUID team, LocalDate date) {
    people.checkTeam(actor, team, true);
    lock(team);
    int count =
        db.update(
            "update email_outbox set status='QUEUED',last_error=null where status='FAILED' and"
                + " week_id in (select id from attendance_weeks where team_id=? and thursday=?)",
            team,
            date);
    if (count == 0) throw new IllegalStateException("ایمیل ناموفق برای تلاش مجدد وجود ندارد");
  }

  public String csv(Person actor, UUID team, LocalDate date) {
    people.checkTeam(actor, team, true);
    WeekView week = view(actor, team, date);
    if (!week.status().equals("FINALIZED"))
      throw new IllegalStateException("ابتدا فهرست را نهایی کنید");
    StringBuilder out = new StringBuilder("\ufeffنام,کد پرسنلی,تاریخ,روز\r\n");
    for (Member m : week.members()) {
      if (m.thursdayStatus().equals("APPROVED"))
        out.append(cell(m.fullName()))
            .append(',')
            .append(cell(m.personnelCode()))
            .append(',')
            .append(date)
            .append(",پنجشنبه\r\n");
      if (m.fridayStatus().equals("APPROVED"))
        out.append(cell(m.fullName()))
            .append(',')
            .append(cell(m.personnelCode()))
            .append(',')
            .append(date.plusDays(1))
            .append(",جمعه\r\n");
    }
    return out.toString();
  }

  private String cell(String s) {
    if (s.matches("^[\\s]*[=+@-].*")) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}
