package tn.gov.interior.grh.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JwtResponse {
    private String token;
    private Long id;
    private String username;
    private String email;
    private List<String> roles;
    private Long personnelId;
    private String fullNameAr;
    private String fullNameFr;
    private String grade;
    private String orgUnitNameAr;
    private String orgUnitNameFr;
    private String gender;
    private String profilePicture;
}
