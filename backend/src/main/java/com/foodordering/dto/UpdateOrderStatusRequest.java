package com.foodordering.dto;

public class UpdateOrderStatusRequest {
    private String status;
    private String note;

    public UpdateOrderStatusRequest() {}

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
