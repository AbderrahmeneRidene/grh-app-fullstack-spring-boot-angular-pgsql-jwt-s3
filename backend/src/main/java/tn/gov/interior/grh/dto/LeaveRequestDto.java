package tn.gov.interior.grh.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LeaveRequestDto {
    private Long id;
    private Long personnelId;
    private String personnelFullNameAr;
    private String personnelFullNameFr;
    private String personnelRegistrationNumber;
    private String personnelProfilePicture;
    private String personnelGender;
    private String personnelFirstNameAr;
    private String personnelFatherNameAr;
    private String personnelLastNameAr;
    private String personnelGrade;
    private String personnelOrgUnitNameAr;
    private String personnelOrgUnitNameFr;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String justification;
    private String documentPath;
    private Long duration;
    private LocalDate returnDate;
}
