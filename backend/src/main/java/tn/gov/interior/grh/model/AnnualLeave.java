package tn.gov.interior.grh.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "annual_leaves")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnualLeave {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_code", unique = true)
    private String leaveCode; // 00001, 00002, ...

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "personnel_id", nullable = false)
    private Personnel personnel;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private String status; // PENDING, APPROVED_SERVICE, APPROVED_SD, APPROVED, REJECTED, LEAVE_STARTED, WORK_RESUMED, PENDING_MODIFICATION, PENDING_DELETION

    @Column(name = "document_path")
    private String documentPath;

    @Column(name = "justification")
    private String justification;
}
