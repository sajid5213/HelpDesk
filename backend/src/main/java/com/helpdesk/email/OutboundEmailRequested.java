package com.helpdesk.email;

/** Published inside a transaction; the email goes out only after that transaction commits. */
public record OutboundEmailRequested(OutboundEmail email) {
}
