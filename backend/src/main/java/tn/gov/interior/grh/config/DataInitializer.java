package tn.gov.interior.grh.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.gov.interior.grh.model.*;
import tn.gov.interior.grh.repository.*;
import java.time.LocalDate;
import java.util.*;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AdministrationRepository administrationRepository;

    @Autowired
    private OrganizationalUnitRepository organizationalUnitRepository;

    @Autowired
    private PersonnelRepository personnelRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private TrainingRepository trainingRepository;

    @Autowired
    private PersonnelTrainingRepository personnelTrainingRepository;

    @Autowired
    private CareerProgressionRepository careerProgressionRepository;

    @Autowired
    private AcademicBackgroundRepository academicBackgroundRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.default-administration.name-ar}")
    private String defaultAdminNameAr;

    @Value("${app.default-administration.name-fr}")
    private String defaultAdminNameFr;

    @Value("${app.default-administration.code}")
    private String defaultAdminCode;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (roleRepository.count() > 0) {
            personnelRepository.findByRegistrationNumber("DIR001").ifPresent(p -> {
                if (p.getFunctionalPost() == null) {
                    p.setFunctionalPost("DIRECTEUR");
                    personnelRepository.save(p);
                }
            });
            personnelRepository.findByRegistrationNumber("CSD001").ifPresent(p -> {
                if (p.getFunctionalPost() == null) {
                    p.setFunctionalPost("SOUS_DIRECTEUR");
                    personnelRepository.save(p);
                }
            });
            personnelRepository.findByRegistrationNumber("CSV001").ifPresent(p -> {
                if (p.getFunctionalPost() == null) {
                    p.setFunctionalPost("CHEF_SERVICE");
                    personnelRepository.save(p);
                }
            });
            return; // Base déjà initialisée
        }

        // 1. Initialisation des rôles
        Role superAdminRole = roleRepository.save(new Role(null, "ROLE_SUPER_ADMIN"));
        Role adminDirRole = roleRepository.save(new Role(null, "ROLE_ADMIN_DIRECTION"));
        Role chefSdRole = roleRepository.save(new Role(null, "ROLE_CHEF_SOUS_DIRECTION"));
        Role chefServRole = roleRepository.save(new Role(null, "ROLE_CHEF_SERVICE"));
        Role agentRhRole = roleRepository.save(new Role(null, "ROLE_AGENT_RH"));
        Role userRole = roleRepository.save(new Role(null, "ROLE_USER"));

        // 2. Création de l'utilisateur SuperAdmin
        UserAccount superadmin = UserAccount.builder()
                .username("superadmin")
                .password(passwordEncoder.encode("admin123"))
                .email("superadmin@interior.gov.tn")
                .active(true)
                .roles(Set.of(superAdminRole))
                .build();
        userAccountRepository.save(superadmin);

        // 3. Création de l'administration par défaut (École de Police بصلامبو)
        Administration school = Administration.builder()
            .nameAr(defaultAdminNameAr)
            .nameFr(defaultAdminNameFr)
            .code(defaultAdminCode)
            .build();
        school = administrationRepository.save(school);

        // 4. Structure Hiérarchique (Direction -> Sous-Directions -> Services)
        // A. Direction (Unité racine)
        OrganizationalUnit rootDirection = OrganizationalUnit.builder()
                .nameAr("إدارة المدرسة")
                .nameFr("Direction de l'Ecole")
                .type("DIRECTION")
                .administration(school)
                .build();
        rootDirection = organizationalUnitRepository.save(rootDirection);

        // B. Sous-Direction 1: Formation et Etudes
        OrganizationalUnit sdFormation = OrganizationalUnit.builder()
                .nameAr("الإدارة الفرعية للتكوين والدراسات")
                .nameFr("Sous-Direction de la Formation et des Etudes")
                .type("SOUS_DIRECTION")
                .parent(rootDirection)
                .administration(school)
                .build();
        sdFormation = organizationalUnitRepository.save(sdFormation);

        // Services pour SD Formation
        OrganizationalUnit servPedagogie = organizationalUnitRepository.save(OrganizationalUnit.builder()
                .nameAr("مصلحة البيداغوجيا والبرمجة والتربصات")
                .nameFr("Service de la Pedagogie, Programmation et Stages")
                .type("SERVICE").parent(sdFormation).administration(school).build());

        OrganizationalUnit servRelations = organizationalUnitRepository.save(OrganizationalUnit.builder()
                .nameAr("مصلحة العلاقات مع المؤسسات العلمية والتكوينية")
                .nameFr("Service des Relations avec les Etablissements Scientifiques")
                .type("SERVICE").parent(sdFormation).administration(school).build());

        OrganizationalUnit servDocumentation = organizationalUnitRepository.save(OrganizationalUnit.builder()
                .nameAr("مصلحة التوثيق والدراسات")
                .nameFr("Service de la Documentation et des Etudes")
                .type("SERVICE").parent(sdFormation).administration(school).build());

        // C. Sous-Direction 2: Instruction et Affaires Eleves
        OrganizationalUnit sdInstruction = OrganizationalUnit.builder()
                .nameAr("الإدارة الفرعية للتدريب وشؤون التلامذة")
                .nameFr("Sous-Direction de l'Instruction et des Affaires des Eleves")
                .type("SOUS_DIRECTION")
                .parent(rootDirection)
                .administration(school)
                .build();
        sdInstruction = organizationalUnitRepository.save(sdInstruction);

        OrganizationalUnit servEleves = organizationalUnitRepository.save(OrganizationalUnit.builder()
                .nameAr("مصلحة شؤون التلامذة")
                .nameFr("Service des Affaires des Eleves")
                .type("SERVICE").parent(sdInstruction).administration(school).build());

        OrganizationalUnit servSport = organizationalUnitRepository.save(OrganizationalUnit.builder()
                .nameAr("مصلحة التدريب والرياضة")
                .nameFr("Service de l'Instruction et des Sports")
                .type("SERVICE").parent(sdInstruction).administration(school).build());

        // D. Sous-Direction 3: Affaires Administratives et Financieres
        OrganizationalUnit sdAdminFin = OrganizationalUnit.builder()
                .nameAr("الإدارة الفرعية للشؤون الإدارية والمالية")
                .nameFr("Sous-Direction des Affaires Administratives et Financieres")
                .type("SOUS_DIRECTION")
                .parent(rootDirection)
                .administration(school)
                .build();
        sdAdminFin = organizationalUnitRepository.save(sdAdminFin);

        OrganizationalUnit servAdmin = organizationalUnitRepository.save(OrganizationalUnit.builder()
                .nameAr("مصلحة الشؤون الإدارية")
                .nameFr("Service des Affaires Administratives")
                .type("SERVICE").parent(sdAdminFin).administration(school).build());

        OrganizationalUnit servFinance = organizationalUnitRepository.save(OrganizationalUnit.builder()
                .nameAr("مصلحة الشؤون المالية")
                .nameFr("Service des Affaires Financieres")
                .type("SERVICE").parent(sdAdminFin).administration(school).build());

        OrganizationalUnit servMaintenance = organizationalUnitRepository.save(OrganizationalUnit.builder()
                .nameAr("مصلحة الإسناد والصيانة")
                .nameFr("Service du Soutien et de la Maintenance")
                .type("SERVICE").parent(sdAdminFin).administration(school).build());

        // 5. Création des comptes et personnel par défaut pour chaque niveau
        // A. Directeur de l'école (ADMIN_DIRECTION)
        UserAccount dirUser = userAccountRepository.save(UserAccount.builder()
                .username("directeur")
                .password(passwordEncoder.encode("dir123"))
                .email("directeur@interior.gov.tn")
                .active(true)
                .roles(Set.of(adminDirRole))
                .build());

        Personnel directeur = personnelRepository.save(Personnel.builder()
                .registrationNumber("DIR001")
                .firstNameAr("محمد")
                .fatherNameAr("الهادي")
                .lastNameAr("بن علي")
                .firstNameFr("Mohamed")
                .lastNameFr("Ben Ali")
                .dateOfBirth(LocalDate.of(1975, 5, 12))
                .grade("عميد")
                .functionalPost("DIRECTEUR")
                .gender("MALE")
                .maritalStatus("MARIE")
                .phoneNumber("71123456")
                .phoneNumber2("98123456")
                .emergencyPhone("22123456")
                .organizationalUnit(rootDirection)
                .userAccount(dirUser)
                .build());

        // B. Chef de Sous-Direction Formation (CHEF_SOUS_DIRECTION)
        UserAccount sdFormUser = userAccountRepository.save(UserAccount.builder()
                .username("chef_sd_formation")
                .password(passwordEncoder.encode("formation123"))
                .email("sd.formation@interior.gov.tn")
                .active(true)
                .roles(Set.of(chefSdRole))
                .build());

        Personnel chefSdForm = personnelRepository.save(Personnel.builder()
                .registrationNumber("CSD001")
                .firstNameAr("سليم")
                .fatherNameAr("عبد الله")
                .lastNameAr("الورفلي")
                .firstNameFr("Selim")
                .lastNameFr("Ouertani")
                .dateOfBirth(LocalDate.of(1980, 8, 22))
                .grade("رائد")
                .functionalPost("SOUS_DIRECTEUR")
                .gender("MALE")
                .maritalStatus("MARIE")
                .phoneNumber("71654321")
                .phoneNumber2("98654321")
                .emergencyPhone("22654321")
                .organizationalUnit(sdFormation)
                .userAccount(sdFormUser)
                .build());

        // C. Chef de Service Pédagogie (CHEF_SERVICE)
        UserAccount servPedUser = userAccountRepository.save(UserAccount.builder()
                .username("chef_service_pedagogie")
                .password(passwordEncoder.encode("pedagogie123"))
                .email("pedagogie@interior.gov.tn")
                .active(true)
                .roles(Set.of(chefServRole))
                .build());

        Personnel chefServPed = personnelRepository.save(Personnel.builder()
                .registrationNumber("CSV001")
                .firstNameAr("ليلى")
                .fatherNameAr("المنصف")
                .lastNameAr("الطرابلسي")
                .firstNameFr("Leila")
                .lastNameFr("Trabelsi")
                .dateOfBirth(LocalDate.of(1984, 11, 5))
                .grade("نقيب")
                .functionalPost("CHEF_SERVICE")
                .gender("FEMALE")
                .maritalStatus("CELIBATAIRE")
                .phoneNumber("71789456")
                .emergencyPhone("98789456")
                .organizationalUnit(servPedagogie)
                .userAccount(servPedUser)
                .build());

        // D. Agent RH (AGENT_RH)
        UserAccount agentRhUser = userAccountRepository.save(UserAccount.builder()
                .username("agentrh")
                .password(passwordEncoder.encode("rh123"))
                .email("rh@interior.gov.tn")
                .active(true)
                .roles(Set.of(agentRhRole))
                .build());

        Personnel agentRh = personnelRepository.save(Personnel.builder()
                .registrationNumber("RH001")
                .firstNameAr("أنيس")
                .fatherNameAr("البشير")
                .lastNameAr("الرياحي")
                .firstNameFr("Anis")
                .lastNameFr("Riahi")
                .dateOfBirth(LocalDate.of(1990, 2, 14))
                .grade("ملازم أول")
                .gender("MALE")
                .maritalStatus("CELIBATAIRE")
                .phoneNumber("71159357")
                .emergencyPhone("98159357")
                .organizationalUnit(servAdmin)
                .userAccount(agentRhUser)
                .build());

        // E. Personnel Standard / Elève (USER)
        UserAccount studentUser = userAccountRepository.save(UserAccount.builder()
                .username("agent_ahmed")
                .password(passwordEncoder.encode("ahmed123"))
                .email("ahmed.tounsi@interior.gov.tn")
                .active(true)
                .roles(Set.of(userRole))
                .build());

        Personnel student = personnelRepository.save(Personnel.builder()
                .registrationNumber("AGT089")
                .firstNameAr("أحمد")
                .fatherNameAr("بلقاسم")
                .lastNameAr("التونسي")
                .firstNameFr("Ahmed")
                .lastNameFr("Tounsi")
                .dateOfBirth(LocalDate.of(1998, 7, 30))
                .grade("ناظر أمن")
                .gender("MALE")
                .maritalStatus("CELIBATAIRE")
                .phoneNumber("71357951")
                .emergencyPhone("98357951")
                .organizationalUnit(servEleves)
                .userAccount(studentUser)
                .build());

        // 6. Données de parcours académique pour Ahmed
        academicBackgroundRepository.save(AcademicBackground.builder()
                .degreeName("الإجازة التطبيقية في القانون")
                .specialty("Droit Administratif")
                .university("Faculté de Droit de Tunis")
                .graduationYear(2020)
                .personnel(student)
                .build());

        // 7. Historique de Carrière pour Ahmed
        careerProgressionRepository.save(CareerProgression.builder()
                .personnel(student)
                .previousGrade("حافظ أمن (Sergent)")
                .newGrade("ناظر أمن (Sous-lieutenant)")
                .promotionDate(LocalDate.of(2023, 6, 1))
                .decreeReference("أمر عدد 412 لسنة 2023")
                .build());

        // 8. Formations
        Training cyberSecurity = trainingRepository.save(Training.builder()
                .titleAr("التكوين في الأمن السيبراني والتصدي للجريمة الرقمية")
                .titleFr("Formation en Cybersecurite et Cybercriminalite")
                .startDate(LocalDate.now().minusDays(15))
                .endDate(LocalDate.now().plusDays(15))
                .institution("المدرسة الوطنية بصلامبو")
                .build());

        Training management = trainingRepository.save(Training.builder()
                .titleAr("التدريب على إدارة الأزمات والقيادة الأمنية")
                .titleFr("Gestion de Crise et Leadership Securitaire")
                .startDate(LocalDate.now().minusMonths(3))
                .endDate(LocalDate.now().minusMonths(2))
                .institution("المدرسة الوطنية للمسالك الإدارية")
                .build());

        // Inscription de Ahmed aux formations
        personnelTrainingRepository.save(PersonnelTraining.builder()
                .personnel(student)
                .training(cyberSecurity)
                .status("EN_COURS")
                .build());

        personnelTrainingRepository.save(PersonnelTraining.builder()
                .personnel(student)
                .training(management)
                .status("TERMINE")
                .evaluation("امتياز (Excellent) - 17.5/20")
                .build());

        // 9. Demandes de congés
        leaveRequestRepository.save(LeaveRequest.builder()
                .personnel(student)
                .leaveType("MALADIE")
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().minusDays(3))
                .status("APPROVED")
                .justification("شهادة طبية لراحة 3 أيام (Certificat de 3 jours)")
                .build());

        leaveRequestRepository.save(LeaveRequest.builder()
                .personnel(student)
                .leaveType("ANNUEL")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(25))
                .status("PENDING")
                .justification("إجازة سنوية (Conge annuel principal)")
                .build());
    }
}
