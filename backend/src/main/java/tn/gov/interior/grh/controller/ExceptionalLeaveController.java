package tn.gov.interior.grh.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.gov.interior.grh.dto.ExceptionalLeaveDto;
import tn.gov.interior.grh.model.*;
import tn.gov.interior.grh.repository.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exceptional-leaves")
public class ExceptionalLeaveController {

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
    public ResponseEntity<List<ExceptionalLeaveDto>> getLeaves() {
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<Personnel> currentPersonnelOpt = getPersonnelFromPrincipal(principal);

        boolean isAllAccessRole = principal.getAuthorities().stream().anyMatch(a -> 
            a.getAuthority().equals("ROLE_SUPER_ADMIN") || 
            a.getAuthority().equals("ROLE_ADMIN_DIRECTION") || 
            a.getAuthority().equals("ROLE_AGENT_RH")
        );

        if (currentPersonnelOpt.isEmpty()) {
            if (isAllAccessRole) {
                return ResponseEntity.ok(exceptionalLeaveRepository.findAll().stream().map(this::convertToDto).collect(Collectors.toList()));
            }
            return ResponseEntity.badRequest().build();
        }

        Personnel current = currentPersonnelOpt.get();
        List<ExceptionalLeave> requests = new ArrayList<>();

        boolean isSd = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CHEF_SOUS_DIRECTION"));
        boolean isServ = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CHEF_SERVICE"));

        if (isAllAccessRole) {
            requests = exceptionalLeaveRepository.findAll();
        } else if (isSd) {
            List<Long> unitIds = new ArrayList<>();
            unitIds.add(current.getOrganizationalUnit().getId());
            List<OrganizationalUnit> children = organizationalUnitRepository.findByParentId(current.getOrganizationalUnit().getId());
            for (OrganizationalUnit child : children) {
                unitIds.add(child.getId());
            }
            requests = exceptionalLeaveRepository.findByPersonnelOrganizationalUnitIdIn(unitIds);
        } else if (isServ) {
            requests = exceptionalLeaveRepository.findByPersonnelOrganizationalUnitId(current.getOrganizationalUnit().getId());
        } else {
            requests = exceptionalLeaveRepository.findByPersonnelId(current.getId());
        }

        List<ExceptionalLeaveDto> dtos = requests.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createLeaveRequest(@RequestBody ExceptionalLeaveDto dto) {
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

        String type = dto.getExceptionalLeaveType();
        if (type == null || type.trim().isEmpty()) {
            type = "ONE_DAY"; // Fallback
        }

        java.time.LocalDate endDate = dto.getStartDate();
        double duration = 1.0;

        if ("TWO_HOURS".equals(type)) {
            if (dto.getStartTime() == null || dto.getStartTime().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("توقيت المغادرة إجباري للرخص من فئة ساعتين");
            }
            endDate = dto.getStartDate();
            duration = 0.0;
        } else if ("HALF_DAY".equals(type)) {
            if (dto.getSession() == null || dto.getSession().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("تحديد الحصة (صباحية/مسائية) إجباري للرخص من فئة نصف يوم");
            }
            endDate = dto.getStartDate();
            duration = 0.5;
        } else if ("ONE_DAY".equals(type)) {
            endDate = dto.getStartDate();
            duration = 1.0;
        } else if ("TWO_DAYS".equals(type)) {
            endDate = dto.getStartDate().plusDays(1);
            duration = 2.0;
        } else {
            return ResponseEntity.badRequest().body("نوع الرخصة الاستثنائية غير صالح");
        }

        double balance = calculateLeaveBalance(targetPersonnel.getId());
        if (duration > balance) {
            return ResponseEntity.badRequest().body("المدة المطلوبة (" + duration + " يوم) تتجاوز الرصيد المتبقي (" + balance + " يوم)");
        }

        // Check overlap (Annual, Exceptional & Sick)
        if (hasOverlap(targetPersonnel.getId(), dto.getStartDate(), endDate)) {
            return ResponseEntity.badRequest().body("الموظف لديه إجازة أخرى أو طلب إجازة قيد الدراسة في نفس الفترة.");
        }

        String leaveCode = generateNextLeaveCode();

        ExceptionalLeave request = ExceptionalLeave.builder()
                .leaveCode(leaveCode)
                .personnel(targetPersonnel)
                .startDate(dto.getStartDate())
                .endDate(endDate)
                .exceptionalLeaveType(type)
                .startTime(dto.getStartTime())
                .session(dto.getSession())
                .duration(duration)
                .status("PENDING")
                .justification(dto.getJustification())
                .documentPath(dto.getDocumentPath())
                .build();

        ExceptionalLeave saved = exceptionalLeaveRepository.save(request);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_CHEF_SERVICE', 'ROLE_CHEF_SOUS_DIRECTION', 'ROLE_ADMIN_DIRECTION', 'ROLE_AGENT_RH')")
    @Transactional
    public ResponseEntity<?> updateLeaveStatus(@PathVariable Long id, @RequestParam("status") String status, @RequestParam(value = "justification", required = false) String justification) {
        Optional<ExceptionalLeave> requestOpt = exceptionalLeaveRepository.findById(id);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ExceptionalLeave request = requestOpt.get();
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

        ExceptionalLeave updated = exceptionalLeaveRepository.save(request);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> modifyLeaveRequest(@PathVariable Long id, @RequestBody ExceptionalLeaveDto dto) {
        Optional<ExceptionalLeave> requestOpt = exceptionalLeaveRepository.findById(id);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ExceptionalLeave request = requestOpt.get();
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
            if (dto.getStartDate() != null) {
                request.setStartDate(dto.getStartDate());
                // Recalculate end date based on type
                String t = dto.getExceptionalLeaveType() != null ? dto.getExceptionalLeaveType() : request.getExceptionalLeaveType();
                if ("TWO_DAYS".equals(t)) {
                    request.setEndDate(dto.getStartDate().plusDays(1));
                } else {
                    request.setEndDate(dto.getStartDate());
                }
            }
            if (dto.getExceptionalLeaveType() != null) {
                request.setExceptionalLeaveType(dto.getExceptionalLeaveType());
                if ("TWO_HOURS".equals(dto.getExceptionalLeaveType())) {
                    request.setDuration(0.0);
                    request.setEndDate(request.getStartDate());
                } else if ("HALF_DAY".equals(dto.getExceptionalLeaveType())) {
                    request.setDuration(0.5);
                    request.setEndDate(request.getStartDate());
                } else if ("ONE_DAY".equals(dto.getExceptionalLeaveType())) {
                    request.setDuration(1.0);
                    request.setEndDate(request.getStartDate());
                } else if ("TWO_DAYS".equals(dto.getExceptionalLeaveType())) {
                    request.setDuration(2.0);
                    request.setEndDate(request.getStartDate().plusDays(1));
                }
            }
            if (dto.getStartTime() != null) request.setStartTime(dto.getStartTime());
            if (dto.getSession() != null) request.setSession(dto.getSession());
            if (dto.getJustification() != null) request.setJustification(dto.getJustification());
            if (dto.getDocumentPath() != null) request.setDocumentPath(dto.getDocumentPath());
            
            ExceptionalLeave saved = exceptionalLeaveRepository.save(request);
            return ResponseEntity.ok(convertToDto(saved));
        }

        if (!isDirector && !isAgentRh) {
            return ResponseEntity.status(403).body("غير مسموح بتعديل هذا الطلب");
        }

        if (dto.getStartDate() != null) {
            request.setStartDate(dto.getStartDate());
            String t = dto.getExceptionalLeaveType() != null ? dto.getExceptionalLeaveType() : request.getExceptionalLeaveType();
            if ("TWO_DAYS".equals(t)) {
                request.setEndDate(dto.getStartDate().plusDays(1));
            } else {
                request.setEndDate(dto.getStartDate());
            }
        }
        if (dto.getExceptionalLeaveType() != null) {
            request.setExceptionalLeaveType(dto.getExceptionalLeaveType());
            if ("TWO_HOURS".equals(dto.getExceptionalLeaveType())) {
                request.setDuration(0.0);
                request.setEndDate(request.getStartDate());
            } else if ("HALF_DAY".equals(dto.getExceptionalLeaveType())) {
                request.setDuration(0.5);
                request.setEndDate(request.getStartDate());
            } else if ("ONE_DAY".equals(dto.getExceptionalLeaveType())) {
                request.setDuration(1.0);
                request.setEndDate(request.getStartDate());
            } else if ("TWO_DAYS".equals(dto.getExceptionalLeaveType())) {
                request.setDuration(2.0);
                request.setEndDate(request.getStartDate().plusDays(1));
            }
        }
        if (dto.getStartTime() != null) request.setStartTime(dto.getStartTime());
        if (dto.getSession() != null) request.setSession(dto.getSession());
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

        ExceptionalLeave saved = exceptionalLeaveRepository.save(request);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<?> deleteLeaveRequest(@PathVariable Long id) {
        if (exceptionalLeaveRepository.existsById(id)) {
            exceptionalLeaveRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    private Optional<Personnel> getPersonnelFromPrincipal(UserDetails principal) {
        return personnelRepository.findAll().stream()
                .filter(p -> p.getUserAccount() != null && p.getUserAccount().getUsername().equals(principal.getUsername()))
                .findFirst();
    }

    private ExceptionalLeaveDto convertToDto(ExceptionalLeave el) {
        java.time.LocalDate returnDate = el.getEndDate() != null ? el.getEndDate().plusDays(1) : null;

        return ExceptionalLeaveDto.builder()
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
                .startDate(el.getStartDate())
                .endDate(el.getEndDate())
                .status(el.getStatus())
                .justification(el.getJustification())
                .documentPath(el.getDocumentPath())
                .duration(el.getDuration() != null ? el.getDuration() : 1.0)
                .returnDate(returnDate)
                .exceptionalLeaveType(el.getExceptionalLeaveType())
                .startTime(el.getStartTime())
                .session(el.getSession())
                .build();
    }

    private double calculateLeaveBalance(Long personnelId) {
        int limit = 6;
        int currentYear = java.time.LocalDate.now().getYear();

        List<ExceptionalLeave> requests = exceptionalLeaveRepository.findByPersonnelId(personnelId);
        double daysUsed = 0;
        for (ExceptionalLeave r : requests) {
            if (("APPROVED".equals(r.getStatus()) || "LEAVE_STARTED".equals(r.getStatus()) || "WORK_RESUMED".equals(r.getStatus()) || "PENDING_MODIFICATION".equals(r.getStatus()) || "PENDING_DELETION".equals(r.getStatus()))
                && r.getStartDate() != null
                && r.getStartDate().getYear() == currentYear) {

                daysUsed += r.getDuration() != null ? r.getDuration() : 1.0;
            }
        }
        return Math.max(0.0, limit - daysUsed);
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
        Optional<ExceptionalLeave> lastLeave = exceptionalLeaveRepository.findTopByOrderByIdDesc();
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
