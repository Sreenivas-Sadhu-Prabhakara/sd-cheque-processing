package com.bank.bian.chequeprocessing.domain;

import java.time.Instant;

/**
 * Control record made real: "Cheque Transaction Procedure" — one cheque
 * moving through the clearing lifecycle.
 *
 * State machine (enforced in ChequeProcessingService):
 *
 *   LODGED ──present──▶ PRESENTED ──clear───▶ CLEARED   (terminal; beneficiary credited)
 *     │                     └──────return──▶ RETURNED  (terminal; with reason)
 *     └──stop──▶ STOPPED  (terminal; ONLY before presentment — once a cheque
 *                          is with the clearing house it cannot be stopped)
 */
public class Cheque {

    public enum Status { LODGED, PRESENTED, CLEARED, RETURNED, STOPPED }

    private String chequeId;
    private String chequeNumber;
    private String drawerAccountRef;       // account the money comes FROM
    private String beneficiaryAccountRef;  // account to credit on clearance
    private long amountMinor;
    private String currency;
    private Status status = Status.LODGED;
    private String returnReason;
    private Instant lodgedAt;
    private Instant presentedAt;
    private Instant settledAt;             // cleared / returned / stopped time

    public static Cheque lodge(String chequeId, String chequeNumber, String drawerAccountRef,
                               String beneficiaryAccountRef, long amountMinor, String currency,
                               Instant now) {
        Cheque c = new Cheque();
        c.chequeId = chequeId;
        c.chequeNumber = chequeNumber;
        c.drawerAccountRef = drawerAccountRef;
        c.beneficiaryAccountRef = beneficiaryAccountRef;
        c.amountMinor = amountMinor;
        c.currency = currency;
        c.lodgedAt = now;
        return c;
    }

    public boolean isTerminal() {
        return status == Status.CLEARED || status == Status.RETURNED || status == Status.STOPPED;
    }

    public String getChequeId() { return chequeId; }
    public String getChequeNumber() { return chequeNumber; }
    public String getDrawerAccountRef() { return drawerAccountRef; }
    public String getBeneficiaryAccountRef() { return beneficiaryAccountRef; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }
    public Instant getLodgedAt() { return lodgedAt; }
    public Instant getPresentedAt() { return presentedAt; }
    public void setPresentedAt(Instant presentedAt) { this.presentedAt = presentedAt; }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
}
