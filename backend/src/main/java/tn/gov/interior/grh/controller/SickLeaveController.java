package tn.gov.interior.grh.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.gov.interior.grh.dto.SickLeaveDto;
import tn.gov.interior.grh.model.*;
import tn.gov.interior.grh.repository.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sick-leaves")
public class SickLeaveController {

    @Autowired
    private SickLeaveRepository sickLeaveRepository;

    @Autowired
    private PersonnelRepository personnelRepository;

    @Autowired
    private OrganizationalUnitRepository organizationalUnitRepository;

    @Autowired
    private AnnualLeaveRepository annualLeaveRepository;

    @Autowired
    private ExceptionalLeaveRepository exceptionalLeaveRepository;

    @GetMapping
    public ResponseEntity<List<SickLeaveDto>> getSickLeaves() {
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<Personnel> currentPersonnelOpt = getPersonnelFromPrincipal(principal);

        boolean isAllAccessRole = principal.getAuthorities().stream().anyMatch(a -> 
            a.getAuthority().equals("ROLE_SUPER_ADMIN") || 
            a.getAuthority().equals("ROLE_ADMIN_DIRECTION") || 
            a.getAuthority().equals("ROLE_AGENT_RH")
        );

        if (currentPersonnelOpt.isEmpty()) {
            if (isAllAccessRole) {
                return ResponseEntity.ok(sickLeaveRepository.findAll().stream().map(this::convertToDto).collect(Collectors.toList()));
            }
            return ResponseEntity.badRequest().build();
        }

        Personnel current = currentPersonnelOpt.get();
        List<SickLeave> sickLeaves;

        boolean isSd = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CHEF_SOUS_DIRECTION"));
        boolean isServ = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CHEF_SERVICE"));

        if (isAllAccessRole) {
            sickLeaves = sickLeaveRepository.findAll();
        } else if (isSd) {
            List<Long> unitIds = new ArrayList<>();
            unitIds.add(current.getOrganizationalUnit().getId());
            List<OrganizationalUnit> children = organizationalUnitRepository.findByParentId(current.getOrganizationalUnit().getId());
            for (OrganizationalUnit child : children) {
                unitIds.add(child.getId());
            }
            sickLeaves = sickLeaveRepository.findByPersonnelOrganizationalUnitIdIn(unitIds);
        } else if (isServ) {
            sickLeaves = sickLeaveRepository.findByPersonnelOrganizationalUnitId(current.getOrganizationalUnit().getId());
        } else {
            // Normal user: only their own sick leaves
            sickLeaves = sickLeaveRepository.findByPersonnelId(current.getId());
        }

        List<SickLeaveDto> dtos = sickLeaves.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_AGENT_RH', 'ROLE_ADMIN_DIRECTION', 'ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> createSickLeave(@RequestBody SickLeaveDto dto) {
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (dto.getPersonnelId() == null) {
            return ResponseEntity.badRequest().body("يجب تحديد الموظف المعني");
        }

        Optional<Personnel> targetOpt = personnelRepository.findById(dto.getPersonnelId());
        if (targetOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("الموظف غير موجود");
        }

        Personnel targetPersonnel = targetOpt.get();

        if (dto.getStartDate() == null) {
            return ResponseEntity.badRequest().body("تاريخ البداية إجباري");
        }
        if (dto.getEndDate() == null) {
            return ResponseEntity.badRequest().body("تاريخ النهاية إجباري");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            return ResponseEntity.badRequest().body("تاريخ النهاية لا يمكن أن يكون قبل تاريخ البداية");
        }
        // Vérification du chevauchement de congés (Annual, Exceptional & Sick)
        if (hasOverlap(targetPersonnel.getId(), dto.getStartDate(), dto.getEndDate())) {
            return ResponseEntity.badRequest().body("الموظف لديه إجازة أخرى أو طلب إجازة قيد الدراسة في نفس الفترة.");
        }

        // Generate leaveCode
        String leaveCode = generateNextLeaveCode();

        SickLeave sickLeave = SickLeave.builder()
                .leaveCode(leaveCode)
                .personnel(targetPersonnel)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .justification(dto.getJustification())
                .documentPath(dto.getDocumentPath())
                .createdBy(principal.getUsername())
                .build();

        SickLeave saved = sickLeaveRepository.save(sickLeave);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_AGENT_RH', 'ROLE_ADMIN_DIRECTION', 'ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> extendSickLeave(@PathVariable Long id, @RequestBody SickLeaveDto dto) {
        Optional<SickLeave> sickLeaveOpt = sickLeaveRepository.findById(id);
        if (sickLeaveOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SickLeave sickLeave = sickLeaveOpt.get();

        // Only allow extending the end date and adding notes - NOT changing the personnel
        if (dto.getEndDate() != null) {
            if (dto.getEndDate().isBefore(sickLeave.getStartDate())) {
                return ResponseEntity.badRequest().body("تاريخ النهاية الجديد لا يمكن أن يكون قبل تاريخ البداية");
            }
            sickLeave.setEndDate(dto.getEndDate());
        }

        if (dto.getJustification() != null) {
            sickLeave.setJustification(dto.getJustification());
        }

        if (dto.getDocumentPath() != null) {
            sickLeave.setDocumentPath(dto.getDocumentPath());
        }

        if (dto.getExtensionNotes() != null) {
            // Append extension notes with timestamp
            String existingNotes = sickLeave.getExtensionNotes() != null ? sickLeave.getExtensionNotes() : "";
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String newNote = "[" + timestamp + "] " + dto.getExtensionNotes();
            sickLeave.setExtensionNotes(existingNotes.isEmpty() ? newNote : existingNotes + "\n" + newNote);
        }

        SickLeave saved = sickLeaveRepository.save(sickLeave);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_CHEF_SERVICE', 'ROLE_CHEF_SOUS_DIRECTION', 'ROLE_ADMIN_DIRECTION', 'ROLE_AGENT_RH')")
    @Transactional
    public ResponseEntity<?> updateLeaveStatus(@PathVariable Long id, @RequestParam("status") String status, @RequestParam(value = "justification", required = false) String justification) {
        Optional<SickLeave> requestOpt = sickLeaveRepository.findById(id);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SickLeave request = requestOpt.get();
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        boolean isAgentRh = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_AGENT_RH"));

        if (isAgentRh) {
            List<String> allowedAgentRhStatuses = Arrays.asList("LEAVE_STARTED", "WORK_RESUMED");
            if (!allowedAgentRhStatuses.contains(status)) {
                return ResponseEntity.badRequest().body("ليس لديك الصلاحية لتغيير هذه الحالة.");
            }
        }
        
        // If resuming work early, adjust the end date to yesterday to reflect actual return date
        if ("WORK_RESUMED".equals(status)) {
            java.time.LocalDate today = java.time.LocalDate.now();
            if (request.getEndDate() != null && request.getEndDate().isAfter(today)) {
                request.setEndDate(today.minusDays(1));
            }
        }

        request.setStatus(status);
        if (justification != null) {
            request.setJustification(justification);
        }

        SickLeave updated = sickLeaveRepository.save(request);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> deleteSickLeave(@PathVariable Long id) {
        if (sickLeaveRepository.existsById(id)) {
            sickLeaveRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    private String generateNextLeaveCode() {
        Optional<SickLeave> lastLeave = sickLeaveRepository.findTopByOrderByIdDesc();
        if (lastLeave.isPresent() && lastLeave.get().getLeaveCode() != null) {
            try {
                int lastCode = Integer.parseInt(lastLeave.get().getLeaveCode());
                return String.format("%05d", lastCode + 1);
            } catch (NumberFormatException e) {
                // fallback
            }
        }
        return "00001";
    }

    private Optional<Personnel> getPersonnelFromPrincipal(UserDetails principal) {
        return personnelRepository.findAll().stream()
                .filter(p -> p.getUserAccount() != null && p.getUserAccount().getUsername().equals(principal.getUsername()))
                .findFirst();
    }

    private SickLeaveDto convertToDto(SickLeave sl) {
        long duration = 0;
        if (sl.getStartDate() != null && sl.getEndDate() != null) {
            duration = java.time.temporal.ChronoUnit.DAYS.between(sl.getStartDate(), sl.getEndDate()) + 1;
        }
        java.time.LocalDate returnDate = sl.getEndDate() != null ? sl.getEndDate().plusDays(1) : null;

        return SickLeaveDto.builder()
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
                .startDate(sl.getStartDate())
                .endDate(sl.getEndDate())
                .justification(sl.getJustification())
                .documentPath(sl.getDocumentPath())
                .createdBy(sl.getCreatedBy())
                .extensionNotes(sl.getExtensionNotes())
                .duration(duration)
                .returnDate(returnDate)
                .status(sl.getStatus())
                .build();
    }

    private boolean hasOverlap(Long personnelId, java.time.LocalDate start, java.time.LocalDate end) {
        // Check annual leaves
        List<AnnualLeave> annualList = annualLeaveRepository.findByPersonnelId(personnelId);
        for (AnnualLeave existing : annualList) {
            if (!"REJECTED".equals(existing.getStatus())) {
                if (existing.getStartDate() != null && existing.getEndDate() != null) {
                    if (!existing.getStartDate().isAfter(end) && !existing.getEndDate().isBefore(start)) {
                        return true;
                    }
                }
            }
        }
        // Check exceptional leaves
        List<ExceptionalLeave> exceptionalList = exceptionalLeaveRepository.findByPersonnelId(personnelId);
        for (ExceptionalLeave existing : exceptionalList) {
            if (!"REJECTED".equals(existing.getStatus())) {
                if (existing.getStartDate() != null && existing.getEndDate() != null) {
                    if (!existing.getStartDate().isAfter(end) && !existing.getEndDate().isBefore(start)) {
                        return true;
                    }
                }
            }
        }
        // Check sick leaves
        List<SickLeave> sickList = sickLeaveRepository.findByPersonnelId(personnelId);
        for (SickLeave existing : sickList) {
            if (existing.getStartDate() != null && existing.getEndDate() != null) {
                if (!existing.getStartDate().isAfter(end) && !existing.getEndDate().isBefore(start)) {
                    return true;
                }
            }
        }
        return false;
    }
}
