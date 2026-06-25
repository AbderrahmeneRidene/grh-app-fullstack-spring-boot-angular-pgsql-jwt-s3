package tn.gov.interior.grh.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "leave_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "personnel_id", nullable = false)
    private Personnel personnel;

    @Column(name = "leave_type", nullable = false)
    private String leaveType; // MALADIE, ANNUEL, EXCEPTIONNEL

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private String status; // PENDING, APPROVED_SERVICE, APPROVED_SD, APPROVED, REJECTED

    @Column(name = "document_path")
    private String documentPath; // Path of uploaded file (like medical certificate)
    
    @Column(name = "justification")
    private String justification; // Notes / comments
}
