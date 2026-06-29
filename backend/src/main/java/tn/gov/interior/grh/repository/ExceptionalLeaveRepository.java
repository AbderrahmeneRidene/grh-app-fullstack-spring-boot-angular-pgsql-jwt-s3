package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.ExceptionalLeave;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExceptionalLeaveRepository extends JpaRepository<ExceptionalLeave, Long> {
    List<ExceptionalLeave> findByPersonnelId(Long personnelId);
    List<ExceptionalLeave> findByStatus(String status);
    Optional<ExceptionalLeave> findTopByOrderByIdDesc();
    
    @Query("SELECT el FROM ExceptionalLeave el WHERE el.personnel.organizationalUnit.id = :unitId")
    List<ExceptionalLeave> findByPersonnelOrganizationalUnitId(@Param("unitId") Long unitId);

    @Query("SELECT el FROM ExceptionalLeave el WHERE el.personnel.organizationalUnit.id IN :unitIds")
    List<ExceptionalLeave> findByPersonnelOrganizationalUnitIdIn(@Param("unitIds") List<Long> unitIds);
}
