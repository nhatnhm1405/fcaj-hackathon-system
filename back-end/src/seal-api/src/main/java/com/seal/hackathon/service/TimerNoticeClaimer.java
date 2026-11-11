package com.seal.hackathon.service;

import com.seal.hackathon.entity.RoundTimerNotice;
import com.seal.hackathon.repository.RoundTimerNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically claims a timer milestone mark so it is fanned out exactly once.
 *
 * Runs in its OWN transaction (REQUIRES_NEW): if two concurrent reads race to
 * claim the same mark, the loser's unique-constraint violation is rolled back in
 * isolation and reported as {@code false} — it never poisons the caller's
 * (read) transaction. Kept in a separate bean so the proxy actually applies the
 * new-transaction boundary (self-invocation would bypass it).
 */
@Service
@RequiredArgsConstructor
public class TimerNoticeClaimer {

    private final RoundTimerNoticeRepository noticeRepository;

    /** @return true only for the single caller that wins the claim. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Integer roundId, String phase, String milestoneKey) {
        if (noticeRepository.existsByRoundIdAndPhaseAndMilestoneKey(roundId, phase, milestoneKey)) {
            return false;
        }
        try {
            noticeRepository.saveAndFlush(RoundTimerNotice.builder()
                    .roundId(roundId)
                    .phase(phase)
                    .milestoneKey(milestoneKey)
                    .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            return false; // another reader claimed it a moment earlier
        }
    }
}
