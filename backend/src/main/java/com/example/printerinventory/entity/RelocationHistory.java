package com.example.printerinventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "relocation_history")
public class RelocationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "printer_id", nullable = false, updatable = false)
    private Printer printer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "previous_location_id", nullable = false, updatable = false)
    private Location previousLocation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_location_id", nullable = false, updatable = false)
    private Location newLocation;
    @Column(nullable = false, updatable = false)
    private LocalDate relocationDate;
    @Column(length = 2000, updatable = false)
    private String remarks;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected RelocationHistory() {}

    public RelocationHistory(Printer printer, Location previousLocation, Location newLocation,
                             LocalDate relocationDate, String remarks, Instant createdAt) {
        this.printer = printer;
        this.previousLocation = previousLocation;
        this.newLocation = newLocation;
        this.relocationDate = relocationDate;
        this.remarks = remarks;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Printer getPrinter() { return printer; }
    public Location getPreviousLocation() { return previousLocation; }
    public Location getNewLocation() { return newLocation; }
    public LocalDate getRelocationDate() { return relocationDate; }
    public String getRemarks() { return remarks; }
    public Instant getCreatedAt() { return createdAt; }
}
