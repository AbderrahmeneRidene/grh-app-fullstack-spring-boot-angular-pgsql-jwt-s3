package tn.gov.interior.grh.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "sick_leaves")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SickLeave {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_code", nullable = false, unique = true)
    private String leaveCode; // 00001, 00002, ...

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "personnel_id", nullable = false)
    private Personnel personnel;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "justification")
    private String justification;

    @Column(name = "document_path")
    private String documentPath;

    @Column(name = "created_by")
    private String createdBy; // Username of the RH agent who created this leave

    @Column(name = "extension_notes")
    private String extensionNotes; // Notes added when extending the leave
}
