package com.example.printerinventory.repository;

import com.example.printerinventory.entity.Printer;
import java.util.Optional;
import java.util.List;
import com.example.printerinventory.entity.PrinterStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;

public interface PrinterRepository extends JpaRepository<Printer, Long>, JpaSpecificationExecutor<Printer> {
    interface StatusCount {
        PrinterStatus getStatus();
        long getTotal();
    }

    @Query("select p.status as status, count(p) as total from Printer p where p.deletedAt is null group by p.status")
    List<StatusCount> countLiveByStatus();

    @EntityGraph(attributePaths = "location")
    List<Printer> findTop5ByDeletedAtIsNullOrderByCreatedAtDescIdDesc();

    @Override
    @EntityGraph(attributePaths = "location")
    Page<Printer> findAll(Specification<Printer> specification, Pageable pageable);

    @EntityGraph(attributePaths = "location")
    List<Printer> findAll(Specification<Printer> specification, org.springframework.data.domain.Sort sort);

    Optional<Printer> findByIdAndDeletedAtIsNull(long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Printer p where p.id = :id and p.deletedAt is null")
    Optional<Printer> findForUpdate(long id);
    boolean existsByStickerNumberIgnoreCaseAndIdNot(String stickerNumber, long id);
    boolean existsBySerialNumberIgnoreCaseAndIdNot(String serialNumber, long id);
    boolean existsByLocationId(long locationId);
}
