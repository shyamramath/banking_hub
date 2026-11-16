package com.example.ensenta.client;

/**
 * Maps Ensenta ResponseCode values (see the Authentication and Check Deposit guides)
 * to actions the gateway and the mobile app can take.
 */
public class EnsentaException extends RuntimeException {

    public enum Code {
        E12("No eligible accounts", Action.FIX_REQUEST),
        E35("Rejected by deposit policy (reject-level risk)", Action.SHOW_REJECT),
        E50("Session not found", Action.RESTART_SESSION),
        E51("Session expired", Action.RESTART_SESSION),
        E77("User blocklisted", Action.BLOCK_USER),
        E78("User not on allowlist", Action.BLOCK_USER),
        E88("Deposit item not found yet", Action.RETRY_LATER),
        E500("Invalid parameter", Action.FIX_REQUEST),
        EL1("Deposit limit exceeded", Action.SHOW_LIMIT),
        E99("Unexpected error", Action.RESTART_DEPOSIT),
        UNKNOWN("Unrecognised response code", Action.RESTART_DEPOSIT);

        public final String description;
        public final Action action;
        Code(String description, Action action) { this.description = description; this.action = action; }

        public static Code of(String responseCode) {
            try { return Code.valueOf(responseCode); } catch (Exception e) { return UNKNOWN; }
        }
    }

    public enum Action { FIX_REQUEST, SHOW_REJECT, RESTART_SESSION, BLOCK_USER, RETRY_LATER, SHOW_LIMIT, RESTART_DEPOSIT }

    private final Code code;
    private final String rawCode;
    private final String rawMessage;

    public EnsentaException(String rawCode, String rawMessage) {
        super(rawCode + ": " + rawMessage);
        this.rawCode = rawCode;
        this.rawMessage = rawMessage;
        this.code = Code.of(rawCode);
    }

    public Code code() { return code; }
    public String rawCode() { return rawCode; }
    public String rawMessage() { return rawMessage; }

    /** "00" is success in every Ensenta response. */
    public static void check(String responseCode, String message) {
        if (!"00".equals(responseCode)) {
            throw new EnsentaException(responseCode, message);
        }
    }
}
