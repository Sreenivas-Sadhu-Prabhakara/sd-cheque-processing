package com.bank.bian.chequeprocessing.domain;

import com.bank.bian.chequeprocessing.events.DomainEvent;
import com.bank.bian.chequeprocessing.events.EventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Business rules for Cheque Processing (the "check clearance" service domain).
 *
 *  - Lodgement validates the instrument: positive amount, ISO currency,
 *    a plausible cheque number (6+ digits), and drawer != beneficiary
 *    (you can't deposit a cheque into the account it's drawn on).
 *  - The state machine is strict — every transition checks the source state:
 *      LODGED → PRESENTED → CLEARED | RETURNED ;  LODGED → STOPPED
 *  - A stop order is honored ONLY before presentment. Once presented to the
 *    clearing house, the cheque is out of the bank's hands: stop → 409.
 *  - Return requires a reason (insufficient funds, signature mismatch, …) —
 *    a return without a reason is operationally useless downstream.
 *  - cheque.cleared carries the beneficiary credit instruction: the flagship
 *    payments choreography (consumed by Current/Savings Account as a
 *    cheque-credit posting; HTTP call today, Kafka consumer when live).
 */
@Service
public class ChequeProcessingService {

    public static final String TOPIC_CHEQUES = "bian.cheques.lifecycle";

    private final ChequeRepository repository;
    private final EventPublisher events;
    private final Clock clock;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Autowired
    public ChequeProcessingService(ChequeRepository repository, EventPublisher events) {
        this(repository, events, Clock.systemUTC());
    }

    public ChequeProcessingService(ChequeRepository repository, EventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    // ── lodgement (Initiate) ─────────────────────────────────────────────────

    public Cheque lodge(String chequeNumber, String drawerAccountRef,
                        String beneficiaryAccountRef, long amountMinor, String currency) {
        if (amountMinor <= 0) {
            throw DomainException.invalid("AMOUNT_NOT_POSITIVE", "amountMinor must be > 0");
        }
        if (chequeNumber == null || !chequeNumber.matches("\\d{6,}")) {
            throw DomainException.invalid("CHEQUE_NUMBER_INVALID",
                    "chequeNumber must be at least 6 digits");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw DomainException.invalid("CURRENCY_INVALID", "currency must be an ISO 4217 code");
        }
        if (drawerAccountRef == null || drawerAccountRef.isBlank()
                || beneficiaryAccountRef == null || beneficiaryAccountRef.isBlank()) {
            throw DomainException.invalid("ACCOUNT_REFS_REQUIRED",
                    "drawerAccountRef and beneficiaryAccountRef are required");
        }
        if (drawerAccountRef.equals(beneficiaryAccountRef)) {
            throw DomainException.invalid("SELF_DEPOSIT",
                    "a cheque cannot be deposited into the account it is drawn on");
        }

        Cheque cheque = Cheque.lodge("CHQ-" + UUID.randomUUID(), chequeNumber,
                drawerAccountRef, beneficiaryAccountRef, amountMinor, currency, clock.instant());
        repository.save(cheque);

        // High-value lodgements are a fraud signal — this is part of the fraud flagship feed.
        events.publish(DomainEvent.of(TOPIC_CHEQUES, "cheque.lodged", Map.of(
                "chequeId", cheque.getChequeId(),
                "chequeNumber", chequeNumber,
                "drawerAccountRef", drawerAccountRef,
                "beneficiaryAccountRef", beneficiaryAccountRef,
                "amountMinor", amountMinor,
                "currency", currency)));
        return cheque;
    }

    // ── clearing lifecycle ───────────────────────────────────────────────────

    /** Present the cheque to the clearing house. LODGED → PRESENTED. */
    public Cheque present(String chequeId) {
        return transition(chequeId, Cheque.Status.LODGED, "present", cheque -> {
            cheque.setStatus(Cheque.Status.PRESENTED);
            cheque.setPresentedAt(clock.instant());
            events.publish(DomainEvent.of(TOPIC_CHEQUES, "cheque.presented",
                    Map.of("chequeId", chequeId)));
        });
    }

    /** Clearing house honored the cheque. PRESENTED → CLEARED; credit the beneficiary. */
    public Cheque clear(String chequeId) {
        return transition(chequeId, Cheque.Status.PRESENTED, "clear", cheque -> {
            cheque.setStatus(Cheque.Status.CLEARED);
            cheque.setSettledAt(clock.instant());
            // The beneficiary credit instruction — Current/Savings Account act on this.
            events.publish(DomainEvent.of(TOPIC_CHEQUES, "cheque.cleared", Map.of(
                    "chequeId", chequeId,
                    "beneficiaryAccountRef", cheque.getBeneficiaryAccountRef(),
                    "amountMinor", cheque.getAmountMinor(),
                    "currency", cheque.getCurrency(),
                    "reference", "cheque " + cheque.getChequeNumber())));
        });
    }

    /** Clearing house bounced the cheque. PRESENTED → RETURNED. Reason mandatory. */
    public Cheque returnCheque(String chequeId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.invalid("RETURN_REASON_REQUIRED",
                    "a return reason is required (e.g. insufficient-funds, signature-mismatch)");
        }
        return transition(chequeId, Cheque.Status.PRESENTED, "return", cheque -> {
            cheque.setStatus(Cheque.Status.RETURNED);
            cheque.setReturnReason(reason);
            cheque.setSettledAt(clock.instant());
            events.publish(DomainEvent.of(TOPIC_CHEQUES, "cheque.returned", Map.of(
                    "chequeId", chequeId,
                    "reason", reason,
                    "beneficiaryAccountRef", cheque.getBeneficiaryAccountRef())));
        });
    }

