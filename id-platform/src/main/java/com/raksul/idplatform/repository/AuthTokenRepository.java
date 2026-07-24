package com.raksul.idplatform.repository;

import com.raksul.idplatform.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByToken(String token);

    Optional<AuthToken> findByTokenAndType(String token, AuthToken.TokenType type);

    @Modifying
    @Query("UPDATE AuthToken t SET t.revoked = true WHERE t.user.id = :userId")
    void revokeAllUserTokens(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE AuthToken t SET t.revoked = true WHERE t.token = :token")
    void revokeToken(@Param("token") String token);

    boolean existsByTokenAndRevokedFalse(String token);
}
