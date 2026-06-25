package tn.gov.interior.grh.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.gov.interior.grh.dto.*;
import tn.gov.interior.grh.model.*;
import tn.gov.interior.grh.repository.*;
import tn.gov.interior.grh.service.FileStorageService;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/personnel")
public class PersonnelController {

    @Autowired
    private PersonnelRepository personnelRepository;

    @Autowired
    private OrganizationalUnitRepository organizationalUnitRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AcademicBackgroundRepository academicBackgroundRepository;

    @Autowired
    private CareerProgressionRepository careerProgressionRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private PersonnelTrainingRepository personnelTrainingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FileStorageService fileStorageService;

    @GetMapping
    public List<PersonnelDto> getAllPersonnel() {
        return personnelRepository.findAll().stream()
                .filter(p -> p.getArchiveStatus() == null || "ACTIVE".equals(p.getArchiveStatus()) || "PENDING_ARCHIVE".equals(p.getArchiveStatus()))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/search")
    public List<PersonnelDto> searchPersonnel(@RequestParam("query") String query) {
        return personnelRepository.searchPersonnel(query).stream()
                .filter(p -> p.getArchiveStatus() == null || "ACTIVE".equals(p.getArchiveStatus()) || "PENDING_ARCHIVE".equals(p.getArchiveStatus()))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/archived")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT_RH', 'ROLE_ADMIN_DIRECTION', 'ROLE_SUPER_ADMIN')")
    public List<PersonnelDto> getArchivedPersonnel() {
        return personnelRepository.findAll().stream()
                .filter(p -> "ARCHIVED".equals(p.getArchiveStatus()) || "PENDING_RECOVER".equals(p.getArchiveStatus()))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonnelDto> getPersonnelById(@PathVariable Long id) {
        return personnelRepository.findById(id)
                .map(p -> ResponseEntity.ok(convertToDto(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT_RH', 'ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> createPersonnel(@RequestBody PersonnelDto dto) {
        String validationError = validatePersonnel(dto, false);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }

        Optional<OrganizationalUnit> unitOpt = organizationalUnitRepository.findById(dto.getOrganizationalUnitId());
        if (unitOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Organizational unit not found");
        }

        String profilePictureUrl = dto.getProfilePicture();
        if (dto.getProfilePicture() != null && dto.getProfilePicture().startsWith("data:")) {
            try {
                profilePictureUrl = fileStorageService.uploadBase64(dto.getProfilePicture(), dto.getRegistrationNumber(), "profiles");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Personnel.PersonnelBuilder personnelBuilder = Personnel.builder()
                .registrationNumber(dto.getRegistrationNumber())
                .firstNameAr(dto.getFirstNameAr())
                .fatherNameAr(dto.getFatherNameAr())
                .lastNameAr(dto.getLastNameAr())
                .firstNameFr(dto.getFirstNameFr())
                .lastNameFr(dto.getLastNameFr())
                .dateOfBirth(dto.getDateOfBirth())
                .grade(dto.getGrade())
                .functionalPost(dto.getFunctionalPost())
                .gender(dto.getGender())
                .maritalStatus(dto.getMaritalStatus())
                .phoneNumber(dto.getPhoneNumber())
                .phoneNumber2(dto.getPhoneNumber2())
                .emergencyPhone(dto.getEmergencyPhone())
                .profilePicture(profilePictureUrl)
                .organizationalUnit(unitOpt.get());

        // Créer un compte utilisateur (par défaut si non spécifié)
        String usernameToUse = (dto.getUsername() != null && !dto.getUsername().trim().isEmpty())
                ? dto.getUsername().trim()
                : dto.getRegistrationNumber().trim();

        if (userAccountRepository.existsByUsername(usernameToUse)) {
            return ResponseEntity.badRequest().body("اسم المستخدم مستعمل بالفعل (Username already exists)");
        }

        String emailToUse = (dto.getEmail() != null && !dto.getEmail().trim().isEmpty())
                ? dto.getEmail().trim()
                : usernameToUse.toLowerCase() + "@interior.gov.tn";

        if (userAccountRepository.existsByEmail(emailToUse)) {
            return ResponseEntity.badRequest().body("البريد الإلكتروني مستعمل بالفعل (Email already exists)");
        }

        Set<Role> roles = new HashSet<>();
        if (dto.getRoles() != null) {
            for (String rName : dto.getRoles()) {
                roleRepository.findByName(rName).ifPresent(roles::add);
            }
        }
        if (roles.isEmpty()) {
            roleRepository.findByName("ROLE_USER").ifPresent(roles::add);
        }

        UserAccount userAccount = UserAccount.builder()
                .username(usernameToUse)
                .password(passwordEncoder.encode("p@ssword123")) // Mot de passe par défaut sécurisé
                .email(emailToUse)
                .roles(roles)
                .active(true)
                .build();

        userAccount = userAccountRepository.save(userAccount);
        personnelBuilder.userAccount(userAccount);

        Personnel personnel = personnelBuilder.build();

        if (dto.getChildren() != null) {
            for (ChildDto cDto : dto.getChildren()) {
                personnel.getChildren().add(Child.builder()
                    .name(cDto.getName())
                    .birthDate(cDto.getBirthDate())
                    .personnel(personnel)
                    .build());
            }
        }

        if (dto.getStages() != null) {
            for (PersonnelStageDto sDto : dto.getStages()) {
                personnel.getStages().add(PersonnelStage.builder()
                    .title(sDto.getTitle())
                    .startDate(sDto.getStartDate())
                    .duration(sDto.getDuration())
                    .institution(sDto.getInstitution())
                    .personnel(personnel)
                    .build());
            }
        }

        Personnel saved = personnelRepository.save(personnel);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT_RH', 'ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> updatePersonnel(@PathVariable Long id, @RequestBody PersonnelDto dto) {
        String validationError = validatePersonnel(dto, true);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }

        Optional<Personnel> personnelOpt = personnelRepository.findById(id);
        if (personnelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Personnel personnel = personnelOpt.get();
        Optional<OrganizationalUnit> unitOpt = organizationalUnitRepository.findById(dto.getOrganizationalUnitId());
        if (unitOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Organizational unit not found");
        }

        personnel.setFirstNameAr(dto.getFirstNameAr());
        personnel.setFatherNameAr(dto.getFatherNameAr());
        personnel.setLastNameAr(dto.getLastNameAr());
        personnel.setFirstNameFr(dto.getFirstNameFr());
        personnel.setLastNameFr(dto.getLastNameFr());
        personnel.setDateOfBirth(dto.getDateOfBirth());
        personnel.setGrade(dto.getGrade());
        personnel.setFunctionalPost(dto.getFunctionalPost());
        personnel.setGender(dto.getGender());
        personnel.setMaritalStatus(dto.getMaritalStatus());
        personnel.setPhoneNumber(dto.getPhoneNumber());
        personnel.setPhoneNumber2(dto.getPhoneNumber2());
        personnel.setEmergencyPhone(dto.getEmergencyPhone());
        
        if (dto.getProfilePicture() != null && dto.getProfilePicture().startsWith("data:")) {
            try {
                String profilePictureUrl = fileStorageService.uploadBase64(dto.getProfilePicture(), personnel.getRegistrationNumber(), "profiles");
                personnel.setProfilePicture(profilePictureUrl);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            personnel.setProfilePicture(dto.getProfilePicture());
        }
        
        personnel.setOrganizationalUnit(unitOpt.get());

        // Update children list (using orphanRemoval)
        personnel.getChildren().clear();
        if (dto.getChildren() != null) {
            for (ChildDto cDto : dto.getChildren()) {
                personnel.getChildren().add(Child.builder()
                    .name(cDto.getName())
                    .birthDate(cDto.getBirthDate())
                    .personnel(personnel)
                    .build());
            }
        }

        // Update stages list (using orphanRemoval)
        personnel.getStages().clear();
        if (dto.getStages() != null) {
            for (PersonnelStageDto sDto : dto.getStages()) {
                personnel.getStages().add(PersonnelStage.builder()
                    .title(sDto.getTitle())
                    .startDate(sDto.getStartDate())
                    .duration(sDto.getDuration())
                    .institution(sDto.getInstitution())
                    .personnel(personnel)
                    .build());
            }
        }

        // Mettre à jour le compte utilisateur si présent, sinon le créer
        if (personnel.getUserAccount() != null) {
            UserAccount account = personnel.getUserAccount();
            
            if (dto.getUsername() != null && !dto.getUsername().trim().isEmpty() && !dto.getUsername().trim().equals(account.getUsername())) {
                String newUsername = dto.getUsername().trim();
                if (userAccountRepository.existsByUsername(newUsername)) {
                    return ResponseEntity.badRequest().body("اسم المستخدم مستعمل بالفعل (Username already exists)");
                }
                account.setUsername(newUsername);
            }
            
            if (dto.getEmail() != null && !dto.getEmail().trim().isEmpty()) {
                String newEmail = dto.getEmail().trim();
                if (!newEmail.equalsIgnoreCase(account.getEmail()) && userAccountRepository.existsByEmail(newEmail)) {
                    return ResponseEntity.badRequest().body("البريد الإلكتروني مستعمل بالفعل (Email already exists)");
                }
                account.setEmail(newEmail);
            }
            
            if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
                account.setPassword(passwordEncoder.encode(dto.getPassword().trim()));
            }
            
            if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
                Set<Role> roles = new HashSet<>();
                for (String rName : dto.getRoles()) {
                    roleRepository.findByName(rName).ifPresent(roles::add);
                }
                if (!roles.isEmpty()) {
                    account.setRoles(roles);
                }
            }
            userAccountRepository.save(account);
        } else {
            // Créer un compte si des infos de compte sont fournies ou matricule par défaut
            String usernameToUse = (dto.getUsername() != null && !dto.getUsername().trim().isEmpty())
                    ? dto.getUsername().trim()
                    : dto.getRegistrationNumber().trim();

            if (userAccountRepository.existsByUsername(usernameToUse)) {
                return ResponseEntity.badRequest().body("اسم المستخدم مستعمل بالفعل (Username already exists)");
            }

            String emailToUse = (dto.getEmail() != null && !dto.getEmail().trim().isEmpty())
                    ? dto.getEmail().trim()
                    : usernameToUse.toLowerCase() + "@interior.gov.tn";

            if (userAccountRepository.existsByEmail(emailToUse)) {
                return ResponseEntity.badRequest().body("البريد الإلكتروني مستعمل بالفعل (Email already exists)");
            }

            Set<Role> roles = new HashSet<>();
            if (dto.getRoles() != null) {
                for (String rName : dto.getRoles()) {
                    roleRepository.findByName(rName).ifPresent(roles::add);
                }
            }
            if (roles.isEmpty()) {
                roleRepository.findByName("ROLE_USER").ifPresent(roles::add);
            }

            String rawPassword = (dto.getPassword() != null && !dto.getPassword().trim().isEmpty())
                    ? dto.getPassword().trim()
                    : "p@ssword123";

            UserAccount userAccount = UserAccount.builder()
                    .username(usernameToUse)
                    .password(passwordEncoder.encode(rawPassword))
                    .email(emailToUse)
                    .roles(roles)
                    .active(true)
                    .build();

            userAccount = userAccountRepository.save(userAccount);
            personnel.setUserAccount(userAccount);
        }

        Personnel updated = personnelRepository.save(personnel);
        return ResponseEntity.ok(convertToDto(updated));
    }

    // --- Parcours Académique ---

    @GetMapping("/{id}/academic")
    public List<AcademicBackground> getAcademicBackground(@PathVariable Long id) {
        return academicBackgroundRepository.findByPersonnelId(id);
    }

    @PostMapping("/{id}/academic")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT_RH', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> addAcademicBackground(@PathVariable Long id, @RequestBody AcademicBackground ab) {
        Optional<Personnel> personnelOpt = personnelRepository.findById(id);
        if (personnelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ab.setPersonnel(personnelOpt.get());
        AcademicBackground saved = academicBackgroundRepository.save(ab);
        return ResponseEntity.ok(saved);
    }

    // --- Historique de Carrière / Promotions ---

    @GetMapping("/{id}/career")
    public List<CareerProgression> getCareerProgression(@PathVariable Long id) {
        return careerProgressionRepository.findByPersonnelIdOrderByPromotionDateDesc(id);
    }

    @PostMapping("/{id}/career")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT_RH', 'ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> addCareerProgression(@PathVariable Long id, @RequestBody CareerProgression cp) {
        Optional<Personnel> personnelOpt = personnelRepository.findById(id);
        if (personnelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Personnel personnel = personnelOpt.get();
        
        // Mettre à jour le grade actuel du personnel
        cp.setPreviousGrade(personnel.getGrade());
        personnel.setGrade(cp.getNewGrade());
        personnelRepository.save(personnel);
        
        cp.setPersonnel(personnel);
        CareerProgression saved = careerProgressionRepository.save(cp);
        return ResponseEntity.ok(saved);
    }

    private PersonnelDto convertToDto(Personnel p) {
        PersonnelDto.PersonnelDtoBuilder builder = PersonnelDto.builder()
                .id(p.getId())
                .registrationNumber(p.getRegistrationNumber())
                .firstNameAr(p.getFirstNameAr())
                .fatherNameAr(p.getFatherNameAr())
                .lastNameAr(p.getLastNameAr())
                .firstNameFr(p.getFirstNameFr())
                .lastNameFr(p.getLastNameFr())
                .dateOfBirth(p.getDateOfBirth())
                .grade(p.getGrade())
                .functionalPost(p.getFunctionalPost())
                .gender(p.getGender())
                .maritalStatus(p.getMaritalStatus())
                .phoneNumber(p.getPhoneNumber())
                .phoneNumber2(p.getPhoneNumber2())
                .emergencyPhone(p.getEmergencyPhone())
                .organizationalUnitId(p.getOrganizationalUnit().getId())
                .profilePicture(p.getProfilePicture())
                .organizationalUnitNameAr(p.getOrganizationalUnit().getNameAr())
                .organizationalUnitNameFr(p.getOrganizationalUnit().getNameFr());

        if (p.getChildren() != null) {
            builder.children(p.getChildren().stream()
                .map(c -> ChildDto.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .birthDate(c.getBirthDate())
                    .build())
                .collect(Collectors.toList()));
        }

        if (p.getStages() != null) {
            builder.stages(p.getStages().stream()
                .map(s -> PersonnelStageDto.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .startDate(s.getStartDate())
                    .duration(s.getDuration())
                    .institution(s.getInstitution())
                    .build())
                .collect(Collectors.toList()));
        }

        if (p.getUserAccount() != null) {
            builder.username(p.getUserAccount().getUsername())
                   .email(p.getUserAccount().getEmail())
                   .roles(p.getUserAccount().getRoles().stream().map(Role::getName).collect(Collectors.toList()));
        }

        builder.archiveStatus(p.getArchiveStatus() != null ? p.getArchiveStatus() : "ACTIVE");

        return builder.build();
    }



    @PostMapping("/{id}/request-archive")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT_RH', 'ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> requestArchive(@PathVariable Long id) {
        Optional<Personnel> pOpt = personnelRepository.findById(id);
        if (pOpt.isEmpty()) return ResponseEntity.notFound().build();

        Personnel p = pOpt.get();
        p.setArchiveStatus("PENDING_ARCHIVE");
        personnelRepository.save(p);
        return ResponseEntity.ok(convertToDto(p));
    }

    @PostMapping("/{id}/approve-archive")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_DIRECTION', 'ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> approveArchive(@PathVariable Long id) {
        Optional<Personnel> pOpt = personnelRepository.findById(id);
        if (pOpt.isEmpty()) return ResponseEntity.notFound().build();

        Personnel p = pOpt.get();
        p.setArchiveStatus("ARCHIVED");
        if (p.getUserAccount() != null) {
            p.getUserAccount().setActive(false);
            userAccountRepository.save(p.getUserAccount());
        }
        personnelRepository.save(p);
        return ResponseEntity.ok(convertToDto(p));
    }

    @PostMapping("/{id}/request-active")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT_RH', 'ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> requestActive(@PathVariable Long id) {
        Optional<Personnel> pOpt = personnelRepository.findById(id);
        if (pOpt.isEmpty()) return ResponseEntity.notFound().build();

        Personnel p = pOpt.get();
        p.setArchiveStatus("PENDING_RECOVER");
        personnelRepository.save(p);
        return ResponseEntity.ok(convertToDto(p));
    }

    @PostMapping("/{id}/approve-active")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_DIRECTION', 'ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> approveActive(@PathVariable Long id) {
        Optional<Personnel> pOpt = personnelRepository.findById(id);
        if (pOpt.isEmpty()) return ResponseEntity.notFound().build();

        Personnel p = pOpt.get();
        p.setArchiveStatus("ACTIVE");
        if (p.getUserAccount() != null) {
            p.getUserAccount().setActive(true);
            userAccountRepository.save(p.getUserAccount());
        }
        personnelRepository.save(p);
        return ResponseEntity.ok(convertToDto(p));
    }

    @PostMapping("/{id}/reject-archive-action")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_DIRECTION', 'ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> rejectArchiveAction(@PathVariable Long id) {
        Optional<Personnel> pOpt = personnelRepository.findById(id);
        if (pOpt.isEmpty()) return ResponseEntity.notFound().build();

        Personnel p = pOpt.get();
        if ("PENDING_ARCHIVE".equals(p.getArchiveStatus())) {
            p.setArchiveStatus("ACTIVE");
        } else if ("PENDING_RECOVER".equals(p.getArchiveStatus())) {
            p.setArchiveStatus("ARCHIVED");
        }
        personnelRepository.save(p);
        return ResponseEntity.ok(convertToDto(p));
    }

    private String validatePersonnel(PersonnelDto dto, boolean isEdit) {
        if (dto == null) return "البيانات فارغة";

        // Check registrationNumber
        if (!isEdit) {
            if (dto.getRegistrationNumber() == null || dto.getRegistrationNumber().trim().isEmpty()) {
                return "المعرف الوحيد (الماتريكول) إجباري";
            }
            if (!dto.getRegistrationNumber().trim().matches("^[a-zA-Z0-9]{4,10}$")) {
                return "المعرف الوحيد يجب أن يتكون من 4 إلى 10 رموز وأرقام وبدون فراغات";
            }
            if (personnelRepository.findByRegistrationNumber(dto.getRegistrationNumber().trim()).isPresent()) {
                return "المعرف الوحيد (الماتريكول) مستعمل بالفعل";
            }
        }

        // Arabic Names
        if (dto.getFirstNameAr() == null || dto.getFirstNameAr().trim().isEmpty()) {
            return "الاسم باللغة العربية إجباري";
        }
        if (!dto.getFirstNameAr().trim().matches("^[\\u0600-\\u06FF\\s]{1,20}$")) {
            return "الاسم باللغة العربية يجب أن يحتوي على حروف عربية فقط ولا يتجاوز 20 حرفاً";
        }

        if (dto.getFatherNameAr() == null || dto.getFatherNameAr().trim().isEmpty()) {
            return "اسم الأب باللغة العربية إجباري";
        }
        if (!dto.getFatherNameAr().trim().matches("^[\\u0600-\\u06FF\\s]{1,20}$")) {
            return "اسم الأب باللغة العربية يجب أن يحتوي على حروف عربية فقط ولا يتجاوز 20 حرفاً";
        }

        if (dto.getLastNameAr() == null || dto.getLastNameAr().trim().isEmpty()) {
            return "اللقب باللغة العربية إجباري";
        }
        if (!dto.getLastNameAr().trim().matches("^[\\u0600-\\u06FF\\s]{1,20}$")) {
            return "اللقب باللغة العربية يجب أن يحتوي على حروف عربية فقط ولا يتجاوز 20 حرفاً";
        }

        // French Names (optional)
        if (dto.getFirstNameFr() != null && !dto.getFirstNameFr().trim().isEmpty()) {
            if (!dto.getFirstNameFr().trim().matches("^[a-zA-Z\\s\\-]{1,30}$")) {
                return "الاسم باللغة الفرنسية يجب أن يحتوي على حروف لاتينية فقط ولا يتجاوز 30 حرفاً";
            }
        }
        if (dto.getLastNameFr() != null && !dto.getLastNameFr().trim().isEmpty()) {
            if (!dto.getLastNameFr().trim().matches("^[a-zA-Z\\s\\-]{1,30}$")) {
                return "اللقب باللغة الفرنسية يجب أن يحتوي على حروف لاتينية فقط ولا يتجاوز 30 حرفاً";
            }
        }

        // Age check
        if (dto.getDateOfBirth() == null) {
            return "تاريخ الولادة إجباري";
        }
        java.time.LocalDate dob = dto.getDateOfBirth();
        java.time.LocalDate today = java.time.LocalDate.now();
        int age = java.time.Period.between(dob, today).getYears();
        if (age < 18 || age > 60) {
            return "يجب أن يكون السن بين 18 و 60 سنة";
        }

        // Phones
        if (dto.getPhoneNumber() != null && !dto.getPhoneNumber().trim().isEmpty()) {
            if (!dto.getPhoneNumber().trim().matches("^\\d{8}$")) {
                return "رقم الهاتف يجب أن يتكون من 8 أرقام بالضبط";
            }
        }
        if (dto.getPhoneNumber2() != null && !dto.getPhoneNumber2().trim().isEmpty()) {
            if (!dto.getPhoneNumber2().trim().matches("^\\d{8}$")) {
                return "رقم الهاتف الثاني يجب أن يتكون من 8 أرقام بالضبط";
            }
        }
        if (dto.getEmergencyPhone() != null && !dto.getEmergencyPhone().trim().isEmpty()) {
            if (!dto.getEmergencyPhone().trim().matches("^\\d{8}$")) {
                return "رقم هاتف الطوارئ يجب أن يتكون من 8 أرقام بالضبط";
            }
        }

        // Email
        if (dto.getEmail() != null && !dto.getEmail().trim().isEmpty()) {
            if (!dto.getEmail().trim().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
                return "البريد الإلكتروني غير صالح";
            }
        }

        // Children
        if (dto.getChildren() != null) {
            for (ChildDto c : dto.getChildren()) {
                if (c.getName() == null || c.getName().trim().isEmpty()) {
                    return "اسم الابن/الابنة إجباري";
                }
                boolean validName = c.getName().trim().matches("^[\\u0600-\\u06FF\\s]+$") || c.getName().trim().matches("^[a-zA-Z\\s\\-]+$");
                if (!validName) {
                    return "اسم الابن/الابنة يجب أن يحتوي على حروف فقط";
                }
                if (c.getBirthDate() == null) {
                    return "تاريخ ولادة الابن/الابنة إجباري";
                }
                if (c.getBirthDate().isAfter(today)) {
                    return "تاريخ ولادة الابن/الابنة لا يمكن أن يكون في المستقبل";
                }
            }
        }

        // Stages
        if (dto.getStages() != null) {
            for (PersonnelStageDto s : dto.getStages()) {
                if (s.getTitle() == null || s.getTitle().trim().isEmpty()) {
                    return "عنوان التربص إجباري";
                }
                if (s.getStartDate() == null) {
                    return "تاريخ بداية التربص إجباري";
                }
                if (s.getStartDate().isAfter(today)) {
                    return "تاريخ بداية التربص لا يمكن أن يكون في المستقبل";
                }
                if (s.getDuration() == null || s.getDuration().trim().isEmpty()) {
                    return "مدة التربص إجبارية";
                }
                if (s.getInstitution() == null || s.getInstitution().trim().isEmpty()) {
                    return "مكان/مؤسسة التربص إجبارية";
                }
            }
        }

        return null;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> deletePersonnel(@PathVariable Long id) {
        Optional<Personnel> pOpt = personnelRepository.findById(id);
        if (pOpt.isEmpty()) return ResponseEntity.notFound().build();

        Personnel p = pOpt.get();

        // 1. Delete associated academic backgrounds
        List<AcademicBackground> academics = academicBackgroundRepository.findByPersonnelId(id);
        academicBackgroundRepository.deleteAll(academics);

        // 2. Delete associated career progression details
        List<CareerProgression> careers = careerProgressionRepository.findByPersonnelIdOrderByPromotionDateDesc(id);
        careerProgressionRepository.deleteAll(careers);

        // 3. Delete associated leave requests
        List<LeaveRequest> leaves = leaveRequestRepository.findByPersonnelId(id);
        leaveRequestRepository.deleteAll(leaves);

        // 4. Delete associated personnel trainings
        List<PersonnelTraining> trainings = personnelTrainingRepository.findByPersonnelId(id);
        personnelTrainingRepository.deleteAll(trainings);

        // 5. Delete user account s'il existe et délier
        if (p.getUserAccount() != null) {
            UserAccount account = p.getUserAccount();
            p.setUserAccount(null);
            personnelRepository.save(p);
            userAccountRepository.delete(account);
        }

        // 6. Delete personnel
        personnelRepository.delete(p);
        return ResponseEntity.ok().build();
    }
}
