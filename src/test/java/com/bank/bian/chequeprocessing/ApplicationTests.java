package com.bank.bian.chequeprocessing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Boot + API smoke test: the clearing lifecycle through HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {

    static final String CR = "/v1/cheque-transaction-procedure";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    String url(String path) { return "http://localhost:" + port + path; }

    @Test
    void lodgePresentClearJourney() {
        var lodged = rest.postForEntity(url(CR + "/initiate"),
                Map.of("chequeNumber", "987654", "drawerAccountRef", "CA-D1",
                        "beneficiaryAccountRef", "CA-B1", "amountMinor", 75_000, "currency", "INR"),
                Map.class);
        assertThat(lodged.getStatusCode().value()).isEqualTo(201);
        String id = (String) lodged.getBody().get("chequeId");

        rest.postForEntity(url(CR + "/" + id + "/present"), null, Map.class);
        var cleared = rest.postForEntity(url(CR + "/" + id + "/clear"), null, Map.class);
        assertThat(cleared.getBody().get("status")).isEqualTo("CLEARED");

        // stop after clearance → 409 (terminal)
        var stop = rest.exchange(url(CR + "/" + id + "/control"),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("action", "stop")), Map.class);
        assertThat(stop.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void selfDepositRejectedThroughApi() {
        var bad = rest.postForEntity(url(CR + "/initiate"),
                Map.of("chequeNumber", "111222", "drawerAccountRef", "CA-X",
                        "beneficiaryAccountRef", "CA-X", "amountMinor", 1_000, "currency", "INR"),
                Map.class);
        assertThat(bad.getStatusCode().value()).isEqualTo(400);
        assertThat(bad.getBody().get("code")).isEqualTo("SELF_DEPOSIT");
    }
}
