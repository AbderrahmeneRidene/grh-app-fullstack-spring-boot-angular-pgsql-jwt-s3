package tn.gov.interior.grh.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PersonnelDto {
    private Long id;
    private String registrationNumber;
    private String firstNameAr;
    private String fatherNameAr;
    private String lastNameAr;
    private String firstNameFr;
    private String lastNameFr;
    private LocalDate dateOfBirth;
    private String grade;
    private String functionalPost;
    private String gender;
    private String maritalStatus;
    private String phoneNumber;
    private String phoneNumber2;
    private String emergencyPhone;
    private List<ChildDto> children;
    private List<PersonnelStageDto> stages;
    private String profilePicture;
    private Long organizationalUnitId;
    private String organizationalUnitNameAr;
    private String organizationalUnitNameFr;
    
    // Compte utilisateur associé
    private String username;
    private String email;
    private List<String> roles;
    private String archiveStatus;
    private String password;
}
