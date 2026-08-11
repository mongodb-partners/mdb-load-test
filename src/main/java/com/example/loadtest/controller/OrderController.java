package com.example.loadtest.controller;

import com.example.loadtest.dto.BulkIdsRequest;
import com.example.loadtest.dto.BulkResponse;
import com.example.loadtest.dto.OrderRequest;
import com.example.loadtest.dto.OrderResponse;
import com.example.loadtest.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PostMapping("/bulk")
    public ResponseEntity<BulkResponse> bulkCreate(@RequestBody @NotEmpty List<@Valid OrderRequest> reqs) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.bulkCreate(reqs));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/bulk-get")
    public List<OrderResponse> bulkGet(@Valid @RequestBody BulkIdsRequest req) {
        return service.bulkGet(req.ids());
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }
}
