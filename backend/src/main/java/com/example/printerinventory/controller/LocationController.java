package com.example.printerinventory.controller;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.service.LocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
    private final LocationService locations;

    public LocationController(LocationService locations) { this.locations = locations; }

    @GetMapping
    public List<LocationResponse> list() { return locations.list(); }

    @GetMapping("/{id}")
    public LocationResponse get(@PathVariable @Positive long id) { return locations.get(id); }

    @PostMapping
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request) {
        var location = locations.create(request);
        return ResponseEntity.created(URI.create("/api/locations/" + location.id())).body(location);
    }

    @PutMapping("/{id}")
    public LocationResponse update(@PathVariable @Positive long id, @Valid @RequestBody LocationRequest request) {
        return locations.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive long id) {
        locations.delete(id);
        return ResponseEntity.noContent().build();
    }
}
