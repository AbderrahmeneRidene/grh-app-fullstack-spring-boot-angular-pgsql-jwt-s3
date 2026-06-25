package tn.gov.interior.grh.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "personnel_trainings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonnelTraining {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "personnel_id", nullable = false)
    private Personnel personnel;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "training_id", nullable = false)
    private Training training;

    @Column(nullable = false)
    private String status; // EN_COURS, TERMINE, ECHOUE

    private String evaluation; // Evaluation / Grade / Notes
}
