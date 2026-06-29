package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.SickLeave;
import java.util.List;
import java.util.Optional;

@Repository
public interface SickLeaveRepository extends JpaRepository<SickLeave, Long> {
    List<SickLeave> findByPersonnelId(Long personnelId);

    @Query("SELECT sl FROM SickLeave sl WHERE sl.personnel.organizationalUnit.id = :unitId")
    List<SickLeave> findByPersonnelOrganizationalUnitId(@Param("unitId") Long unitId);

    @Query("SELECT sl FROM SickLeave sl WHERE sl.personnel.organizationalUnit.id IN :unitIds")
    List<SickLeave> findByPersonnelOrganizationalUnitIdIn(@Param("unitIds") List<Long> unitIds);

    Optional<SickLeave> findTopByOrderByIdDesc();
}