    /** Drawer's stop order. Honored only BEFORE presentment: LODGED → STOPPED. */
    public Cheque stop(String chequeId) {
        return withLock(chequeId, cheque -> {
            if (cheque.getStatus() == Cheque.Status.PRESENTED) {
                throw DomainException.rule("ALREADY_PRESENTED",
                        "cheque is with the clearing house; a stop order can no longer be honored");
            }
            requireStatus(cheque, Cheque.Status.LODGED, "stop");
            cheque.setStatus(Cheque.Status.STOPPED);
            cheque.setSettledAt(clock.instant());
            repository.save(cheque);
            events.publish(DomainEvent.of(TOPIC_CHEQUES, "cheque.stopped",
                    Map.of("chequeId", chequeId)));
            return cheque;
        });
    }

    // ── queries ──────────────────────────────────────────────────────────────

    public Cheque retrieve(String chequeId) {
        return repository.findById(chequeId)
                .orElseThrow(() -> DomainException.notFound("CHEQUE_UNKNOWN", "no cheque " + chequeId));
    }

    public Collection<Cheque> list() {
        return repository.findAll();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Cheque transition(String chequeId, Cheque.Status required, String action,
                              java.util.function.Consumer<Cheque> apply) {
        return withLock(chequeId, cheque -> {
            requireStatus(cheque, required, action);
            apply.accept(cheque);
            repository.save(cheque);
            return cheque;
        });
    }

    private void requireStatus(Cheque cheque, Cheque.Status expected, String action) {
        if (cheque.getStatus() != expected) {
            throw DomainException.rule("ILLEGAL_TRANSITION",
                    "'" + action + "' requires status " + expected
                            + " (cheque is " + cheque.getStatus() + ")");
        }
    }

    private <T> T withLock(String chequeId, java.util.function.Function<Cheque, T> body) {
        ReentrantLock lock = locks.computeIfAbsent(chequeId, k -> new ReentrantLock());
        lock.lock();
        try {
            return body.apply(retrieve(chequeId));
        } finally {
            lock.unlock();
        }
    }
}
