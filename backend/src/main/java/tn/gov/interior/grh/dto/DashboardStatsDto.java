package tn.gov.interior.grh.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DashboardStatsDto {
    private long totalPersonnel;
    private long totalUnits;
    private long activeLeaves;
    private long activeTrainings;
    private Map<String, Long> leavesByType;
    private Map<String, Long> personnelByGrade;
    private Map<String, Long> personnelByOrgUnit;
    private long activeMaleCount;
    private long activeFemaleCount;
    private Map<String, Long> activePersonnelByAgeGroup;
    private List<LeaveRequestDto> activeAnnualLeaves;
    private List<LeaveRequestDto> activeExceptionalLeaves;
    private List<LeaveRequestDto> activeSickLeaves;
}
