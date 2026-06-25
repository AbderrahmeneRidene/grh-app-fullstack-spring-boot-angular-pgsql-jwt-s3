package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.Personnel;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonnelRepository extends JpaRepository<Personnel, Long> {
    Optional<Personnel> findByRegistrationNumber(String registrationNumber);
    Optional<Personnel> findByUserAccountId(Long userAccountId);
    List<Personnel> findByOrganizationalUnitId(Long organizationalUnitId);
    boolean existsByOrganizationalUnitIdIn(List<Long> organizationalUnitIds);
    
    @Query("SELECT p FROM Personnel p WHERE " +
           "LOWER(p.registrationNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.firstNameAr) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.lastNameAr) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.firstNameFr) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.lastNameFr) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Personnel> searchPersonnel(@Param("query") String query);
}
