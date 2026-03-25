package com.langdong.spare.controller;

import com.langdong.spare.dto.EquipmentSparePartDTO;
import com.langdong.spare.entity.Equipment;
import com.langdong.spare.entity.EquipmentSparePart;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.mapper.EquipmentMapper;
import com.langdong.spare.mapper.EquipmentSparePartMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/equipments")
public class EquipmentController {

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private EquipmentSparePartMapper equipmentSparePartMapper;

    @Autowired
    private SparePartMapper sparePartMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('base:equipment:list')")
    public List<Equipment> getAll() {
        return equipmentMapper.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('base:equipment:list')")
    public ResponseEntity<Equipment> getById(@PathVariable Long id) {
        Equipment e = equipmentMapper.findById(id);
        if (e == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(e);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('base:equipment:list')")
    public Equipment create(@RequestBody Equipment equipment) {
        equipmentMapper.insert(equipment);
        return equipment;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('base:equipment:list')")
    public ResponseEntity<Equipment> update(@PathVariable Long id, @RequestBody Equipment equipment) {
        Equipment existing = equipmentMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        equipment.setId(id);
        equipmentMapper.update(equipment);
        return ResponseEntity.ok(equipment);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('base:equipment:list')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Equipment existing = equipmentMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        equipmentSparePartMapper.deleteByEquipmentId(id);
        equipmentMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/spare-parts")
    @PreAuthorize("hasAuthority('base:equipment:list')")
    public ResponseEntity<List<SparePart>> getLinkedSpareParts(@PathVariable Long id) {
        Equipment existing = equipmentMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        List<SparePart> linkedParts = sparePartMapper.findByEquipmentId(id);
        return ResponseEntity.ok(linkedParts);
    }

    @PostMapping("/{id}/spare-parts")
    @PreAuthorize("hasAuthority('base:equipment:list')")
    public ResponseEntity<?> linkSparePart(@PathVariable Long id, @RequestBody EquipmentSparePartDTO dto) {
        Equipment existing = equipmentMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        EquipmentSparePart esp = equipmentSparePartMapper.findByEqAndSpId(id, dto.getSparePartId());
        if (esp != null) {
            esp.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : esp.getQuantity() + 1);
            equipmentSparePartMapper.updateQuantity(esp);
        } else {
            esp = new EquipmentSparePart();
            esp.setEquipmentId(id);
            esp.setSparePartId(dto.getSparePartId());
            esp.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
            equipmentSparePartMapper.insert(esp);
        }

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/spare-parts/{spId}")
    @PreAuthorize("hasAuthority('base:equipment:list')")
    public ResponseEntity<?> unlinkSparePart(@PathVariable Long id, @PathVariable Long spId) {
        int res = equipmentSparePartMapper.deleteByEqAndSpId(id, spId);
        if (res > 0) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
