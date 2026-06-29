package tn.gov.interior.grh.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.gov.interior.grh.dto.AnnualLeaveDto;
import tn.gov.interior.grh.model.*;
import tn.gov.interior.grh.repository.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/annual-leaves")
public class AnnualLeaveController {

    @Autowired
    private AnnualLeaveRepository annualLeaveRepository;

    @Autowired
    private ExceptionalLeaveRepository exceptionalLeaveRepository;

    @Autowired
    private SickLeaveRepository sickLeaveRepository;

    @Autowired
    private PersonnelRepository personnelRepository;

    @Autowired
    private OrganizationalUnitRepository organizationalUnitRepository;

    @GetMapping
    public ResponseEntity<List<AnnualLeaveDto>> getLeaves() {
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<Personnel> currentPersonnelOpt = getPersonnelFromPrincipal(principal);

        boolean isAllAccessRole = principal.getAuthorities().stream().anyMatch(a -> 
            a.getAuthority().equals("ROLE_SUPER_ADMIN") || 
            a.getAuthority().equals("ROLE_ADMIN_DIRECTION") || 
            a.getAuthority().equals("ROLE_AGENT_RH")
        );

        if (currentPersonnelOpt.isEmpty()) {
            if (isAllAccessRole) {
                return ResponseEntity.ok(annualLeaveRepository.findAll().stream().map(this::convertToDto).collect(Collectors.toList()));
            }
            return ResponseEntity.badRequest().build();
        }

        Personnel current = currentPersonnelOpt.get();
        List<AnnualLeave> requests = new ArrayList<>();

        boolean isSd = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CHEF_SOUS_DIRECTION"));
        boolean isServ = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CHEF_SERVICE"));

        if (isAllAccessRole) {
            requests = annualLeaveRepository.findAll();
        } else if (isSd) {
            List<Long> unitIds = new ArrayList<>();
            unitIds.add(current.getOrganizationalUnit().getId());
            List<OrganizationalUnit> children = organizationalUnitRepository.findByParentId(current.getOrganizationalUnit().getId());
            for (OrganizationalUnit child : children) {
                unitIds.add(child.getId());
            }
            requests = annualLeaveRepository.findByPersonnelOrganizationalUnitIdIn(unitIds);
        } else if (isServ) {
            requests = annualLeaveRepository.findByPersonnelOrganizationalUnitId(current.getOrganizationalUnit().getId());
        } else {
            requests = annualLeaveRepository.findByPersonnelId(current.getId());
        }

        List<AnnualLeaveDto> dtos = requests.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createLeaveRequest(@RequestBody AnnualLeaveDto dto) {
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<Personnel> currentPersonnelOpt = getPersonnelFromPrincipal(principal);

        Personnel current = currentPersonnelOpt.orElse(null);
        Personnel targetPersonnel = current;

        boolean hasPrivilegedRole = principal.getAuthorities().stream().anyMatch(a -> 
            a.getAuthority().equals("ROLE_AGENT_RH") || 
            a.getAuthority().equals("ROLE_ADMIN_DIRECTION") || 
            a.getAuthority().equals("ROLE_SUPER_ADMIN")
        );

        if (hasPrivilegedRole && dto.getPersonnelId() != null) {
            Optional<Personnel> targetOpt = personnelRepository.findById(dto.getPersonnelId());
            if (targetOpt.isPresent()) {
                targetPersonnel = targetOpt.get();
            } else {
                return ResponseEntity.badRequest().body("الموظف غير موجود");
            }
        }

        if (targetPersonnel == null) {
            return ResponseEntity.badRequest().body("Must have an associated Personnel file to request leaves");
        }

        if (dto.getStartDate() == null) {
            return ResponseEntity.badRequest().body("تاريخ البداية إجباري");
        }
        if (dto.getEndDate() == null) {
            return ResponseEntity.badRequest().body("تاريخ النهاية إجباري");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            return ResponseEntity.badRequest().body("تاريخ النهاية لا يمكن أن يكون قبل تاريخ البداية");
        }

        long duration = java.time.temporal.ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;
        if (duration <= 0) {
            return ResponseEntity.badRequest().body("المدة المطلوبة يجب أن تكون أكبر من صفر");
        }

        int balance = calculateLeaveBalance(targetPersonnel.getId());
        if (duration > balance) {
            return ResponseEntity.badRequest().body("المدة المطلوبة (" + duration + " يوم) تتجاوز الرصيد المتبقي (" + balance + " يوم)");
        }

        // Vérification du chevauchement de congés (Annual, Exceptional & Sick)
        if (hasOverlap(targetPersonnel.getId(), dto.getStartDate(), dto.getEndDate())) {
            return ResponseEntity.badRequest().body("الموظف لديه إجازة أخرى أو طلب إجازة قيد الدراسة في نفس الفترة.");
        }

        String leaveCode = generateNextLeaveCode();

        AnnualLeave request = AnnualLeave.builder()
                .leaveCode(leaveCode)
                .personnel(targetPersonnel)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status("PENDING")
                .justification(dto.getJustification())
                .documentPath(dto.getDocumentPath())
                .build();

        AnnualLeave saved = annualLeaveRepository.save(request);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_CHEF_SERVICE', 'ROLE_CHEF_SOUS_DIRECTION', 'ROLE_ADMIN_DIRECTION', 'ROLE_AGENT_RH')")
    @Transactional
    public ResponseEntity<?> updateLeaveStatus(@PathVariable Long id, @RequestParam("status") String status, @RequestParam(value = "justification", required = false) String justification) {
        Optional<AnnualLeave> requestOpt = annualLeaveRepository.findById(id);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AnnualLeave request = requestOpt.get();
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        boolean isAgentRh = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_AGENT_RH"));

        if (isAgentRh) {
            List<String> allowedAgentRhStatuses = Arrays.asList("LEAVE_STARTED", "WORK_RESUMED", "PENDING_MODIFICATION", "PENDING_DELETION");
            if (!allowedAgentRhStatuses.contains(status)) {
                return ResponseEntity.badRequest().body("ليس لديك الصلاحية لتغيير هذه الحالة.");
            }
        }
        
        request.setStatus(status);
        if (justification != null) {
            request.setJustification(justification);
        }

        AnnualLeave updated = annualLeaveRepository.save(request);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> modifyLeaveRequest(@PathVariable Long id, @RequestBody AnnualLeaveDto dto) {
        Optional<AnnualLeave> requestOpt = annualLeaveRepository.findById(id);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AnnualLeave request = requestOpt.get();
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<Personnel> currentPersonnelOpt = getPersonnelFromPrincipal(principal);
        boolean isDirector = principal.getAuthorities().stream().anyMatch(a -> 
            a.getAuthority().equals("ROLE_ADMIN_DIRECTION") || a.getAuthority().equals("ROLE_SUPER_ADMIN")
        );
        boolean isAgentRh = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_AGENT_RH"));

        boolean isOwner = currentPersonnelOpt.isPresent() && 
            request.getPersonnel().getId().equals(currentPersonnelOpt.get().getId());

        if (isOwner && !isDirector && !isAgentRh) {
            if (!"PENDING".equals(request.getStatus())) {
                return ResponseEntity.badRequest().body("لا يمكن تعديل الطلب بعد الموافقة عليه أو رفضه. يرجى التواصل مع مسؤول الموارد البشرية.");
            }
            if (dto.getStartDate() != null) request.setStartDate(dto.getStartDate());
            if (dto.getEndDate() != null) request.setEndDate(dto.getEndDate());
            if (dto.getJustification() != null) request.setJustification(dto.getJustification());
            if (dto.getDocumentPath() != null) request.setDocumentPath(dto.getDocumentPath());
            
            AnnualLeave saved = annualLeaveRepository.save(request);
            return ResponseEntity.ok(convertToDto(saved));
        }

        if (!isDirector && !isAgentRh) {
            return ResponseEntity.status(403).body("غير مسموح بتعديل هذا الطلب");
        }

        if (dto.getStartDate() != null) request.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) request.setEndDate(dto.getEndDate());
        if (dto.getJustification() != null) request.setJustification(dto.getJustification());
        if (dto.getDocumentPath() != null) request.setDocumentPath(dto.getDocumentPath());

        if (isDirector) {
            request.setStatus("APPROVED");
        } else if (isAgentRh) {
            if ("APPROVED".equals(request.getStatus()) || "LEAVE_STARTED".equals(request.getStatus()) || "WORK_RESUMED".equals(request.getStatus())) {
                request.setStatus("PENDING_MODIFICATION");
            } else {
                request.setStatus("PENDING");
            }
        }

        AnnualLeave saved = annualLeaveRepository.save(request);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> deleteLeaveRequest(@PathVariable Long id) {
        if (annualLeaveRepository.existsById(id)) {
            annualLeaveRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    private Optional<Personnel> getPersonnelFromPrincipal(UserDetails principal) {
        return personnelRepository.findAll().stream()
                .filter(p -> p.getUserAccount() != null && p.getUserAccount().getUsername().equals(principal.getUsername()))
                .findFirst();
    }

    private AnnualLeaveDto convertToDto(AnnualLeave al) {
        long duration = 0;
        if (al.getStartDate() != null && al.getEndDate() != null) {
            duration = java.time.temporal.ChronoUnit.DAYS.between(al.getStartDate(), al.getEndDate()) + 1;
        }
        java.time.LocalDate returnDate = al.getEndDate() != null ? al.getEndDate().plusDays(1) : null;

        return AnnualLeaveDto.builder()
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
                .startDate(al.getStartDate())
                .endDate(al.getEndDate())
                .status(al.getStatus())
                .justification(al.getJustification())
                .documentPath(al.getDocumentPath())
                .duration(duration)
                .returnDate(returnDate)
                .build();
    }

    private int calculateLeaveBalance(Long personnelId) {
        int limit = 45;
        int currentYear = java.time.LocalDate.now().getYear();

        List<AnnualLeave> requests = annualLeaveRepository.findByPersonnelId(personnelId);
        int daysUsed = 0;
        for (AnnualLeave r : requests) {
            if (("APPROVED".equals(r.getStatus()) || "LEAVE_STARTED".equals(r.getStatus()) || "WORK_RESUMED".equals(r.getStatus()) || "PENDING_MODIFICATION".equals(r.getStatus()) || "PENDING_DELETION".equals(r.getStatus()))
                && r.getStartDate() != null
                && r.getStartDate().getYear() == currentYear) {

                long diff = java.time.temporal.ChronoUnit.DAYS.between(r.getStartDate(), r.getEndDate()) + 1;
                daysUsed += (int) diff;
            }
        }
        return Math.max(0, limit - daysUsed);
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

    private String generateNextLeaveCode() {
        Optional<AnnualLeave> lastLeave = annualLeaveRepository.findTopByOrderByIdDesc();
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
}
