package com.example.agriflowapplication;

public class UserProfile {
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String farmName;
    private String farmLocation;

    public UserProfile() {} // Required for Firebase

    public UserProfile(String fullName, String email, String phone, String role, String farmName, String farmLocation) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.farmName = farmName;
        this.farmLocation = farmLocation;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFarmName() { return farmName; }
    public void setFarmName(String farmName) { this.farmName = farmName; }

    public String getFarmLocation() { return farmLocation; }
    public void setFarmLocation(String farmLocation) { this.farmLocation = farmLocation; }
}
