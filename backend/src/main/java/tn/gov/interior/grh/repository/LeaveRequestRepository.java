package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.LeaveRequest;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByPersonnelId(Long personnelId);
    List<LeaveRequest> findByStatus(String status);
    
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.personnel.organizationalUnit.id = :unitId")
    List<LeaveRequest> findByPersonnelOrganizationalUnitId(@Param("unitId") Long unitId);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.personnel.organizationalUnit.id IN :unitIds")
    List<LeaveRequest> findByPersonnelOrganizationalUnitIdIn(@Param("unitIds") List<Long> unitIds);
}
