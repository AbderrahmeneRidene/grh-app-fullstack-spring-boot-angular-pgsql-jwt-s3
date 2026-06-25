package tn.gov.interior.grh.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "personnels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Personnel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_number", unique = true, nullable = false)
    private String registrationNumber; // Matricule

    @Column(name = "first_name_ar", nullable = false)
    private String firstNameAr;

    @Column(name = "father_name_ar")
    private String fatherNameAr;

    @Column(name = "last_name_ar", nullable = false)
    private String lastNameAr;

    @Column(name = "first_name_fr")
    private String firstNameFr;

    @Column(name = "last_name_fr")
    private String lastNameFr;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String grade; // Rank / Echelle (e.g. Brigadier, Major, Lieutenant, etc.)

    @Column(name = "functional_post")
    private String functionalPost; // e.g., DIRECTEUR, SOUS_DIRECTEUR, CHEF_SERVICE, CHEF_QISM, null

    @Column(name = "gender")
    private String gender; // MALE, FEMALE

    @Column(name = "marital_status")
    private String maritalStatus; // CELIBATAIRE, MARIE, DIVORCE, VEUF

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "phone_number2")
    private String phoneNumber2;

    @Column(name = "emergency_phone")
    private String emergencyPhone;

    @Column(name = "profile_picture", columnDefinition = "TEXT")
    private String profilePicture;

    @OneToMany(mappedBy = "personnel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Child> children = new ArrayList<>();

    @OneToMany(mappedBy = "personnel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PersonnelStage> stages = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organizational_unit_id", nullable = false)
    private OrganizationalUnit organizationalUnit;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_account_id")
    private UserAccount userAccount;

    @Column(name = "archive_status", nullable = true)
    @Builder.Default
    private String archiveStatus = "ACTIVE"; // ACTIVE, PENDING_ARCHIVE, ARCHIVED, PENDING_RECOVER
}
