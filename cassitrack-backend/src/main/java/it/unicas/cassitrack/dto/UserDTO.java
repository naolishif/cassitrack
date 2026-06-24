package it.unicas.cassitrack.dto;

import it.unicas.cassitrack.model.User;
import lombok.Data;

@Data
public class UserDTO {

    private Long id;
    private String name;
    private String surname;
    private String email;
    private String role;
    private String taxId;
    private String telephone;

    public static UserDTO from(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setSurname(user.getSurname());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setTaxId(maskTaxId(user.getTaxId()));
        dto.setTelephone(maskTelephone(user.getTelephone()));
        return dto;
    }

    private static String maskTaxId(String taxId) {
        if (taxId == null || taxId.length() <= 4) return "****";
        return "*".repeat(taxId.length() - 4) + taxId.substring(taxId.length() - 4);
    }

    private static String maskTelephone(String telephone) {
        if (telephone == null || telephone.length() <= 3) return "***";
        return "*".repeat(telephone.length() - 3) + telephone.substring(telephone.length() - 3);
    }
}
