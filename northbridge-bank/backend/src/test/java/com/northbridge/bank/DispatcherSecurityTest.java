package com.northbridge.bank;

import static com.northbridge.bank.TestTokens.ALL_CUSTOMER_SCOPES;
import static com.northbridge.bank.TestTokens.alice;
import static com.northbridge.bank.TestTokens.bob;
import static com.northbridge.bank.TestTokens.carol;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northbridge.bank.dispatch.OperationRateLimiter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The security matrix for the single endpoint: no token / wrong scope / wrong role /
 * wrong owner / bad payload / valid.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DispatcherSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OperationRateLimiter rateLimiter;

    @BeforeEach
    void resetLimits() {
        rateLimiter.reset();
    }

    static String transfer(String from, String to, String amount) {
        return "{\"fromAccountId\":\"" + from + "\",\"toAccountId\":\"" + to + "\",\"amount\":" + amount
            + ",\"memo\":\"test\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}";
    }

    ResultActions call(RequestPostProcessor who, String operation, String payloadJson) throws Exception {
        String body = "{\"operation\":\"" + operation + "\",\"payload\":" + payloadJson + "}";
        var req = post("/api/execute").contentType(MediaType.APPLICATION_JSON).content(body);
        if (who != null) req.with(who);
        return mvc.perform(req);
    }

    @Nested
    class Authentication {
        @Test
        void noTokenIsRejected() throws Exception {
            call(null, "accounts.list", "{}").andExpect(status().isUnauthorized());
        }

        @Test
        void anyOtherUrlIsDenied() throws Exception {
            mvc.perform(get("/api/accounts").with(alice(ALL_CUSTOMER_SCOPES))).andExpect(status().isForbidden());
        }

        @Test
        void healthIsPublic() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        void correlationIdIsEchoed() throws Exception {
            String cid = "test-" + UUID.randomUUID();
            mvc.perform(post("/api/execute").header("X-Correlation-Id", cid).with(alice(ALL_CUSTOMER_SCOPES))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"operation\":\"accounts.list\"}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Correlation-Id", cid))
                    .andExpect(jsonPath("$.correlationId").value(cid));
        }
    }

    @Nested
    class FunctionLevelAuthorization {
        @Test
        void validCustomerListsOnlyOwnAccounts() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "accounts.list", "{}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(3)))
                    .andExpect(jsonPath("$.data[*].id", everyItem(startsWith("acc-alice"))))
                    .andExpect(jsonPath("$.data[0].maskedNumber", startsWith("••••")));
        }

        @Test
        void missingScopeIsForbidden() throws Exception {
            call(alice("transfers:write"), "accounts.list", "{}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("OPERATION_NOT_PERMITTED"));
        }

        @Test
        void wrongRoleIsForbiddenEvenWithTheScope() throws Exception {
            call(carol("accounts:read transfers:write"), "accounts.list", "{}")
                    .andExpect(status().isForbidden());
        }

        @Test
        void customerCannotReadAuditLogEvenWithAuditScope() throws Exception {
            call(alice("accounts:read audit:read"), "audit.list", "{}")
                    .andExpect(status().isForbidden());
        }

        @Test
        void unknownOperationLooksExactlyLikeForbidden() throws Exception {
            String unknown = call(alice(ALL_CUSTOMER_SCOPES), "admin.users.delete", "{}")
                    .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
            String forbidden = call(alice(ALL_CUSTOMER_SCOPES), "audit.list", "{}")
                    .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(unknown).get("error")).isEqualTo(json.readTree(forbidden).get("error"));
        }

        @Test
        void malformedOperationNameIsRejectedAtTheEnvelope() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "../../etc/passwd", "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
    }

    @Nested
    class ObjectLevelAuthorization {
        @Test
        void ownerCanReadAccount() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "account.details", "{\"accountId\":\"acc-alice-chk\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("Everyday Checking"));
        }

        @Test
        void someoneElsesAccountLooksLikeItDoesNotExist() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "account.details", "{\"accountId\":\"acc-bob-chk\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
            call(alice(ALL_CUSTOMER_SCOPES), "account.details", "{\"accountId\":\"acc-nope\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }

        @Test
        void cannotListSomeoneElsesTransactions() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "transactions.list", "{\"accountId\":\"acc-bob-sav\"}")
                    .andExpect(status().isNotFound());
        }

        @Test
        void cannotTransferOutOfSomeoneElsesAccount() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "transfer.create", transfer("acc-bob-chk", "acc-alice-sav", "10.00"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class PayloadValidation {
        @Test
        void massAssignmentFieldIsRejected() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "account.details",
                    "{\"accountId\":\"acc-alice-chk\",\"ownerId\":\"22222222-2222-4222-8222-222222222222\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_PAYLOAD"))
                    .andExpect(jsonPath("$.error.details[0].field").value("ownerId"));
        }

        @Test
        void constraintViolationsAreReported() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "transfer.create",
                    "{\"fromAccountId\":\"acc-alice-chk\",\"toAccountId\":\"acc-alice-sav\",\"amount\":-5,"
                            + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[*].field", hasItem("amount")));
        }

        @Test
        void exactlyOneDestinationIsRequired() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "transfer.create",
                    "{\"fromAccountId\":\"acc-alice-chk\",\"amount\":5,\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[*].field", hasItem("exactlyOneDestination")));
        }

        @Test
        void payloadMustBeAnObject() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "accounts.list", "[1,2,3]")
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Transfers {
        @Test
        void transferMovesMoneyOnceEvenWhenReplayed() throws Exception {
            BigDecimalSnapshot before = balances();
            String body = transfer("acc-alice-chk", "acc-alice-sav", "25.00");

            JsonNode first = json.readTree(call(alice(ALL_CUSTOMER_SCOPES), "transfer.create", body)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            JsonNode replay = json.readTree(call(alice(ALL_CUSTOMER_SCOPES), "transfer.create", body)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

            assertThat(first.at("/data/replayed").asBoolean()).isFalse();
            assertThat(replay.at("/data/replayed").asBoolean()).isTrue();
            assertThat(replay.at("/data/transferId").asText()).isEqualTo(first.at("/data/transferId").asText());

            BigDecimalSnapshot after = balances();
            assertThat(before.checking - after.checking).isEqualTo(25.00, org.assertj.core.data.Offset.offset(0.001));
            assertThat(after.savings - before.savings).isEqualTo(25.00, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        void transferNeedsWriteScope() throws Exception {
            call(alice("accounts:read"), "transfer.create", transfer("acc-alice-chk", "acc-alice-sav", "1.00"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void overLimitIsABusinessError() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "transfer.create", transfer("acc-alice-sav", "acc-alice-chk", "10000.01"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("LIMIT_EXCEEDED"));
        }

        @Test
        void transfersAreRateLimited() throws Exception {
            for (int i = 0; i < 5; i++) {
                call(bob(ALL_CUSTOMER_SCOPES), "transfer.create", transfer("acc-bob-sav", "acc-bob-chk", "1.00"))
                        .andExpect(status().isOk());
            }
            call(bob(ALL_CUSTOMER_SCOPES), "transfer.create", transfer("acc-bob-sav", "acc-bob-chk", "1.00"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
        }

        @Test
        void auditorSeesTransfersAndDenials() throws Exception {
            call(alice(ALL_CUSTOMER_SCOPES), "transfer.create", transfer("acc-alice-chk", "acc-alice-sav", "2.00"))
                    .andExpect(status().isOk());
            call(alice(ALL_CUSTOMER_SCOPES), "audit.list", "{}").andExpect(status().isForbidden());

            call(carol("accounts:read audit:read"), "audit.list", "{\"limit\":20}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].operation", hasItem("transfer.create")))
                    .andExpect(jsonPath("$.data[*].outcome", hasItem("denied")));
        }

        private record BigDecimalSnapshot(double checking, double savings) {
        }

        private BigDecimalSnapshot balances() throws Exception {
            JsonNode list = json.readTree(call(alice(ALL_CUSTOMER_SCOPES), "accounts.list", "{}")
                    .andReturn().getResponse().getContentAsString()).get("data");
            double chk = 0;
            double sav = 0;
            for (JsonNode a : list) {
                if (a.get("id").asText().equals("acc-alice-chk")) chk = a.get("balance").asDouble();
                if (a.get("id").asText().equals("acc-alice-sav")) sav = a.get("balance").asDouble();
            }
            return new BigDecimalSnapshot(chk, sav);
        }
    }

    @Nested
    class Batch {
        @Test
        void eachItemIsAuthorizedIndependently() throws Exception {
            String body = """
                    {"requests":[
                      {"operation":"profile.get"},
                      {"operation":"accounts.list"},
                      {"operation":"audit.list"},
                      {"operation":"account.details","payload":{"accountId":"acc-bob-chk"}}
                    ]}""";
            mvc.perform(post("/api/execute/batch").with(alice(ALL_CUSTOMER_SCOPES))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[0].success").value(true))
                    .andExpect(jsonPath("$[1].success").value(true))
                    .andExpect(jsonPath("$[2].error.code").value("OPERATION_NOT_PERMITTED"))
                    .andExpect(jsonPath("$[3].error.code").value("NOT_FOUND"));
        }

        @Test
        void batchSizeIsCapped() throws Exception {
            StringBuilder sb = new StringBuilder("{\"requests\":[");
            for (int i = 0; i < 11; i++) sb.append(i == 0 ? "" : ",").append("{\"operation\":\"accounts.list\"}");
            sb.append("]}");
            mvc.perform(post("/api/execute/batch").with(alice(ALL_CUSTOMER_SCOPES))
                            .contentType(MediaType.APPLICATION_JSON).content(sb.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BATCH_TOO_LARGE"));
        }
    }
}
