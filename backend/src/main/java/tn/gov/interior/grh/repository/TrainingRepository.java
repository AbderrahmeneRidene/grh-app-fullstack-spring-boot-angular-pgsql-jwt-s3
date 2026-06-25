package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.Training;

@Repository
public interface TrainingRepository extends JpaRepository<Training, Long> {
}
