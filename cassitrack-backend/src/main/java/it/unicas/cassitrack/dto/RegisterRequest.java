package it.unicas.cassitrack.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String taxId;
    private String name;
    private String surname;
    private String email;
    private String password;
    private String telephone;
    private String role;        //Debug only (?) Does the admin want to create other admins?
}