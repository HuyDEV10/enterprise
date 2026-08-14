package com.huy.enterprise.supplier;

import com.huy.enterprise.common.*;
import com.huy.enterprise.common.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {
    private final SupplierRepository repository;

    public List<SupplierResponse> findAll(RiskLevel riskLevel) {
        List<Supplier> list = riskLevel == null ? repository.findAll() : repository.findByRiskLevel(riskLevel);
        return list.stream().map(SupplierResponse::from).toList();
    }

    public SupplierResponse findById(UUID id) { return SupplierResponse.from(get(id)); }

    @Transactional
    public SupplierResponse create(CreateSupplierRequest r) {
        if (repository.existsBySupplierCode(r.supplierCode())) throw new ConflictException("Supplier code already exists: " + r.supplierCode());
        Supplier s = new Supplier();
        s.setSupplierCode(r.supplierCode());
        apply(s, r.name(), r.email(), r.phone(), r.address(), r.country(), r.region(), r.status(), r.riskLevel(), r.notes());
        return SupplierResponse.from(repository.save(s));
    }

    @Transactional
    public SupplierResponse update(UUID id, UpdateSupplierRequest r) {
        Supplier s = get(id);
        apply(s, r.name(), r.email(), r.phone(), r.address(), r.country(), r.region(), r.status(), r.riskLevel(), r.notes());
        return SupplierResponse.from(repository.save(s));
    }

    @Transactional
    public void deactivate(UUID id) {
        Supplier s = get(id); s.setStatus(RecordStatus.INACTIVE); repository.save(s);
    }

    private Supplier get(UUID id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id)); }
    private void apply(Supplier s, String name, String email, String phone, String address, String country, String region,
            RecordStatus status, RiskLevel riskLevel, String notes) {
        s.setName(name); s.setEmail(email); s.setPhone(phone); s.setAddress(address); s.setCountry(country); s.setRegion(region);
        s.setStatus(status == null ? RecordStatus.ACTIVE : status); s.setRiskLevel(riskLevel == null ? RiskLevel.LOW : riskLevel); s.setNotes(notes);
    }
}
