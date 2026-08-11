package com.example.loadtest.controller;

import com.example.loadtest.dto.BulkIdsRequest;
import com.example.loadtest.dto.BulkResponse;
import com.example.loadtest.dto.ProductRequest;
import com.example.loadtest.dto.ProductResponse;
import com.example.loadtest.dto.ProductUpdate;
import com.example.loadtest.dto.VectorSearchHit;
import com.example.loadtest.dto.VectorSearchRequest;
import com.example.loadtest.service.ProductService;
import com.example.loadtest.service.VectorSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private final ProductService service;
    private final VectorSearchService vectorSearchService;

    public ProductController(ProductService service, VectorSearchService vectorSearchService) {
        this.service = service;
        this.vectorSearchService = vectorSearchService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PostMapping("/bulk")
    public ResponseEntity<BulkResponse> bulkCreate(@RequestBody @NotEmpty List<@Valid ProductRequest> reqs) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.bulkCreate(reqs));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/bulk-get")
    public List<ProductResponse> bulkGet(@Valid @RequestBody BulkIdsRequest req) {
        return service.bulkGet(req.ids());
    }

    @GetMapping
    public List<ProductResponse> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody ProductRequest req) {
        return service.update(id, req);
    }

    @PutMapping("/bulk")
    public BulkResponse bulkUpdate(@RequestBody @NotEmpty List<@Valid ProductUpdate> updates) {
        return service.bulkUpdate(updates);
    }

    @PostMapping("/search")
    public List<VectorSearchHit> search(@Valid @RequestBody VectorSearchRequest req) {
        return vectorSearchService.search(req);
    }
}
