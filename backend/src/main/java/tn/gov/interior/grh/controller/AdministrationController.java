package tn.gov.interior.grh.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.gov.interior.grh.dto.OrgUnitDto;
import tn.gov.interior.grh.model.Administration;
import tn.gov.interior.grh.model.OrganizationalUnit;
import tn.gov.interior.grh.repository.AdministrationRepository;
import tn.gov.interior.grh.repository.OrganizationalUnitRepository;
import tn.gov.interior.grh.repository.PersonnelRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdministrationController {

    @Autowired
    private AdministrationRepository administrationRepository;

    @Autowired
    private OrganizationalUnitRepository organizationalUnitRepository;

    @Autowired
    private PersonnelRepository personnelRepository;

    // --- Gestion des Administrations ---

    @GetMapping("/administrations")
    public List<Administration> getAllAdministrations() {
        return administrationRepository.findAll();
    }

    @PostMapping("/administrations")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public Administration createAdministration(@RequestBody Administration administration) {
        return administrationRepository.save(administration);
    }

    // --- Gestion des Unités Organisationnelles ---

    @GetMapping("/org-units")
    public List<OrgUnitDto> getAllOrgUnits() {
        return organizationalUnitRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/org-units/tree")
    public ResponseEntity<List<OrganizationalUnit>> getOrgUnitTree() {
        List<OrganizationalUnit> roots = organizationalUnitRepository.findByParentIsNullAndAdministrationId(1L);
        return ResponseEntity.ok(roots);
    }

    @PostMapping("/org-units")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN_DIRECTION', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> createOrgUnit(@RequestBody OrgUnitDto dto) {
        String validationError = validateOrgUnit(dto);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }
        Optional<Administration> adminOpt = administrationRepository.findById(1L);
        if (adminOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Default administration not found");
        }

        OrganizationalUnit unit = OrganizationalUnit.builder()
                .nameAr(dto.getNameAr())
                .nameFr(dto.getNameFr())
                .type(dto.getType())
                .administration(adminOpt.get())
                .build();

        if (dto.getParentId() != null) {
            Optional<OrganizationalUnit> parentOpt = organizationalUnitRepository.findById(dto.getParentId());
            if (parentOpt.isPresent()) {
                unit.setParent(parentOpt.get());
            } else {
                return ResponseEntity.badRequest().body("Parent unit not found");
            }
        }

        OrganizationalUnit saved = organizationalUnitRepository.save(unit);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @PutMapping("/org-units/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN_DIRECTION', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> updateOrgUnit(@PathVariable Long id, @RequestBody OrgUnitDto dto) {
        String validationError = validateOrgUnit(dto);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }
        Optional<OrganizationalUnit> unitOpt = organizationalUnitRepository.findById(id);
        if (unitOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        OrganizationalUnit unit = unitOpt.get();
        unit.setNameAr(dto.getNameAr());
        unit.setNameFr(dto.getNameFr());
        unit.setType(dto.getType());

        if (dto.getParentId() != null) {
            Optional<OrganizationalUnit> parentOpt = organizationalUnitRepository.findById(dto.getParentId());
            if (parentOpt.isPresent()) {
                unit.setParent(parentOpt.get());
            } else {
                return ResponseEntity.badRequest().body("Parent unit not found");
            }
        } else {
            unit.setParent(null);
        }

        OrganizationalUnit saved = organizationalUnitRepository.save(unit);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @DeleteMapping("/org-units/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN_DIRECTION', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> deleteOrgUnit(@PathVariable Long id) {
        Optional<OrganizationalUnit> unitOpt = organizationalUnitRepository.findById(id);
        if (unitOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        OrganizationalUnit unit = unitOpt.get();
        List<Long> unitIds = new ArrayList<>();
        collectIds(unit, unitIds);

        if (personnelRepository.existsByOrganizationalUnitIdIn(unitIds)) {
            return ResponseEntity.badRequest().body("personnel_assigned");
        }

        organizationalUnitRepository.delete(unit);
        return ResponseEntity.ok().build();
    }

    private void collectIds(OrganizationalUnit unit, List<Long> ids) {
        ids.add(unit.getId());
        for (OrganizationalUnit child : unit.getChildren()) {
            collectIds(child, ids);
        }
    }

    private OrgUnitDto convertToDto(OrganizationalUnit unit) {
        return OrgUnitDto.builder()
                .id(unit.getId())
                .nameAr(unit.getNameAr())
                .nameFr(unit.getNameFr())
                .type(unit.getType())
                .parentId(unit.getParent() != null ? unit.getParent().getId() : null)
                .build();
    }

    private String validateOrgUnit(OrgUnitDto dto) {
        if (dto == null) return "البيانات فارغة";

        if (dto.getNameAr() == null || dto.getNameAr().trim().isEmpty()) {
            return "الاسم باللغة العربية إجباري";
        }
        if (!dto.getNameAr().trim().matches("^[\\u0600-\\u06FF\\s0-9\\-\\(\\)\\[\\]\\.\\,]{1,100}$")) {
            return "الاسم باللغة العربية يجب أن يحتوي على حروف عربية فقط ولا يتجاوز 100 حرفاً";
        }

        if (dto.getNameFr() == null || dto.getNameFr().trim().isEmpty()) {
            return "الاسم باللغة الفرنسية إجباري";
        }
        if (!dto.getNameFr().trim().matches("^[a-zA-Z\\s0-9\\-\\(\\)\\[\\]\\.\\,\\'\\u00C0-\\u00FF]{1,100}$")) {
            return "الاسم باللغة الفرنسية يجب أن يحتوي على حروف لاتينية فقط ولا يتجاوز 100 حرفاً";
        }

        if (dto.getType() == null || dto.getType().trim().isEmpty()) {
            return "نوع التقسيم الإداري إجباري";
        }

        String type = dto.getType();
        if (!"DIRECTION".equals(type) && dto.getParentId() == null) {
            return "يجب اختيار الهيكل الأعلى المسؤول";
        }
        if ("DIRECTION".equals(type) && dto.getParentId() != null) {
            return "الهيكل ذو صنف إدارة لا يمكن أن يملك هيكل أعلى مسؤول";
        }

        return null;
    }
}
