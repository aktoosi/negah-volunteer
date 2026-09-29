package ir.weekend;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MailWorker {
  final JdbcTemplate db;
  final JavaMailSender sender;
  final TransactionTemplate tx;
  final boolean enabled;
  final String from;

  public MailWorker(
      JdbcTemplate db,
      JavaMailSender sender,
      TransactionTemplate tx,
      @Value("${app.mail-enabled}") boolean enabled,
      @Value("${app.mail-from}") String from) {
    this.db = db;
    this.sender = sender;
    this.tx = tx;
    this.enabled = enabled;
    this.from = from;
  }

  // SMTP is outside the attendance transaction. Lock prevents concurrent workers sending the same
  // row.
  // SMTP cannot promise exactly-once: a crash after SMTP acceptance may lead to a duplicate on
  // retry.
  @Scheduled(fixedDelay = 15000)
  public void deliver() {
    if (!enabled) return;
    tx.executeWithoutResult(
        s -> {
          var rows =
              db.queryForList(
                  "select * from email_outbox where status='QUEUED' order by id limit 1 for update"
                      + " skip locked");
          if (rows.isEmpty()) return;
          var row = rows.get(0);
          UUID id = (UUID) row.get("id");
          try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo((String) row.get("recipient"));
            mail.setSubject((String) row.get("subject"));
            mail.setText((String) row.get("body"));
            sender.send(mail);
            db.update(
                "update email_outbox set"
                    + " status='SENT',attempts=attempts+1,sent_at=CURRENT_TIMESTAMP,last_error=null"
                    + " where id=?",
                id);
          } catch (org.springframework.mail.MailException ex) {
            db.update(
                "update email_outbox set status='FAILED',attempts=attempts+1,last_error=? where"
                    + " id=?",
                "ارسال ناموفق بود؛ تنظیمات و دسترسی SMTP را بررسی کنید.",
                id);
          }
        });
  }
}
