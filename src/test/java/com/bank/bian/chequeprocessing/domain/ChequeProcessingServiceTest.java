package com.bank.bian.chequeprocessing.domain;

import com.bank.bian.chequeprocessing.events.DomainEvent;
import com.bank.bian.chequeprocessing.events.EventPublisher;
import com.bank.bian.chequeprocessing.infrastructure.InMemoryChequeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The clearing state machine, exercised transition by transition. */
class ChequeProcessingServiceTest {

    static class RecordingPublisher implements EventPublisher {
        final List<DomainEvent> events = new ArrayList<>();
        @Override public void publish(DomainEvent event) { events.add(event); }
        List<String> types() { return events.stream().map(DomainEvent::type).toList(); }
    }

    RecordingPublisher events;
    ChequeProcessingService service;

    @BeforeEach
    void setUp() {
        events = new RecordingPublisher();
        service = new ChequeProcessingService(new InMemoryChequeRepository(), events, Clock.systemUTC());
    }

    Cheque lodged() {
        return service.lodge("123456", "CA-DRAWER", "CA-BENEFICIARY", 50_000, "INR");
    }

    @Nested
    class Lodgement {
        @Test
        void validChequeLodgesAndEmitsFraudFeedEvent() {
            Cheque c = lodged();
            assertThat(c.getStatus()).isEqualTo(Cheque.Status.LODGED);
            assertThat(events.types()).containsExactly("cheque.lodged");
            assertThat(events.events.get(0).payload()).containsKeys("amountMinor", "drawerAccountRef");
        }

        @Test
        void selfDepositRejected() {
            assertThatThrownBy(() -> service.lodge("123456", "CA-SAME", "CA-SAME", 1_000, "INR"))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("drawn on");
        }

        @Test
        void implausibleChequeNumberRejected() {
            assertThatThrownBy(() -> service.lodge("12", "CA-A", "CA-B", 1_000, "INR"))
                    .hasMessageContaining("6 digits");
        }
    }

    @Nested
    class HappyPath {
        @Test
        void lodgePresentClear_emitsBeneficiaryCreditInstruction() {
            Cheque c = lodged();
            service.present(c.getChequeId());
            Cheque cleared = service.clear(c.getChequeId());

            assertThat(cleared.getStatus()).isEqualTo(Cheque.Status.CLEARED);
            assertThat(cleared.getSettledAt()).isNotNull();
            assertThat(events.types())
                    .containsExactly("cheque.lodged", "cheque.presented", "cheque.cleared");

            DomainEvent clearedEvt = events.events.get(2);
            assertThat(clearedEvt.payload())
                    .containsEntry("beneficiaryAccountRef", "CA-BENEFICIARY")
                    .containsEntry("amountMinor", 50_000L);
        }
    }

    @Nested
    class Returns {
        @Test
        void presentedChequeCanBeReturnedWithReason() {
            Cheque c = lodged();
            service.present(c.getChequeId());
            Cheque returned = service.returnCheque(c.getChequeId(), "insufficient-funds");
            assertThat(returned.getStatus()).isEqualTo(Cheque.Status.RETURNED);
            assertThat(returned.getReturnReason()).isEqualTo("insufficient-funds");
        }

        @Test
        void returnWithoutReasonRejected() {
            Cheque c = lodged();
            service.present(c.getChequeId());
            assertThatThrownBy(() -> service.returnCheque(c.getChequeId(), "  "))
                    .hasMessageContaining("reason is required");
        }
    }

    @Nested
    class StateMachineGuards {
        @Test
        void cannotClearAnUnpresentedCheque() {
            Cheque c = lodged();
            assertThatThrownBy(() -> service.clear(c.getChequeId()))
                    .hasMessageContaining("requires status PRESENTED");
        }

        @Test
        void stopWorksOnlyBeforePresentment() {
            Cheque early = lodged();
            service.stop(early.getChequeId());
            assertThat(service.retrieve(early.getChequeId()).getStatus()).isEqualTo(Cheque.Status.STOPPED);

            Cheque late = lodged();
            service.present(late.getChequeId());
            assertThatThrownBy(() -> service.stop(late.getChequeId()))
                    .hasMessageContaining("no longer be honored");
        }

        @Test
        void terminalStatesAcceptNoFurtherTransitions() {
            Cheque c = lodged();
            service.present(c.getChequeId());
            service.clear(c.getChequeId());
            assertThatThrownBy(() -> service.present(c.getChequeId()))
                    .hasMessageContaining("requires");
            assertThatThrownBy(() -> service.returnCheque(c.getChequeId(), "late"))
                    .hasMessageContaining("requires status PRESENTED");
        }
    }
}
