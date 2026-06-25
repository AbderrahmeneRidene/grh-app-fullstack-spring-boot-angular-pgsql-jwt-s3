package tn.gov.interior.grh.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tn.gov.interior.grh.config.JwtTokenProvider;
import tn.gov.interior.grh.dto.LoginRequest;
import tn.gov.interior.grh.dto.JwtResponse;
import tn.gov.interior.grh.dto.UpdateProfileRequest;
import tn.gov.interior.grh.model.Personnel;
import tn.gov.interior.grh.model.UserAccount;
import tn.gov.interior.grh.repository.PersonnelRepository;
import tn.gov.interior.grh.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PersonnelRepository personnelRepository;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Optional<UserAccount> userAccountOpt = userAccountRepository.findByUsername(userDetails.getUsername());

        if (userAccountOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User account not found");
        }

        UserAccount userAccount = userAccountOpt.get();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Recherche des informations de personnel associées
        Optional<Personnel> personnelOpt = personnelRepository.findByUserAccountId(userAccount.getId());
        
        JwtResponse.JwtResponseBuilder builder = JwtResponse.builder()
                .token(jwt)
                .id(userAccount.getId())
                .username(userAccount.getUsername())
                .email(userAccount.getEmail())
                .roles(roles);

        if (personnelOpt.isPresent()) {
            Personnel p = personnelOpt.get();
            builder.personnelId(p.getId())
                   .fullNameAr(p.getFirstNameAr() + " " + p.getLastNameAr())
                   .fullNameFr(p.getFirstNameFr() + " " + p.getLastNameFr())
                   .grade(p.getGrade())
                   .gender(p.getGender())
                   .profilePicture(p.getProfilePicture());
            
            if (p.getOrganizationalUnit() != null) {
                builder.orgUnitNameAr(p.getOrganizationalUnit().getNameAr())
                       .orgUnitNameFr(p.getOrganizationalUnit().getNameFr());
            }
        } else if (roles.contains("ROLE_SUPER_ADMIN")) {
            builder.fullNameAr("مدير النظام")
                   .fullNameFr("System Administrator");
        }

        return ResponseEntity.ok(builder.build());
    }

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/verify-password")
    public ResponseEntity<?> verifyPassword(Authentication authentication, @RequestBody java.util.Map<String, String> body) {
        if (authentication == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        String currentPassword = body.get("currentPassword");
        if (currentPassword == null || currentPassword.isEmpty()) {
            return ResponseEntity.badRequest().body("كلمة المرور الحالية مطلوبة");
        }
        String currentUsername = authentication.getName();
        Optional<UserAccount> userOpt = userAccountRepository.findByUsername(currentUsername);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        UserAccount user = userOpt.get();
        if (passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.ok().body(java.util.Map.of("valid", true));
        } else {
            return ResponseEntity.badRequest().body("كلمة المرور الحالية غير صحيحة");
        }
    }

    @PutMapping("/update-profile")
    public ResponseEntity<?> updateProfile(Authentication authentication, @RequestBody UpdateProfileRequest request) {
        if (authentication == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        String currentUsername = authentication.getName();
        Optional<UserAccount> userOpt = userAccountRepository.findByUsername(currentUsername);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        UserAccount user = userOpt.get();

        // Check username uniqueness if changed
        if (request.getUsername() != null && !request.getUsername().trim().isEmpty() && !request.getUsername().equals(user.getUsername())) {
            if (userAccountRepository.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest().body("Username already exists");
            }
            user.setUsername(request.getUsername().trim());
        }

        // Check email uniqueness if changed
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty() && !request.getEmail().equals(user.getEmail())) {
            if (userAccountRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest().body("Email already exists");
            }
            user.setEmail(request.getEmail().trim());
        }

        // Update password if provided
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        UserAccount updated = userAccountRepository.save(user);

        String newToken = tokenProvider.generateTokenFromUsername(updated.getUsername());
        return ResponseEntity.ok(JwtResponse.builder()
                .id(updated.getId())
                .username(updated.getUsername())
                .email(updated.getEmail())
                .token(newToken)
                .build());
    }
}
