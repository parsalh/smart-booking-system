package com.hua.smartbooking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class EmailService {

    private final RestClient restClient;
    private final String fromEmail;
    private final String baseUrl;

    public EmailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from-email}") String fromEmail,
            @Value("${app.base-url}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.fromEmail = fromEmail;
        this.baseUrl = baseUrl;
    }

    public void sendInvitationEmail(String toEmail, String organizerName) {
        String html = """
            <!DOCTYPE html>
            <html>
            <body style="margin:0; padding:0; background-color:#f8fafc; font-family:'Inter', ui-sans-serif, system-ui, sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f8fafc; padding:40px 0;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="background-color:#ffffff; border-radius:24px; overflow:hidden; box-shadow:0 4px 20px rgba(0,0,0,0.06);">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#2563eb,#10b981); padding:32px 40px;">
                                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                                            <tr>
                                                <td style="width:40px; height:40px; background-color:#ffffff; border-radius:12px; text-align:center; vertical-align:middle; font-size:20px;">📅</td>
                                                <td style="padding-left:12px; color:#ffffff; font-size:18px; font-weight:700; letter-spacing:-0.02em;">SmartBooking</td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:40px;">
                                        <p style="margin:0 0 8px 0; font-size:13px; font-weight:700; color:#2563eb; text-transform:uppercase; letter-spacing:0.08em;">You're invited to SmartBooking</p>
                                        <h1 style="margin:0 0 20px 0; font-size:24px; font-weight:800; color:#0f172a; letter-spacing:-0.02em;">%s wants to schedule a meeting with you</h1>
                                        <p style="margin:0 0 24px 0; font-size:15px; line-height:1.6; color:#475569;">
                                            You'll get a separate Google Calendar invite once the meeting is confirmed — no account needed for that. But if you sign up, here's what changes:
                                        </p>
                                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 28px 0;">
                                            <tr>
                                                <td style="width:28px; vertical-align:top; padding:0 0 14px 0;"><img src="%s/images/icons/refresh-cw.png" width="18" height="18" alt="" style="display:block;"></td>
                                                <td style="padding:0 0 14px 0; font-size:14px; line-height:1.5; color:#334155;"><strong style="color:#0f172a;">No more "does this time work for you?" emails</strong> — your calendar syncs automatically, so people stop guessing.</td>
                                            </tr>
                                            <tr>
                                                <td style="width:28px; vertical-align:top; padding:0 0 14px 0;"><img src="%s/images/icons/calendar-plus.png" width="18" height="18" alt="" style="display:block;"></td>
                                                <td style="padding:0 0 14px 0; font-size:14px; line-height:1.5; color:#334155;"><strong style="color:#0f172a;">Book your own meetings and rooms</strong> — find a room and a time that works for everyone in a few clicks.</td>
                                            </tr>
                                            <tr>
                                                <td style="width:28px; vertical-align:top;"><img src="%s/images/icons/bell-ring.png" width="18" height="18" alt="" style="display:block;"></td>
                                                <td style="font-size:14px; line-height:1.5; color:#334155;"><strong style="color:#0f172a;">One place for every invite</strong> — accept, decline, or see who else is coming, without digging through your inbox.</td>
                                            </tr>
                                        </table>
                                        <table role="presentation" cellpadding="0" cellspacing="0">
                                            <tr>
                                                <td style="border-radius:14px; background:linear-gradient(135deg,#2563eb,#10b981);">
                                                    <a href="%s/login" style="display:inline-block; padding:14px 32px; font-size:15px; font-weight:700; color:#ffffff; text-decoration:none;">Sign up for SmartBooking</a>
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:32px 0 0 0; font-size:12px; line-height:1.6; color:#94a3b8;">
                                            If you weren't expecting this invitation, you can safely ignore this email.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(organizerName, baseUrl, baseUrl, baseUrl, baseUrl);

        Map<String, Object> payload = Map.of(
                "from", fromEmail,
                "to", toEmail,
                "subject", organizerName + " invited you to SmartBooking",
                "html", html
        );

        restClient.post()
                .uri("/emails")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }



}