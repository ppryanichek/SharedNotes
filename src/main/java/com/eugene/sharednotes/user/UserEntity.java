package com.eugene.sharednotes.user;

import com.eugene.sharednotes.common.Role;
import jakarta.persistence.*;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Setter
    @Column(nullable = false)
    private String password;

    @Setter
    @Column(unique = true, nullable = false)
    private String email;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role = Role.USER;
}
