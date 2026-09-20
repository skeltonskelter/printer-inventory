package com.example.printerinventory.repository;

import com.example.printerinventory.entity.RelocationHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface RelocationHistoryRepository extends JpaRepository<RelocationHistory, Long> {
    @Query("select count(distinct r.printer.id) from RelocationHistory r where r.printer.deletedAt is null")
    long countRelocatedLivePrinters();

    @EntityGraph(attributePaths = {"printer", "printer.location", "previousLocation", "newLocation"})
    List<RelocationHistory> findTop5ByPrinterDeletedAtIsNullOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = {"previousLocation", "newLocation"})
    List<RelocationHistory> findByPrinterIdOrderByRelocationDateDescIdDesc(long printerId);

    Optional<RelocationHistory> findFirstByPrinterIdOrderByRelocationDateDescIdDesc(long printerId);
    boolean existsByPreviousLocationIdOrNewLocationId(long previousLocationId, long newLocationId);
}
