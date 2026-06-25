package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.CareerProgression;
import java.util.List;

@Repository
public interface CareerProgressionRepository extends JpaRepository<CareerProgression, Long> {
    List<CareerProgression> findByPersonnelIdOrderByPromotionDateDesc(Long personnelId);
}
