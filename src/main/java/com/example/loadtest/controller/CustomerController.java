package com.example.loadtest.controller;

import com.example.loadtest.dto.BulkIdsRequest;
import com.example.loadtest.dto.BulkResponse;
import com.example.loadtest.dto.CustomerRequest;
import com.example.loadtest.dto.CustomerResponse;
import com.example.loadtest.dto.CustomerUpdate;
import com.example.loadtest.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@Validated
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PostMapping("/bulk")
    public ResponseEntity<BulkResponse> bulkCreate(@RequestBody @NotEmpty List<@Valid CustomerRequest> reqs) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.bulkCreate(reqs));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/bulk-get")
    public List<CustomerResponse> bulkGet(@Valid @RequestBody BulkIdsRequest req) {
        return service.bulkGet(req.ids());
    }

    @GetMapping
    public List<CustomerResponse> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable String id, @Valid @RequestBody CustomerRequest req) {
        return service.update(id, req);
    }

    @PutMapping("/bulk")
    public BulkResponse bulkUpdate(@RequestBody @NotEmpty List<@Valid CustomerUpdate> updates) {
        return service.bulkUpdate(updates);
    }
}
