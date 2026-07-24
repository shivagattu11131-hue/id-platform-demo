package com.raksul.idplatform.repository;

import com.raksul.idplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findBySource(User.UserSource source);

    @Query("SELECT u FROM User u WHERE u.legacyMainSiteId = :legacyId")
    Optional<User> findByLegacyMainSiteId(@Param("legacyId") String legacyId);

    @Query("SELECT u FROM User u WHERE u.legacyMaSiteId = :legacyId")
    Optional<User> findByLegacyMaSiteId(@Param("legacyId") String legacyId);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.source = :source")
    Optional<User> findByEmailAndSource(@Param("email") String email, @Param("source") User.UserSource source);

    @Query("SELECT COUNT(u) FROM User u WHERE u.active = true")
    long countActiveUsers();
}
