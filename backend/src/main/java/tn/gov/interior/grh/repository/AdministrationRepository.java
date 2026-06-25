package tn.gov.interior.grh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.gov.interior.grh.model.Administration;
import java.util.Optional;

@Repository
public interface AdministrationRepository extends JpaRepository<Administration, Long> {
    Optional<Administration> findByCode(String code);
}
