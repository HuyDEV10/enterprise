package com.example.enterprise.supplier;

import com.example.enterprise.common.ResourceNotFoundException;
import com.example.enterprise.common.enums.RecordStatus;
import com.example.enterprise.common.enums.RiskLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SupplierRepository supplierRepository;

    public List<Supplier> findAll() {
        return supplierRepository.findAll();
    }

    public Supplier findById(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
    }

    public Supplier create(CreateSupplierRequest request) {
        Supplier s = new Supplier();
        s.setSupplierCode(request.supplierCode());
        s.setName(request.name());
        s.setEmail(request.email());
        s.setPhone(request.phone());
        s.setAddress(request.address());
        s.setCountry(request.country());
        s.setRegion(request.region());
        s.setStatus(request.status() == null ? RecordStatus.ACTIVE : request.status());
        s.setRiskLevel(request.riskLevel() == null ? RiskLevel.LOW : request.riskLevel());
        s.setNotes(request.notes());
        return supplierRepository.save(s);
    }
}