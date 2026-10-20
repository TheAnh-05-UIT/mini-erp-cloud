package com.minierp.platform.security.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads user identities using the canonical username and active-state rules. / Đọc user identity theo username chuẩn hóa và quy tắc trạng thái hoạt động.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByUsername(String normalizedUsername);

    boolean existsByUsername(String normalizedUsername);

    @Query("""
            select distinct userAccount
            from UserAccount userAccount
            left join fetch userAccount.roleAssignments assignment
            left join fetch assignment.role role
            where userAccount.username = :normalizedUsername
              and userAccount.active = true
              and (role is null or role.active = true)
            """)
    Optional<UserAccount> findActiveWithActiveRolesByUsername(@Param("normalizedUsername") String normalizedUsername);
}
