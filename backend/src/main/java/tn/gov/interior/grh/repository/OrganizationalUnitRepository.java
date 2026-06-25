package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.OrganizationalUnit;
import java.util.List;

@Repository
public interface OrganizationalUnitRepository extends JpaRepository<OrganizationalUnit, Long> {
    List<OrganizationalUnit> findByAdministrationId(Long administrationId);
    List<OrganizationalUnit> findByParentId(Long parentId);
    List<OrganizationalUnit> findByParentIsNullAndAdministrationId(Long administrationId);
}
