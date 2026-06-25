package tn.gov.interior.grh.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "administrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Administration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @Column(name = "name_fr", nullable = false)
    private String nameFr;

    @Column(unique = true, nullable = false)
    private String code; // e.g., ENFCSPN (Ecole Nationale)
}
