package tn.gov.interior.grh.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "exceptional_leaves")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExceptionalLeave {
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

    @Column(name = "exceptional_leave_type")
    private String exceptionalLeaveType; // TWO_HOURS, HALF_DAY, ONE_DAY, TWO_DAYS

    @Column(name = "start_time")
    private String startTime; // e.g. "10:00"

    @Column(name = "session")
    private String session; // MATINALE, APRES_MIDI

    @Column(nullable = false)
    private Double duration; // 0.0, 0.5, 1.0, 2.0
}
