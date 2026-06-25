package tn.gov.interior.grh.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.gov.interior.grh.dto.TrainingDto;
import tn.gov.interior.grh.dto.PersonnelTrainingDto;
import tn.gov.interior.grh.model.*;
import tn.gov.interior.grh.repository.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trainings")
public class TrainingController {

    @Autowired
    private TrainingRepository trainingRepository;

    @Autowired
    private PersonnelTrainingRepository personnelTrainingRepository;

    @Autowired
    private PersonnelRepository personnelRepository;

    @GetMapping
    public List<TrainingDto> getAllTrainings() {
        return trainingRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_CHEF_SOUS_DIRECTION')")
    public ResponseEntity<?> createTraining(@RequestBody TrainingDto dto) {
        String validationError = validateTraining(dto);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }
        Training training = Training.builder()
                .titleAr(dto.getTitleAr())
                .titleFr(dto.getTitleFr())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .institution(dto.getInstitution())
                .build();
        Training saved = trainingRepository.save(training);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CHEF_SOUS_DIRECTION')")
    public ResponseEntity<?> updateTraining(@PathVariable Long id, @RequestBody TrainingDto dto) {
        String validationError = validateTraining(dto);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }
        Optional<Training> trainingOpt = trainingRepository.findById(id);
        if (trainingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Training training = trainingOpt.get();
        training.setTitleAr(dto.getTitleAr());
        training.setTitleFr(dto.getTitleFr());
        training.setStartDate(dto.getStartDate());
        training.setEndDate(dto.getEndDate());
        training.setInstitution(dto.getInstitution());
        Training saved = trainingRepository.save(training);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @GetMapping("/{id}/personnel")
    public List<PersonnelTrainingDto> getEnrolledPersonnel(@PathVariable Long id) {
        return personnelTrainingRepository.findByTrainingId(id).stream()
                .map(this::convertToPersonnelTrainingDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/enroll")
    @PreAuthorize("hasRole('ROLE_AGENT_RH')")
    public ResponseEntity<?> enrollPersonnel(@PathVariable("id") Long trainingId, @RequestParam("personnelId") Long personnelId) {
        Optional<Training> trainingOpt = trainingRepository.findById(trainingId);
        Optional<Personnel> personnelOpt = personnelRepository.findById(personnelId);

        if (trainingOpt.isEmpty() || personnelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PersonnelTraining enrollment = PersonnelTraining.builder()
                .personnel(personnelOpt.get())
                .training(trainingOpt.get())
                .status("EN_COURS")
                .build();

        PersonnelTraining saved = personnelTrainingRepository.save(enrollment);
        return ResponseEntity.ok(convertToPersonnelTrainingDto(saved));
    }

    @PutMapping("/enroll/{enrollmentId}/status")
    @PreAuthorize("hasRole('ROLE_CHEF_SOUS_DIRECTION')")
    @Transactional
    public ResponseEntity<?> updateEnrollmentStatus(@PathVariable Long enrollmentId, @RequestParam("status") String status, @RequestParam(value = "evaluation", required = false) String evaluation) {
        Optional<PersonnelTraining> enrollmentOpt = personnelTrainingRepository.findById(enrollmentId);
        if (enrollmentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PersonnelTraining enrollment = enrollmentOpt.get();
        enrollment.setStatus(status);
        if (evaluation != null) {
            enrollment.setEvaluation(evaluation);
        }

        PersonnelTraining updated = personnelTrainingRepository.save(enrollment);
        return ResponseEntity.ok(convertToPersonnelTrainingDto(updated));
    }

    @GetMapping("/personnel/{personnelId}")
    public List<PersonnelTrainingDto> getPersonnelTrainings(@PathVariable Long personnelId) {
        return personnelTrainingRepository.findByPersonnelId(personnelId).stream()
                .map(this::convertToPersonnelTrainingDto)
                .collect(Collectors.toList());
    }

    private TrainingDto convertToDto(Training t) {
        return TrainingDto.builder()
                .id(t.getId())
                .titleAr(t.getTitleAr())
                .titleFr(t.getTitleFr())
                .startDate(t.getStartDate())
                .endDate(t.getEndDate())
                .institution(t.getInstitution())
                .build();
    }

    private PersonnelTrainingDto convertToPersonnelTrainingDto(PersonnelTraining pt) {
        return PersonnelTrainingDto.builder()
                .id(pt.getId())
                .personnelId(pt.getPersonnel().getId())
                .personnelFullNameAr(pt.getPersonnel().getFirstNameAr() + " " + pt.getPersonnel().getLastNameAr())
                .personnelFullNameFr(pt.getPersonnel().getFirstNameFr() + " " + pt.getPersonnel().getLastNameFr())
                .trainingId(pt.getTraining().getId())
                .trainingTitleAr(pt.getTraining().getTitleAr())
                .trainingTitleFr(pt.getTraining().getTitleFr())
                .status(pt.getStatus())
                .evaluation(pt.getEvaluation())
                .build();
    }
    private String validateTraining(TrainingDto dto) {
        if (dto == null) return "البيانات فارغة";

        if (dto.getTitleAr() == null || dto.getTitleAr().trim().isEmpty()) {
            return "اسم الدورة باللغة العربية إجباري";
        }
        if (!dto.getTitleAr().trim().matches("^[\\u0600-\\u06FF\\s0-9\\-\\(\\)\\[\\]\\.\\,]{1,100}$")) {
            return "اسم الدورة باللغة العربية يجب أن يحتوي على حروف عربية فقط ولا يتجاوز 100 حرفاً";
        }

        if (dto.getTitleFr() == null || dto.getTitleFr().trim().isEmpty()) {
            return "اسم الدورة باللغة الفرنسية إجباري";
        }
        if (!dto.getTitleFr().trim().matches("^[a-zA-Z\\s0-9\\-\\(\\)\\[\\]\\.\\,\\'\\u00C0-\\u00FF]{1,100}$")) {
            return "اسم الدورة باللغة الفرنسية يجب أن يحتوي على حروف لاتينية فقط ولا يتجاوز 100 حرفاً";
        }

        if (dto.getStartDate() == null) {
            return "تاريخ البداية إجباري";
        }
        if (dto.getEndDate() == null) {
            return "تاريخ النهاية إجباري";
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            return "تاريخ النهاية لا يمكن أن يكون قبل تاريخ البداية";
        }

        if (dto.getInstitution() == null || dto.getInstitution().trim().isEmpty()) {
            return "مقر ومؤسسة التكوين إجبارية";
        }
        if (dto.getInstitution().trim().length() > 100) {
            return "اسم مقر التكوين لا يجب أن يتجاوز 100 حرفاً";
        }

        return null;
    }
}
