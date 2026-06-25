package tn.gov.interior.grh.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import tn.gov.interior.grh.dto.*;
import tn.gov.interior.grh.model.*;
import tn.gov.interior.grh.repository.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private PersonnelRepository personnelRepository;

    @Autowired
    private OrganizationalUnitRepository organizationalUnitRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private TrainingRepository trainingRepository;

    @GetMapping("/stats")
    public DashboardStatsDto getDashboardStats() {
        LocalDate today = LocalDate.now();

        List<Personnel> personnelList = personnelRepository.findAll();
        List<OrganizationalUnit> unitList = organizationalUnitRepository.findAll();
        List<LeaveRequest> leaveList = leaveRequestRepository.findAll();
        List<Training> trainingList = trainingRepository.findAll();

        long totalPersonnel = personnelList.size();
        long totalUnits = unitList.size();

        // Calcul des congés actifs aujourd'hui
        long activeLeaves = leaveList.stream()
                .filter(lr -> lr.getStatus().equals("APPROVED") &&
                        !today.isBefore(lr.getStartDate()) &&
                        !today.isAfter(lr.getEndDate()))
                .count();

        // Calcul des formations actives aujourd'hui
        long activeTrainings = trainingList.stream()
                .filter(t -> !today.isBefore(t.getStartDate()) && !today.isAfter(t.getEndDate()))
                .count();

        // Congés par type
        Map<String, Long> leavesByType = leaveList.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getLeaveType, Collectors.counting()));

        // Personnel par Grade
        Map<String, Long> personnelByGrade = personnelList.stream()
                .collect(Collectors.groupingBy(Personnel::getGrade, Collectors.counting()));

        // Personnel par Unité Organisationnelle
        Map<String, Long> personnelByOrgUnit = personnelList.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getOrganizationalUnit().getNameAr(), // Nom en arabe pour l'affichage
                        Collectors.counting()
                ));

        // Filtrer le personnel actif
        List<Personnel> activePersonnelList = personnelList.stream()
                .filter(p -> p.getArchiveStatus() == null || "ACTIVE".equals(p.getArchiveStatus()) || "PENDING_ARCHIVE".equals(p.getArchiveStatus()))
                .collect(Collectors.toList());

        long activeMaleCount = activePersonnelList.stream()
                .filter(p -> "MALE".equalsIgnoreCase(p.getGender()))
                .count();

        long activeFemaleCount = activePersonnelList.stream()
                .filter(p -> "FEMALE".equalsIgnoreCase(p.getGender()))
                .count();

        // Distribution des âges
        Map<String, Long> activePersonnelByAgeGroup = new LinkedHashMap<>();
        activePersonnelByAgeGroup.put("20-25", 0L);
        activePersonnelByAgeGroup.put("25-30", 0L);
        activePersonnelByAgeGroup.put("30-35", 0L);
        activePersonnelByAgeGroup.put("35-40", 0L);
        activePersonnelByAgeGroup.put("40-45", 0L);
        activePersonnelByAgeGroup.put("45-50", 0L);
        activePersonnelByAgeGroup.put("50+", 0L);

        for (Personnel p : activePersonnelList) {
            if (p.getDateOfBirth() != null) {
                int age = java.time.Period.between(p.getDateOfBirth(), LocalDate.now()).getYears();
                if (age >= 20 && age <= 25) {
                    activePersonnelByAgeGroup.put("20-25", activePersonnelByAgeGroup.get("20-25") + 1);
                } else if (age > 25 && age <= 30) {
                    activePersonnelByAgeGroup.put("25-30", activePersonnelByAgeGroup.get("25-30") + 1);
                } else if (age > 30 && age <= 35) {
                    activePersonnelByAgeGroup.put("30-35", activePersonnelByAgeGroup.get("30-35") + 1);
                } else if (age > 35 && age <= 40) {
                    activePersonnelByAgeGroup.put("35-40", activePersonnelByAgeGroup.get("35-40") + 1);
                } else if (age > 40 && age <= 45) {
                    activePersonnelByAgeGroup.put("40-45", activePersonnelByAgeGroup.get("40-45") + 1);
                } else if (age > 45 && age <= 50) {
                    activePersonnelByAgeGroup.put("45-50", activePersonnelByAgeGroup.get("45-50") + 1);
                } else if (age > 50) {
                    activePersonnelByAgeGroup.put("50+", activePersonnelByAgeGroup.get("50+") + 1);
                }
            }
        }

        List<LeaveRequestDto> activeAnnualLeaves = leaveList.stream()
                .filter(lr -> "APPROVED".equals(lr.getStatus()) &&
                        "ANNUEL".equals(lr.getLeaveType()) &&
                        !today.isBefore(lr.getStartDate()) &&
                        !today.isAfter(lr.getEndDate()))
                .map(this::convertToDto)
                .collect(Collectors.toList());

        List<LeaveRequestDto> activeSickLeaves = leaveList.stream()
                .filter(lr -> "APPROVED".equals(lr.getStatus()) &&
                        "MALADIE".equals(lr.getLeaveType()) &&
                        !today.isBefore(lr.getStartDate()) &&
                        !today.isAfter(lr.getEndDate()))
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return DashboardStatsDto.builder()
                .totalPersonnel(totalPersonnel)
                .totalUnits(totalUnits)
                .activeLeaves(activeLeaves)
                .activeTrainings(activeTrainings)
                .leavesByType(leavesByType)
                .personnelByGrade(personnelByGrade)
                .personnelByOrgUnit(personnelByOrgUnit)
                .activeMaleCount(activeMaleCount)
                .activeFemaleCount(activeFemaleCount)
                .activePersonnelByAgeGroup(activePersonnelByAgeGroup)
                .activeAnnualLeaves(activeAnnualLeaves)
                .activeSickLeaves(activeSickLeaves)
                .build();
    }

    private LeaveRequestDto convertToDto(LeaveRequest lr) {
        long duration = 0;
        if (lr.getStartDate() != null && lr.getEndDate() != null) {
            duration = java.time.temporal.ChronoUnit.DAYS.between(lr.getStartDate(), lr.getEndDate()) + 1;
        }
        LocalDate returnDate = lr.getEndDate() != null ? lr.getEndDate().plusDays(1) : null;

        return LeaveRequestDto.builder()
                .id(lr.getId())
                .personnelId(lr.getPersonnel().getId())
                .personnelRegistrationNumber(lr.getPersonnel().getRegistrationNumber())
                .personnelProfilePicture(lr.getPersonnel().getProfilePicture())
                .personnelGender(lr.getPersonnel().getGender())
                .personnelFirstNameAr(lr.getPersonnel().getFirstNameAr())
                .personnelFatherNameAr(lr.getPersonnel().getFatherNameAr())
                .personnelLastNameAr(lr.getPersonnel().getLastNameAr())
                .personnelFullNameAr(lr.getPersonnel().getFirstNameAr() + " " + lr.getPersonnel().getLastNameAr())
                .personnelFullNameFr(lr.getPersonnel().getFirstNameFr() + " " + lr.getPersonnel().getLastNameFr())
                .personnelGrade(lr.getPersonnel().getGrade())
                .personnelOrgUnitNameAr(lr.getPersonnel().getOrganizationalUnit().getNameAr())
                .personnelOrgUnitNameFr(lr.getPersonnel().getOrganizationalUnit().getNameFr())
                .leaveType(lr.getLeaveType())
                .startDate(lr.getStartDate())
                .endDate(lr.getEndDate())
                .status(lr.getStatus())
                .justification(lr.getJustification())
                .documentPath(lr.getDocumentPath())
                .duration(duration)
                .returnDate(returnDate)
                .build();
    }
}
