package tn.gov.interior.grh.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name; // e.g., ROLE_SUPER_ADMIN, ROLE_ADMIN_DIRECTION, ROLE_CHEF_SOUS_DIRECTION, ROLE_CHEF_SERVICE, ROLE_AGENT_RH, ROLE_USER
}
