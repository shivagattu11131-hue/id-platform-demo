package com.raksul.idplatform.repository;

import com.raksul.idplatform.model.MigrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MigrationStatusRepository extends JpaRepository<MigrationStatus, Long> {

    Optional<MigrationStatus> findBySiteName(String siteName);
}
