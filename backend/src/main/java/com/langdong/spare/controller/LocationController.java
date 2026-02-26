package com.langdong.spare.controller;

import com.langdong.spare.entity.Location;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.mapper.LocationMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    @Autowired
    private LocationMapper locationMapper;

    @Autowired
    private SparePartMapper sparePartMapper;

    @GetMapping
    public List<Location> getAllLocations() {
        return locationMapper.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Location> getLocationById(@PathVariable Long id) {
        Location location = locationMapper.findById(id);
        if (location == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(location);
    }

    @PostMapping
    public Location createLocation(@RequestBody Location location) {
        locationMapper.insert(location);
        return location;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Location> updateLocation(@PathVariable Long id, @RequestBody Location location) {
        Location existing = locationMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        location.setId(id);
        locationMapper.update(location);
        return ResponseEntity.ok(location);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLocation(@PathVariable Long id) {
        Location existing = locationMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        // Could perform checking to see if there are SpareParts in this location before
        // deleting
        locationMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/spare-parts")
    public ResponseEntity<List<SparePart>> getSparePartsByLocation(@PathVariable Long id) {
        Location existing = locationMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        List<SparePart> parts = sparePartMapper.findAll().stream()
                .filter(p -> id.equals(p.getLocationId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(parts);
    }
}
