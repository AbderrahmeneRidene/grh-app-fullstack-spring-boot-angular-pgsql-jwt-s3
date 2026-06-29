package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.AnnualLeave;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnnualLeaveRepository extends JpaRepository<AnnualLeave, Long> {
    List<AnnualLeave> findByPersonnelId(Long personnelId);
    List<AnnualLeave> findByStatus(String status);
    Optional<AnnualLeave> findTopByOrderByIdDesc();
    
    @Query("SELECT al FROM AnnualLeave al WHERE al.personnel.organizationalUnit.id = :unitId")
    List<AnnualLeave> findByPersonnelOrganizationalUnitId(@Param("unitId") Long unitId);

    @Query("SELECT al FROM AnnualLeave al WHERE al.personnel.organizationalUnit.id IN :unitIds")
    List<AnnualLeave> findByPersonnelOrganizationalUnitIdIn(@Param("unitIds") List<Long> unitIds);
}
