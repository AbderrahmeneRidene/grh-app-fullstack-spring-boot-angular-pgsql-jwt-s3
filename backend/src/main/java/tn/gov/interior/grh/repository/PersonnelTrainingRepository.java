package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.PersonnelTraining;
import java.util.List;

@Repository
public interface PersonnelTrainingRepository extends JpaRepository<PersonnelTraining, Long> {
    List<PersonnelTraining> findByPersonnelId(Long personnelId);
    List<PersonnelTraining> findByTrainingId(Long trainingId);
}
