package tn.gov.interior.grh.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TrainingDto {
    private Long id;
    private String titleAr;
    private String titleFr;
    private LocalDate startDate;
    private LocalDate endDate;
    private String institution;
}
