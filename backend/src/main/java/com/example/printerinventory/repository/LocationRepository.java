package com.example.printerinventory.repository;

import com.example.printerinventory.entity.Location;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface LocationRepository extends JpaRepository<Location, Long> {
    // Serialize assignment and location edits/deletion so a used location cannot change mid-request.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Location l where l.id = :id")
    Optional<Location> findForUpdate(long id);
}
