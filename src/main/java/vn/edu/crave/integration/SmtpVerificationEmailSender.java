package vn.edu.crave.integration;

import vn.edu.crave.config.EmailConfig;
import vn.edu.crave.exception.AccountException;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Date;
import java.util.Properties;

/** Jakarta Mail on the existing servlet stack; configuration is resolved only when sending. */
public final class SmtpVerificationEmailSender implements VerificationEmailSender {
    @Override
    public void sendCode(String email, String code, String purpose) {
        try {
            if (email == null || email.length() > 320 || email.contains("\r") || email.contains("\n")
                    || code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException();
            String action = switch (purpose) {
                case "REGISTER" -> "xác thực đăng ký tài khoản";
                case "PASSWORD_RESET" -> "đặt lại mật khẩu";
                case "PASSWORD_CHANGE" -> "đổi mật khẩu";
                default -> throw new IllegalArgumentException();
            };
            InternetAddress recipient = new InternetAddress(email, true);
            recipient.validate();
            if (recipient.getPersonal() != null || !recipient.getAddress().equals(email)) throw new IllegalArgumentException();
            EmailConfig.SmtpSettings config = EmailConfig.smtp();
            Properties properties = new Properties();
            properties.setProperty("mail.smtp.host", config.host());
            properties.setProperty("mail.smtp.port", Integer.toString(config.port()));
            properties.setProperty("mail.smtp.auth", Boolean.toString(!config.username().isEmpty()));
            properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(config.startTls()));
            properties.setProperty("mail.smtp.starttls.required", Boolean.toString(config.startTls()));
            properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
            properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
            properties.setProperty("mail.smtp.connectiontimeout", "10000");
            properties.setProperty("mail.smtp.timeout", "10000");
            properties.setProperty("mail.smtp.writetimeout", "10000");
            properties.setProperty("mail.smtp.quitwait", "false");
            properties.setProperty("mail.debug", "false");
            properties.setProperty("mail.debug.auth", "false");
            Session session = Session.getInstance(properties);
            session.setDebug(false);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.from(), "Crave", "UTF-8"));
            message.setRecipient(Message.RecipientType.TO, recipient);
            message.setSubject("Mã xác thực Crave", "UTF-8");
            message.setSentDate(new Date());
            message.setText("Mã để " + action + " của bạn là: " + code
                    + "\n\nMã có hiệu lực trong 10 phút và chỉ dùng được một lần."
                    + "\nKhông chia sẻ mã với bất kỳ ai. Nếu bạn không yêu cầu, hãy bỏ qua email này.", "UTF-8");
            message.saveChanges();
            try (Transport transport = session.getTransport("smtp")) {
                transport.connect(config.host(), config.port(), config.username().isEmpty() ? null : config.username(),
                        config.password().isEmpty() ? null : config.password());
                transport.sendMessage(message, message.getAllRecipients());
            }
        } catch (Exception failure) {
            // SMTP errors can include credentials, addresses and provider responses. Never attach the cause.
            throw new AccountException("EMAIL_UNAVAILABLE", "Không thể gửi mã xác thực. Vui lòng thử lại sau.", 503);
        }
    }
}
