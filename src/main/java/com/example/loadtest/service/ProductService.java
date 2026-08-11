package com.example.loadtest.service;

import com.example.loadtest.config.AppProperties;
import com.example.loadtest.dto.ProductRequest;
import com.example.loadtest.dto.ProductResponse;
import com.example.loadtest.dto.ProductUpdate;
import com.example.loadtest.dto.BulkResponse;
import com.example.loadtest.exception.NotFoundException;
import com.example.loadtest.model.Product;
import com.example.loadtest.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService extends BaseDataService {

    private final ProductRepository repository;
    private final MongoTemplate mongoTemplate;

    public ProductService(AppProperties props, ProductRepository repository, MongoTemplate mongoTemplate) {
        super(props);
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    public ProductResponse create(ProductRequest req) {
        return execute(
                () -> ProductResponse.from(repository.save(toEntity(new Product(), req))),
                () -> simulated(UUID.randomUUID().toString(), req));
    }

    public BulkResponse bulkCreate(List<ProductRequest> reqs) {
        return execute(
                () -> {
                    List<Product> entities = reqs.stream().map(r -> toEntity(new Product(), r)).toList();
                    List<Product> saved = repository.saveAll(entities);
                    return new BulkResponse(reqs.size(), saved.size(), saved.stream().map(Product::getId).toList());
                },
                () -> simulatedBulk(reqs.size()));
    }

    public ProductResponse get(String id) {
        return execute(
                () -> repository.findById(id).map(ProductResponse::from)
                        .orElseThrow(() -> new NotFoundException("Product not found: " + id)),
                () -> simulated(id, null));
    }

    public List<ProductResponse> bulkGet(List<String> ids) {
        return execute(
                () -> {
                    List<ProductResponse> out = new ArrayList<>();
                    repository.findAllById(ids).forEach(p -> out.add(ProductResponse.from(p)));
                    return out;
                },
                () -> ids.stream().map(id -> simulated(id, null)).toList());
    }

    public List<ProductResponse> list(int page, int size) {
        return execute(
                () -> repository.findAll(PageRequest.of(page, size)).map(ProductResponse::from).getContent(),
                () -> simulatedList(size));
    }

    public ProductResponse update(String id, ProductRequest req) {
        return execute(
                () -> {
                    Product existing = repository.findById(id)
                            .orElseThrow(() -> new NotFoundException("Product not found: " + id));
                    return ProductResponse.from(repository.save(toEntity(existing, req)));
                },
                () -> simulated(id, req));
    }

    public BulkResponse bulkUpdate(List<ProductUpdate> updates) {
        return execute(
                () -> {
                    BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Product.class);
                    for (ProductUpdate u : updates) {
                        Query q = new Query(Criteria.where("_id").is(u.id()));
                        Update update = new Update()
                                .set("sku", u.sku())
                                .set("name", u.name())
                                .set("description", u.description())
                                .set("category", u.category())
                                .set("price", u.price());
                        bulk.updateOne(q, update);
                    }
                    int modified = bulk.execute().getModifiedCount();
                    return new BulkResponse(updates.size(), modified, updates.stream().map(ProductUpdate::id).toList());
                },
                () -> new BulkResponse(updates.size(), updates.size(),
                        updates.stream().map(ProductUpdate::id).toList()));
    }

    // ---- mapping / simulated helpers --------------------------------------

    private Product toEntity(Product p, ProductRequest req) {
        p.setSku(req.sku());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setCategory(req.category());
        p.setPrice(req.price());
        // NOTE: products created via the API are not embedded (only seeded products
        // are), so they won't appear in vector-search results.
        return p;
    }

    /** Source text for a product embedding (name + description). */
    public static String embedText(String name, String description) {
        return description == null || description.isBlank() ? name : name + ". " + description;
    }

    private ProductResponse simulated(String id, ProductRequest req) {
        if (req != null) {
            return new ProductResponse(id, req.sku(), req.name(), req.description(), req.category(), req.price());
        }
        return new ProductResponse(id, "SIM-SKU", "Simulated Product", "simulated", "sim", 0.0);
    }

    private BulkResponse simulatedBulk(int n) {
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(UUID.randomUUID().toString());
        }
        return new BulkResponse(n, n, ids);
    }

    private List<ProductResponse> simulatedList(int n) {
        List<ProductResponse> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(simulated(UUID.randomUUID().toString(), null));
        }
        return out;
    }
}
