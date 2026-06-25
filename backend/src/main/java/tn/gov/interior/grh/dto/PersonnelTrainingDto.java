package tn.gov.interior.grh.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PersonnelTrainingDto {
    private Long id;
    private Long personnelId;
    private String personnelFullNameAr;
    private String personnelFullNameFr;
    private Long trainingId;
    private String trainingTitleAr;
    private String trainingTitleFr;
    private String status;
    private String evaluation;
}
