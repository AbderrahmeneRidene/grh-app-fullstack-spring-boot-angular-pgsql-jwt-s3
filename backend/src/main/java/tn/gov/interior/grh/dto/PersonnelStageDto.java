package tn.gov.interior.grh.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PersonnelStageDto {
    private Long id;
    private String title;
    private LocalDate startDate;
    private String duration;
    private String institution;
}
