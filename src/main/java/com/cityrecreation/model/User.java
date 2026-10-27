package com.cityrecreation.model;

import java.io.Serializable;

/**
 * Represents a registered user in the City Recreational Spaces Navigator.
 * Corresponds to the USER and ROLE tables in schema.sql.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private int roleId;
    private String roleName;
    private String email;
    private String passwordHash;
    private String phoneNum;
    private boolean emailVerified;
    private boolean twofaEnabled;
    private String billingAddress;
    private String paymentMethodStub;

    public User() {
    }

    public User(int userId, int roleId, String roleName, String email, String passwordHash,
                String phoneNum, boolean emailVerified, boolean twofaEnabled) {
        this.userId = userId;
        this.roleId = roleId;
        this.roleName = roleName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phoneNum = phoneNum;
        this.emailVerified = emailVerified;
        this.twofaEnabled = twofaEnabled;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getRoleId() {
        return roleId;
    }

    public void setRoleId(int roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhoneNum() {
        return phoneNum;
    }

    public void setPhoneNum(String phoneNum) {
        this.phoneNum = phoneNum;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isTwofaEnabled() {
        return twofaEnabled;
    }

    public void setTwofaEnabled(boolean twofaEnabled) {
        this.twofaEnabled = twofaEnabled;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(String billingAddress) {
        this.billingAddress = billingAddress;
    }

    public String getPaymentMethodStub() {
        return paymentMethodStub;
    }

    public void setPaymentMethodStub(String paymentMethodStub) {
        this.paymentMethodStub = paymentMethodStub;
    }
}
