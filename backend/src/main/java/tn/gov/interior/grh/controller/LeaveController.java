package tn.gov.interior.grh.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.gov.interior.grh.dto.LeaveRequestDto;
import tn.gov.interior.grh.model.*;
import tn.gov.interior.grh.repository.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private PersonnelRepository personnelRepository;

    @Autowired
    private OrganizationalUnitRepository organizationalUnitRepository;

    @GetMapping
    public ResponseEntity<List<LeaveRequestDto>> getLeaves() {
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<Personnel> currentPersonnelOpt = getPersonnelFromPrincipal(principal);

        boolean isAllAccessRole = principal.getAuthorities().stream().anyMatch(a -> 
            a.getAuthority().equals("ROLE_SUPER_ADMIN") || 
            a.getAuthority().equals("ROLE_ADMIN_DIRECTION") || 
            a.getAuthority().equals("ROLE_AGENT_RH")
        );

        if (currentPersonnelOpt.isEmpty()) {
            if (isAllAccessRole) {
                return ResponseEntity.ok(leaveRequestRepository.findAll().stream().map(this::convertToDto).collect(Collectors.toList()));
            }
            return ResponseEntity.badRequest().build();
        }

        Personnel current = currentPersonnelOpt.get();
        List<LeaveRequest> requests = new ArrayList<>();

        boolean isSd = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CHEF_SOUS_DIRECTION"));
        boolean isServ = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CHEF_SERVICE"));

        if (isAllAccessRole) {
            requests = leaveRequestRepository.findAll();
        } else if (isSd) {
            // Voir les demandes de sa sous-direction et de tous les services rattachés
            List<Long> unitIds = new ArrayList<>();
            unitIds.add(current.getOrganizationalUnit().getId());
            // Récupérer les enfants (services)
            List<OrganizationalUnit> children = organizationalUnitRepository.findByParentId(current.getOrganizationalUnit().getId());
            for (OrganizationalUnit child : children) {
                unitIds.add(child.getId());
            }
            requests = leaveRequestRepository.findByPersonnelOrganizationalUnitIdIn(unitIds);
        } else if (isServ) {
            // Voir uniquement les demandes de son service
            requests = leaveRequestRepository.findByPersonnelOrganizationalUnitId(current.getOrganizationalUnit().getId());
        } else {
            // Rôle simple utilisateur : voir uniquement ses propres demandes
            requests = leaveRequestRepository.findByPersonnelId(current.getId());
        }

        List<LeaveRequestDto> dtos = requests.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createLeaveRequest(@RequestBody LeaveRequestDto dto) {
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

        if (dto.getLeaveType() == null || dto.getLeaveType().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("نوع الإجازة إجباري");
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

        if ("ANNUEL".equals(dto.getLeaveType()) || "EXCEPTIONNEL".equals(dto.getLeaveType())) {
            int balance = calculateLeaveBalance(targetPersonnel.getId(), dto.getLeaveType());
            if (duration > balance) {
                return ResponseEntity.badRequest().body("المدة المطلوبة (" + duration + " يوم) تتجاوز الرصيد المتبقي (" + balance + " يوم)");
            }
        }

        // Vérification du chevauchement de congés
        List<LeaveRequest> existingRequests = leaveRequestRepository.findByPersonnelId(targetPersonnel.getId());
        for (LeaveRequest existing : existingRequests) {
            if (!"REJECTED".equals(existing.getStatus())) {
                if (existing.getStartDate() != null && existing.getEndDate() != null) {
                    boolean overlap = !existing.getStartDate().isAfter(dto.getEndDate()) && !existing.getEndDate().isBefore(dto.getStartDate());
                    if (overlap) {
                        return ResponseEntity.badRequest().body("الموظف لديه إجازة أخرى أو طلب إجازة قيد الدراسة في نفس الفترة.");
                    }
                }
            }
        }

        LeaveRequest request = LeaveRequest.builder()
                .personnel(targetPersonnel)
                .leaveType(dto.getLeaveType())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status("PENDING")
                .justification(dto.getJustification())
                .documentPath(dto.getDocumentPath())
                .build();

        LeaveRequest saved = leaveRequestRepository.save(request);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_CHEF_SERVICE', 'ROLE_CHEF_SOUS_DIRECTION', 'ROLE_ADMIN_DIRECTION')")
    @Transactional
    public ResponseEntity<?> updateLeaveStatus(@PathVariable Long id, @RequestParam("status") String status, @RequestParam(value = "justification", required = false) String justification) {
        Optional<LeaveRequest> requestOpt = leaveRequestRepository.findById(id);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        LeaveRequest request = requestOpt.get();
        
        // Workflow de validation multiniveau
        // Service -> Sous-Direction -> Direction
        request.setStatus(status);
        if (justification != null) {
            request.setJustification(justification);
        }

        LeaveRequest updated = leaveRequestRepository.save(request);
        return ResponseEntity.ok(convertToDto(updated));
    }

    private Optional<Personnel> getPersonnelFromPrincipal(UserDetails principal) {
        // Chargement du compte utilisateur
        return personnelRepository.findAll().stream()
                .filter(p -> p.getUserAccount() != null && p.getUserAccount().getUsername().equals(principal.getUsername()))
                .findFirst();
    }

    private LeaveRequestDto convertToDto(LeaveRequest lr) {
        long duration = 0;
        if (lr.getStartDate() != null && lr.getEndDate() != null) {
            duration = java.time.temporal.ChronoUnit.DAYS.between(lr.getStartDate(), lr.getEndDate()) + 1;
        }
        java.time.LocalDate returnDate = lr.getEndDate() != null ? lr.getEndDate().plusDays(1) : null;

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
    private int calculateLeaveBalance(Long personnelId, String leaveType) {
        int limit = "ANNUEL".equals(leaveType) ? 45 : 6;
        int currentYear = java.time.LocalDate.now().getYear();

        List<LeaveRequest> requests = leaveRequestRepository.findByPersonnelId(personnelId);
        int daysUsed = 0;
        for (LeaveRequest r : requests) {
            if (leaveType.equals(r.getLeaveType())
                && "APPROVED".equals(r.getStatus())
                && r.getStartDate() != null
                && r.getStartDate().getYear() == currentYear) {

                long diff = java.time.temporal.ChronoUnit.DAYS.between(r.getStartDate(), r.getEndDate()) + 1;
                daysUsed += (int) diff;
            }
        }
        return Math.max(0, limit - daysUsed);
    }
}
