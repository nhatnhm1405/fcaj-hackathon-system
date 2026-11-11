package com.seal.hackathon.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public void sendAccountApprovedEmail(String to, String fullName) {
        String safeName = escapeHtml(fullName);
        String subject = "[SEAL Hackathon] Account approved";
        String plainText = "Hello " + fullName + ",\n\n"
                + "Your account has been approved. You can now log in to the SEAL Hackathon system.\n\n"
                + "SEAL Hackathon Team";

        String html = buildEmailTemplate(
                "Approved",
                "Your account is ready",
                "Welcome to SEAL Hackathon, " + safeName + ".",
                "Your registration has been approved. You can now sign in, create or join a team, and start preparing for the competition.",
                "Sign in to SEAL Hackathon",
                frontendUrl + "/login",
                "#16a34a",
                "#dcfce7",
                "#14532d"
        );

        sendEmail(to, subject, plainText, html);
    }

    public void sendAccountRejectedEmail(String to, String fullName) {
        String safeName = escapeHtml(fullName);
        String subject = "[SEAL Hackathon] Account registration reviewed";
        String plainText = "Hello " + fullName + ",\n\n"
                + "Your account registration was reviewed but was not approved.\n"
                + "Please contact the event coordinator if you need more information.\n\n"
                + "SEAL Hackathon Team";

        String html = buildEmailTemplate(
                "Rejected",
                "Your registration was reviewed",
                "Hello " + safeName + ", your account was not approved.",
                "Your registration has been reviewed, but it cannot be approved at this time. If you believe this is a mistake, please contact the event coordinator for more information.",
                "Open SEAL Hackathon",
                frontendUrl + "/login",
                "#dc2626",
                "#fee2e2",
                "#7f1d1d"
        );

        sendEmail(to, subject, plainText, html);
    }

    public void sendPasswordResetOtpEmail(String to, String fullName, String otp, long expiresInMinutes) {
        String safeName = escapeHtml(fullName);
        String safeOtp = escapeHtml(otp);
        String subject = "[SEAL Hackathon] Password reset OTP";
        String plainText = "Hello " + fullName + ",\n\n"
                + "Your password reset OTP is: " + otp + "\n"
                + "This code will expire in " + expiresInMinutes + " minutes.\n"
                + "If you did not request this, please ignore this email.\n\n"
                + "SEAL Hackathon Team";

        String html = buildOtpEmailTemplate(
                safeName,
                safeOtp,
                expiresInMinutes
        );

        sendEmail(to, subject, plainText, html);
    }

    private void sendEmail(String to, String subject, String plainText, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            helper.setPriority(1);

            message.setHeader("Importance", "High");
            message.setHeader("Priority", "urgent");
            message.setHeader("X-Priority", "1");
            message.setHeader("X-MSMail-Priority", "High");

            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new MailSendException("Failed to build email message.", ex);
        }
    }

    private String buildEmailTemplate(String badge, String title, String greeting, String message,
                                      String actionLabel, String actionUrl, String accentColor,
                                      String softColor, String darkColor) {
        return """
                <!doctype html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;color:#111827;">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0;">
                        SEAL Hackathon account notification
                    </div>
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f3f4f6;padding:32px 12px;">
                        <tr>
                            <td align="center">
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#ffffff;border-radius:18px;overflow:hidden;border:1px solid #e5e7eb;">
                                    <tr>
                                        <td style="padding:0;background:%s;">
                                            <div style="height:8px;background:linear-gradient(90deg,%s,#2563eb,#7c3aed);"></div>
                                            <div style="padding:30px 32px;color:#ffffff;">
                                                <div style="font-size:13px;letter-spacing:0.12em;text-transform:uppercase;font-weight:700;opacity:0.9;">SEAL Hackathon</div>
                                                <div style="font-size:30px;line-height:1.2;font-weight:800;margin-top:10px;">%s</div>
                                                <div style="font-size:15px;line-height:1.6;margin-top:10px;opacity:0.92;">Software Engineering Arena Lab</div>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:32px;">
                                            <span style="display:inline-block;background:%s;color:%s;border-radius:999px;padding:8px 14px;font-size:13px;font-weight:700;">%s</span>
                                            <h1 style="font-size:24px;line-height:1.3;margin:22px 0 10px;color:#111827;">%s</h1>
                                            <p style="font-size:16px;line-height:1.7;margin:0 0 18px;color:#374151;">%s</p>
                                            <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:14px;padding:18px 20px;margin:22px 0;">
                                                <p style="font-size:15px;line-height:1.7;margin:0;color:#374151;">%s</p>
                                            </div>
                                            <div style="margin-top:26px;">
                                                <a href="%s" style="display:inline-block;background:%s;color:#ffffff;border-radius:12px;padding:13px 18px;font-size:15px;font-weight:700;text-decoration:none;">%s</a>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:22px 32px;background:#111827;color:#d1d5db;">
                                            <div style="font-size:14px;line-height:1.6;">This is an automated email from SEAL Hackathon. Please do not reply directly to this message.</div>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(
                darkColor,
                accentColor,
                title,
                softColor,
                darkColor,
                badge,
                greeting,
                message,
                "Keep this email for your registration records.",
                escapeHtml(actionUrl),
                accentColor,
                actionLabel
        );
    }

    private String buildOtpEmailTemplate(String safeName, String safeOtp, long expiresInMinutes) {
        return """
                <!doctype html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;color:#111827;">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0;">
                        SEAL Hackathon password reset code
                    </div>
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f3f4f6;padding:32px 12px;">
                        <tr>
                            <td align="center">
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#ffffff;border-radius:18px;overflow:hidden;border:1px solid #e5e7eb;">
                                    <tr>
                                        <td style="padding:0;background:#111827;">
                                            <div style="height:8px;background:linear-gradient(90deg,#22c55e,#2563eb,#7c3aed);"></div>
                                            <div style="padding:30px 32px;color:#ffffff;">
                                                <div style="font-size:13px;letter-spacing:0.12em;text-transform:uppercase;font-weight:700;opacity:0.9;">SEAL Hackathon</div>
                                                <div style="font-size:30px;line-height:1.2;font-weight:800;margin-top:10px;">Password reset</div>
                                                <div style="font-size:15px;line-height:1.6;margin-top:10px;opacity:0.92;">Use this one-time code to continue.</div>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:32px;">
                                            <span style="display:inline-block;background:#dbeafe;color:#1e3a8a;border-radius:999px;padding:8px 14px;font-size:13px;font-weight:700;">OTP Code</span>
                                            <h1 style="font-size:24px;line-height:1.3;margin:22px 0 10px;color:#111827;">Hello %s,</h1>
                                            <p style="font-size:16px;line-height:1.7;margin:0 0 18px;color:#374151;">Enter this code on the SEAL Hackathon reset password screen:</p>
                                            <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:14px;padding:22px;margin:22px 0;text-align:center;">
                                                <div style="font-size:34px;line-height:1.1;letter-spacing:0.35em;font-weight:800;color:#111827;font-family:Consolas,Monaco,monospace;">%s</div>
                                            </div>
                                            <p style="font-size:15px;line-height:1.7;margin:0;color:#374151;">This code expires in %d minutes and can be entered up to 10 times. If you did not request this, you can safely ignore this email.</p>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:22px 32px;background:#111827;color:#d1d5db;">
                                            <div style="font-size:14px;line-height:1.6;">This is an automated email from SEAL Hackathon. Please do not reply directly to this message.</div>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(safeName, safeOtp, expiresInMinutes);
    }

    private String escapeHtml(String value) {
        if (value == null || value.isBlank()) {
            return "there";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
