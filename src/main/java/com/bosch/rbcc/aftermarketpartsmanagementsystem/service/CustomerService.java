package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Customer;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<Customer> getAll() {
        return customerRepository.findAll();
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
        // 检查代码是否已存在
        if (customerRepository.existsByCode(customer.getCode())) {
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

        // 检查代码是否被其他客户使用
        if (!existing.getCode().equals(customer.getCode()) &&
                customerRepository.existsByCode(customer.getCode())) {
            throw new IllegalArgumentException("Customer code already exists: " + customer.getCode());
        }

        existing.setName(customer.getName());
        existing.setCode(customer.getCode());
        return customerRepository.save(existing);
    }

    @Transactional
    public void delete(String id) {
        if (!customerRepository.existsById(id)) {
            throw new IllegalArgumentException("Customer not found: " + id);
        }
        customerRepository.deleteById(id);
    }
}
