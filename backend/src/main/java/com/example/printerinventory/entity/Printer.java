package com.example.printerinventory.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "printers")
public class Printer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String brand;
    @Column(nullable = false, length = 120)
    private String model;
    @Column(length = 120)
    private String serialNumber;
    @Column(nullable = false, length = 80)
    private String stickerNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrinterStatus status;
    @Column(length = 2000)
    private String remarks;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant deletedAt;
    @Version
    private long version;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void update(String brand, String model, String serialNumber, String stickerNumber,
                       PrinterStatus status, String remarks) {
        this.brand = brand;
        this.model = model;
        this.serialNumber = serialNumber;
        this.stickerNumber = stickerNumber;
        this.status = status;
        this.remarks = remarks;
    }

    public void assignInitialLocation(Location location) {
        if (this.location != null) throw new IllegalStateException("Initial location already assigned");
        this.location = location;
    }

    public void relocateTo(Location location) { this.location = location; }

    public void markDeleted() { deletedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public String getSerialNumber() { return serialNumber; }
    public String getStickerNumber() { return stickerNumber; }
    public Location getLocation() { return location; }
    public PrinterStatus getStatus() { return status; }
    public String getRemarks() { return remarks; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
