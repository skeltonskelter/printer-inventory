package com.example.printerinventory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "locations")
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String department;
    @Column(length = 120)
    private String building;
    @Column(length = 50)
    private String floor;
    @Column(length = 80)
    private String room;
    @Column(length = 1000)
    private String description;
    @Version
    private long version;

    public Long getId() { return id; }
    public String getDepartment() { return department; }
    public String getBuilding() { return building; }
    public String getFloor() { return floor; }
    public String getRoom() { return room; }
    public String getDescription() { return description; }
    public long getVersion() { return version; }

    public void update(String department, String building, String floor, String room, String description) {
        this.department = department;
        this.building = building;
        this.floor = floor;
        this.room = room;
        this.description = description;
    }
}
