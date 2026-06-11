package com.bank.bian.chequeprocessing.api;

import com.bank.bian.chequeprocessing.domain.Cheque;
import com.bank.bian.chequeprocessing.domain.ChequeProcessingService;
import com.bank.bian.chequeprocessing.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * BIAN semantic API for "Cheque Processing" — Phase 2a, real domain.
 * Control record: Cheque Transaction Procedure.
 * present / clear / return are the Execute steps of the Process pattern.
 *
 * Contract: api/openapi.yaml (owned by this repo).
 */
@RestController
@RequestMapping("/v1")
public class ServiceDomainController {

    static final String CR = "cheque-transaction-procedure";

    private final ChequeProcessingService service;

    public ServiceDomainController(ChequeProcessingService service) {
        this.service = service;
    }

    @GetMapping("/service-domain")
    public Map<String, String> serviceDomain() {
        return Map.of(
                "serviceDomain", "Cheque Processing",
                "businessArea", "Operations and Execution",
                "businessDomain", "Payments",
                "functionalPattern", "Process",
                "assetType", "Cheque Transaction",
                "controlRecord", "Cheque Transaction Procedure",
                "version", "0.2.0",
                "phase", "2a-deep"
        );
    }

    // ── lodgement (Initiate) ─────────────────────────────────────────────────

    public record LodgeRequest(String chequeNumber, String drawerAccountRef,
                               String beneficiaryAccountRef, long amountMinor, String currency) {}

    @PostMapping("/" + CR + "/initiate")
    public ResponseEntity<Cheque> initiate(@RequestBody LodgeRequest req) {
        Cheque cheque = service.lodge(req.chequeNumber(), req.drawerAccountRef(),
                req.beneficiaryAccountRef(), req.amountMinor(),
                req.currency() == null ? "INR" : req.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(cheque);
    }

    @GetMapping("/" + CR)
    public Collection<Cheque> list() {
        return service.list();
    }

    @GetMapping("/" + CR + "/{chequeId}/retrieve")
    public Cheque retrieve(@PathVariable String chequeId) {
        return service.retrieve(chequeId);
    }

    // ── Control: the drawer's stop order ─────────────────────────────────────

    @PutMapping("/" + CR + "/{chequeId}/control")
    public Cheque control(@PathVariable String chequeId, @RequestBody Map<String, String> body) {
        String action = body.get("action");
        if (!"stop".equalsIgnoreCase(action == null ? "" : action)) {
            throw DomainException.invalid("UNKNOWN_ACTION", "supported control action: stop");
        }
        return service.stop(chequeId);
    }

    // ── Execute steps of the clearing procedure ──────────────────────────────

    @PostMapping("/" + CR + "/{chequeId}/present")
    public Cheque present(@PathVariable String chequeId) {
        return service.present(chequeId);
    }

    @PostMapping("/" + CR + "/{chequeId}/clear")
    public Cheque clear(@PathVariable String chequeId) {
        return service.clear(chequeId);
    }

    public record ReturnRequest(String reason) {}

    @PostMapping("/" + CR + "/{chequeId}/return")
    public Cheque returnCheque(@PathVariable String chequeId, @RequestBody ReturnRequest req) {
        return service.returnCheque(chequeId, req.reason());
    }
}
