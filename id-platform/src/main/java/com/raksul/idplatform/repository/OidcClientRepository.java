package com.raksul.idplatform.repository;

import com.raksul.idplatform.model.OidcClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OidcClientRepository extends JpaRepository<OidcClient, Long> {

    Optional<OidcClient> findByClientId(String clientId);

    boolean existsByClientId(String clientId);

    List<OidcClient> findAllByActive(boolean active);
}
