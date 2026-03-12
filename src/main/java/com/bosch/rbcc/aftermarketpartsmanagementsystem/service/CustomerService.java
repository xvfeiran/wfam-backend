package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PageDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Customer;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.CustomerRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<Customer> getAll() {
        return customerRepository.findAll(Sort.by(Sort.Order.asc("name")));
    }

    public PageDTO<Customer> getPage(String name, String code, String sortBy, String sortOrder, Pageable pageable) {
        // Apply sorting
        Pageable sortedPageable = pageable;
        if (sortBy != null && !sortBy.isBlank()) {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
            Sort sort = Sort.by(direction, sortBy);
            sortedPageable = pageable.getSort().isUnsorted()
                ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
                : org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(pageable.getSort()));
        }

        Page<Customer> page = customerRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("name")), "%" + name.toUpperCase() + "%"));
            }
            if (code != null && !code.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("code")), "%" + code.toUpperCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, sortedPageable);
        return PageDTO.of(page);
    }

    public Customer getById(String id) {
        return customerRepository.findById(id).orElse(null);
    }

    @Transactional
    public Customer create(Customer customer) {
        // 检查名称是否已存在
        if (customerRepository.existsByName(customer.getName())) {
            throw new IllegalArgumentException("Customer name already exists: " + customer.getName());
        }
        // 检查代码是否已存在（仅当代码非空时）
        if (customer.getCode() != null && !customer.getCode().trim().isEmpty()
                && customerRepository.existsByCode(customer.getCode())) {
            throw new IllegalArgumentException("Customer code already exists: " + customer.getCode());
        }
        // 生成UUID作为ID
        customer.setId(UUID.randomUUID().toString());
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer update(String id, Customer customer) {
        Customer existing = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));

        // 检查名称是否被其他客户使用
        if (!existing.getName().equals(customer.getName()) &&
                customerRepository.existsByName(customer.getName())) {
            throw new IllegalArgumentException("Customer name already exists: " + customer.getName());
        }

        // 检查代码是否被其他客户使用（仅当代码非空时）
        String newCode = customer.getCode();
        String existingCode = existing.getCode();
        if (newCode != null && !newCode.trim().isEmpty()) {
            if (!newCode.equals(existingCode) && customerRepository.existsByCode(newCode)) {
                throw new IllegalArgumentException("Customer code already exists: " + newCode);
            }
        }

        existing.setName(customer.getName());
        existing.setCode(customer.getCode());
        return customerRepository.save(existing);
    }
}
