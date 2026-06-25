package tn.gov.interior.grh.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "career_progressions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareerProgression {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personnel_id", nullable = false)
    @JsonIgnore
    private Personnel personnel;

    @Column(name = "previous_grade", nullable = false)
    private String previousGrade;

    @Column(name = "new_grade", nullable = false)
    private String newGrade;

    @Column(name = "promotion_date", nullable = false)
    private LocalDate promotionDate;

    @Column(name = "decree_reference")
    private String decreeReference; // References the decree text (e.g. Journal Officiel reference)
}
