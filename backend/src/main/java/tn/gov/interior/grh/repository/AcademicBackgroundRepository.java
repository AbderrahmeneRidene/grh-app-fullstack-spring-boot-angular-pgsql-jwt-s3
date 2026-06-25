package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.AcademicBackground;
import java.util.List;

@Repository
public interface AcademicBackgroundRepository extends JpaRepository<AcademicBackground, Long> {
    List<AcademicBackground> findByPersonnelId(Long personnelId);
}
