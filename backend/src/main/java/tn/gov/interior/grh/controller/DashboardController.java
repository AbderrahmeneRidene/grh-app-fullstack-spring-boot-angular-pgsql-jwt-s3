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
    private AnnualLeaveRepository annualLeaveRepository;

    @Autowired
    private ExceptionalLeaveRepository exceptionalLeaveRepository;

    @Autowired
    private SickLeaveRepository sickLeaveRepository;

    @Autowired
    private TrainingRepository trainingRepository;

    @GetMapping("/stats")
    public DashboardStatsDto getDashboardStats() {
        LocalDate today = LocalDate.now();

        List<Personnel> personnelList = personnelRepository.findAll();
        List<OrganizationalUnit> unitList = organizationalUnitRepository.findAll();
        List<AnnualLeave> annualLeaveList = annualLeaveRepository.findAll();
        List<ExceptionalLeave> exceptionalLeaveList = exceptionalLeaveRepository.findAll();
        List<SickLeave> sickLeaveList = sickLeaveRepository.findAll();
        List<Training> trainingList = trainingRepository.findAll();

        long totalPersonnel = personnelList.size();
        long totalUnits = unitList.size();

        // Calcul des congés actifs aujourd'hui (Annual + Exceptional + Sick)
        long activeAnnualCount = annualLeaveList.stream()
                .filter(lr -> (lr.getStatus().equals("APPROVED") || lr.getStatus().equals("LEAVE_STARTED") || lr.getStatus().equals("PENDING_MODIFICATION") || lr.getStatus().equals("PENDING_DELETION")) &&
                        !today.isBefore(lr.getStartDate()) &&
                        !today.isAfter(lr.getEndDate()))
                .count();

        long activeExceptionalCount = exceptionalLeaveList.stream()
                .filter(lr -> (lr.getStatus().equals("APPROVED") || lr.getStatus().equals("LEAVE_STARTED") || lr.getStatus().equals("PENDING_MODIFICATION") || lr.getStatus().equals("PENDING_DELETION")) &&
                        !today.isBefore(lr.getStartDate()) &&
                        !today.isAfter(lr.getEndDate()))
                .count();

        long activeSickLeavesCount = sickLeaveList.stream()
                .filter(sl -> !today.isBefore(sl.getStartDate()) && !today.isAfter(sl.getEndDate()))
                .count();

        long activeLeaves = activeAnnualCount + activeExceptionalCount + activeSickLeavesCount;

        // Calcul des formations actives aujourd'hui
        long activeTrainings = trainingList.stream()
                .filter(t -> !today.isBefore(t.getStartDate()) && !today.isAfter(t.getEndDate()))
                .count();

        // Congés par type
        Map<String, Long> leavesByType = new HashMap<>();
        leavesByType.put("ANNUEL", (long) annualLeaveList.size());
        leavesByType.put("EXCEPTIONNEL", (long) exceptionalLeaveList.size());
        leavesByType.put("MALADIE", (long) sickLeaveList.size());

        // Personnel par Grade
        Map<String, Long> personnelByGrade = personnelList.stream()
                .collect(Collectors.groupingBy(Personnel::getGrade, Collectors.counting()));

        // Personnel par Unité Organisationnelle
        Map<String, Long> personnelByOrgUnit = personnelList.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getOrganizationalUnit().getNameAr(),
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

        List<LeaveRequestDto> activeAnnualLeaves = annualLeaveList.stream()
                .filter(lr -> ("APPROVED".equals(lr.getStatus()) || "LEAVE_STARTED".equals(lr.getStatus()) || "PENDING_MODIFICATION".equals(lr.getStatus()) || "PENDING_DELETION".equals(lr.getStatus())) &&
                        !today.isBefore(lr.getStartDate()) &&
                        !today.isAfter(lr.getEndDate()))
                .map(this::convertAnnualLeaveToDto)
                .collect(Collectors.toList());

        List<LeaveRequestDto> activeExceptionalLeaves = exceptionalLeaveList.stream()
                .filter(lr -> ("APPROVED".equals(lr.getStatus()) || "LEAVE_STARTED".equals(lr.getStatus()) || "PENDING_MODIFICATION".equals(lr.getStatus()) || "PENDING_DELETION".equals(lr.getStatus())) &&
                        !today.isBefore(lr.getStartDate()) &&
                        !today.isAfter(lr.getEndDate()))
                .map(this::convertExceptionalLeaveToDto)
                .collect(Collectors.toList());

        List<LeaveRequestDto> activeSickLeaves = sickLeaveList.stream()
                .filter(sl -> !today.isBefore(sl.getStartDate()) && !today.isAfter(sl.getEndDate()))
                .map(this::convertSickLeaveToDto)
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
                .activeExceptionalLeaves(activeExceptionalLeaves)
                .activeSickLeaves(activeSickLeaves)
                .build();
    }

    private LeaveRequestDto convertSickLeaveToDto(SickLeave sl) {
        double duration = 0.0;
        if (sl.getStartDate() != null && sl.getEndDate() != null) {
            duration = java.time.temporal.ChronoUnit.DAYS.between(sl.getStartDate(), sl.getEndDate()) + 1.0;
        }
        LocalDate returnDate = sl.getEndDate() != null ? sl.getEndDate().plusDays(1) : null;

        return LeaveRequestDto.builder()
                .id(sl.getId())
                .leaveCode(sl.getLeaveCode())
                .personnelId(sl.getPersonnel().getId())
                .personnelRegistrationNumber(sl.getPersonnel().getRegistrationNumber())
                .personnelProfilePicture(sl.getPersonnel().getProfilePicture())
                .personnelGender(sl.getPersonnel().getGender())
                .personnelFirstNameAr(sl.getPersonnel().getFirstNameAr())
                .personnelFatherNameAr(sl.getPersonnel().getFatherNameAr())
                .personnelLastNameAr(sl.getPersonnel().getLastNameAr())
                .personnelFullNameAr(sl.getPersonnel().getFirstNameAr() + " " + sl.getPersonnel().getLastNameAr())
                .personnelFullNameFr(sl.getPersonnel().getFirstNameFr() + " " + sl.getPersonnel().getLastNameFr())
                .personnelGrade(sl.getPersonnel().getGrade())
                .personnelOrgUnitNameAr(sl.getPersonnel().getOrganizationalUnit().getNameAr())
                .personnelOrgUnitNameFr(sl.getPersonnel().getOrganizationalUnit().getNameFr())
                .leaveType("MALADIE")
                .startDate(sl.getStartDate())
                .endDate(sl.getEndDate())
                .status("APPROVED")
                .justification(sl.getJustification())
                .documentPath(sl.getDocumentPath())
                .duration(duration)
                .returnDate(returnDate)
                .build();
    }

    private LeaveRequestDto convertAnnualLeaveToDto(AnnualLeave al) {
        double duration = 0.0;
        if (al.getStartDate() != null && al.getEndDate() != null) {
            duration = java.time.temporal.ChronoUnit.DAYS.between(al.getStartDate(), al.getEndDate()) + 1.0;
        }
        LocalDate returnDate = al.getEndDate() != null ? al.getEndDate().plusDays(1) : null;

        return LeaveRequestDto.builder()
                .id(al.getId())
                .leaveCode(al.getLeaveCode())
                .personnelId(al.getPersonnel().getId())
                .personnelRegistrationNumber(al.getPersonnel().getRegistrationNumber())
                .personnelProfilePicture(al.getPersonnel().getProfilePicture())
                .personnelGender(al.getPersonnel().getGender())
                .personnelFirstNameAr(al.getPersonnel().getFirstNameAr())
                .personnelFatherNameAr(al.getPersonnel().getFatherNameAr())
                .personnelLastNameAr(al.getPersonnel().getLastNameAr())
                .personnelFullNameAr(al.getPersonnel().getFirstNameAr() + " " + al.getPersonnel().getLastNameAr())
                .personnelFullNameFr(al.getPersonnel().getFirstNameFr() + " " + al.getPersonnel().getLastNameFr())
                .personnelGrade(al.getPersonnel().getGrade())
                .personnelOrgUnitNameAr(al.getPersonnel().getOrganizationalUnit().getNameAr())
                .personnelOrgUnitNameFr(al.getPersonnel().getOrganizationalUnit().getNameFr())
                .leaveType("ANNUEL")
                .startDate(al.getStartDate())
                .endDate(al.getEndDate())
                .status(al.getStatus())
                .justification(al.getJustification())
                .documentPath(al.getDocumentPath())
                .duration(duration)
                .returnDate(returnDate)
                .build();
    }

    private LeaveRequestDto convertExceptionalLeaveToDto(ExceptionalLeave el) {
        LocalDate returnDate = el.getEndDate() != null ? el.getEndDate().plusDays(1) : null;

        return LeaveRequestDto.builder()
                .id(el.getId())
                .leaveCode(el.getLeaveCode())
                .personnelId(el.getPersonnel().getId())
                .personnelRegistrationNumber(el.getPersonnel().getRegistrationNumber())
                .personnelProfilePicture(el.getPersonnel().getProfilePicture())
                .personnelGender(el.getPersonnel().getGender())
                .personnelFirstNameAr(el.getPersonnel().getFirstNameAr())
                .personnelFatherNameAr(el.getPersonnel().getFatherNameAr())
                .personnelLastNameAr(el.getPersonnel().getLastNameAr())
                .personnelFullNameAr(el.getPersonnel().getFirstNameAr() + " " + el.getPersonnel().getLastNameAr())
                .personnelFullNameFr(el.getPersonnel().getFirstNameFr() + " " + el.getPersonnel().getLastNameFr())
                .personnelGrade(el.getPersonnel().getGrade())
                .personnelOrgUnitNameAr(el.getPersonnel().getOrganizationalUnit().getNameAr())
                .personnelOrgUnitNameFr(el.getPersonnel().getOrganizationalUnit().getNameFr())
                .leaveType("EXCEPTIONNEL")
                .startDate(el.getStartDate())
                .endDate(el.getEndDate())
                .status(el.getStatus())
                .justification(el.getJustification())
                .documentPath(el.getDocumentPath())
                .duration(el.getDuration() != null ? el.getDuration() : 1.0)
                .returnDate(returnDate)
                .exceptionalLeaveType(el.getExceptionalLeaveType())
                .session(el.getSession())
                .startTime(el.getStartTime())
                .build();
    }
}
