package com.foodordering.dto;

public class UpdatePaymentStatusRequest {
    private String status;
    private String transactionRef;

    public UpdatePaymentStatusRequest() {}

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
}
