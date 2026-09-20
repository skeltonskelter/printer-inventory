package com.example.printerinventory.repository;

import com.example.printerinventory.entity.Printer;
import com.example.printerinventory.entity.PrinterStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class PrinterSpecifications {
    private PrinterSpecifications() {}

    public static Specification<Printer> matching(String search, String brand, Long locationId, PrinterStatus status) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (search != null && !search.isBlank()) {
                // Treat SQL wildcard characters as literal user input.
                String escaped = search.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("stickerNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.get("serialNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.get("brand")), pattern, '\\'),
                        cb.like(cb.lower(root.get("model")), pattern, '\\')));
            }
            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.trim().toLowerCase(Locale.ROOT)));
            }
            if (locationId != null) predicates.add(cb.equal(root.get("location").get("id"), locationId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
