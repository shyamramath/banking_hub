package com.northbridge.bank.dispatch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an operation's name AND its security policy in one place. A handler cannot be
 * registered without it: {@link OperationRegistry} refuses to start the application if any
 * {@link OperationHandler} bean is missing this annotation (fail closed).
 *
 * <pre>
 * &#64;Component
 * &#64;BankOperation(name = "transfer.create", scopes = "transfers:write", anyRole = "customer",
 *                audit = true, ratePerMinute = 5)
 * class CreateTransferHandler implements OperationHandler&lt;TransferRequest, TransferReceipt&gt; { ... }
 * </pre>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BankOperation {

    /** Logical name, lower-case dotted, e.g. {@code accounts.list}. */
    String name();

    /**
     * OAuth scopes the access token must carry - ALL are required.
     * No default on purpose: every operation must make an explicit choice.
     */
    String[] scopes();

    /** Realm roles - the user needs at least ONE of these. Empty means "any authenticated user". */
    String[] anyRole() default {};

    /** Write an audit record (success, failure and denial). Use for all state-changing operations. */
    boolean audit() default false;

    /** Per user, per operation, per minute. */
    int ratePerMinute() default 120;
}
