package com.gambyte.notification;

import com.gambyte.config.GambyteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends emails (verification, password reset, receipts).
 *
 * <p>If no SMTP server is configured ({@code MAIL_HOST} empty, the normal case in development) the
 * email is written to the log instead, so links can be clicked from the console. In production
 * point MAIL_HOST at AWS SES SMTP (free for low volume) or any SMTP provider.
 *
 * <p>Sending is {@link Async}: a slow mail server never delays the HTTP response.
 */
@Service
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);

  private final ObjectProvider<JavaMailSender> sender;
  private final GambyteProperties props;
  private final String mailHost;

  public MailService(
      ObjectProvider<JavaMailSender> sender,
      GambyteProperties props,
      org.springframework.core.env.Environment env) {
    this.sender = sender;
    this.props = props;
    this.mailHost = env.getProperty("spring.mail.host", "");
  }

  @Async
  public void send(String to, String subject, String body) {
    if (mailHost.isBlank() || sender.getIfAvailable() == null) {
      log.info("EMAIL (not sent, no MAIL_HOST) to={} subject={}\n{}", to, subject, body);
      return;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(props.mail().from());
      message.setTo(to);
      message.setSubject(subject);
      message.setText(body);
      sender.getObject().send(message);
    } catch (RuntimeException e) {
      log.warn("Could not send email to {}: {}", to, e.getMessage());
    }
  }
}
