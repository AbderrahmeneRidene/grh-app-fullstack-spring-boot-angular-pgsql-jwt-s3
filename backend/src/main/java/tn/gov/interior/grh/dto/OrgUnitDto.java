package tn.gov.interior.grh.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrgUnitDto {
    private Long id;
    private String nameAr;
    private String nameFr;
    private String type;
    private Long parentId;
}
