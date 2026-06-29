package tn.gov.interior.grh.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExceptionalLeaveDto {
    private Long id;
    private String leaveCode;
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
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String justification;
    private String documentPath;
    private Double duration;
    private LocalDate returnDate;
    private String exceptionalLeaveType;
    private String startTime;
    private String session;
}
