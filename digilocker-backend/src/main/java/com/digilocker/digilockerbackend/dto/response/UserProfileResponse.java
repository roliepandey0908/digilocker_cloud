
package com.digilocker.digilockerbackend.dto.response;

import java.util.List;

public class UserProfileResponse {

    private String userId;
    private String username;
    private List<String> roles;

    public UserProfileResponse() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
