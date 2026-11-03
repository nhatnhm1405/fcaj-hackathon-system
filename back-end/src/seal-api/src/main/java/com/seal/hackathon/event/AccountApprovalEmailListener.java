package com.seal.hackathon.event;

import com.seal.hackathon.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountApprovalEmailListener {

    private final EmailService emailService;

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAccountApprovalEmail(AccountApprovalEmailEvent event) {
        try {
            if (event.approved()) {
                emailService.sendAccountApprovedEmail(event.email(), event.fullName());
            } else {
                emailService.sendAccountRejectedEmail(event.email(), event.fullName());
            }
        } catch (MailException ex) {
            log.error("Failed to send account approval email to {}", event.email(), ex);
        }
    }
}
