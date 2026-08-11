package com.example.loadtest.service;

import com.example.loadtest.config.AppProperties;
import com.example.loadtest.dto.BulkResponse;
import com.example.loadtest.dto.CustomerRequest;
import com.example.loadtest.dto.CustomerResponse;
import com.example.loadtest.dto.CustomerUpdate;
import com.example.loadtest.exception.NotFoundException;
import com.example.loadtest.model.Customer;
import com.example.loadtest.repository.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerService extends BaseDataService {

    private final CustomerRepository repository;
    private final MongoTemplate mongoTemplate;

    public CustomerService(AppProperties props, CustomerRepository repository, MongoTemplate mongoTemplate) {
        super(props);
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    public CustomerResponse create(CustomerRequest req) {
        return execute(
                () -> CustomerResponse.from(repository.save(toEntity(new Customer(), req, true))),
                () -> simulated(UUID.randomUUID().toString(), req));
    }

    public BulkResponse bulkCreate(List<CustomerRequest> reqs) {
        return execute(
                () -> {
                    List<Customer> entities = reqs.stream().map(r -> toEntity(new Customer(), r, true)).toList();
                    List<Customer> saved = repository.saveAll(entities);
                    return new BulkResponse(reqs.size(), saved.size(), saved.stream().map(Customer::getId).toList());
                },
                () -> simulatedBulk(reqs.size()));
    }

    public CustomerResponse get(String id) {
        return execute(
                () -> repository.findById(id).map(CustomerResponse::from)
                        .orElseThrow(() -> new NotFoundException("Customer not found: " + id)),
                () -> simulated(id, null));
    }

    public List<CustomerResponse> bulkGet(List<String> ids) {
        return execute(
                () -> {
                    List<CustomerResponse> out = new ArrayList<>();
                    repository.findAllById(ids).forEach(c -> out.add(CustomerResponse.from(c)));
                    return out;
                },
                () -> ids.stream().map(id -> simulated(id, null)).toList());
    }

    public List<CustomerResponse> list(int page, int size) {
        return execute(
                () -> repository.findAll(PageRequest.of(page, size)).map(CustomerResponse::from).getContent(),
                () -> simulatedList(size));
    }

    public CustomerResponse update(String id, CustomerRequest req) {
        return execute(
                () -> {
                    Customer existing = repository.findById(id)
                            .orElseThrow(() -> new NotFoundException("Customer not found: " + id));
                    return CustomerResponse.from(repository.save(toEntity(existing, req, false)));
                },
                () -> simulated(id, req));
    }

    public BulkResponse bulkUpdate(List<CustomerUpdate> updates) {
        return execute(
                () -> {
                    BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Customer.class);
                    for (CustomerUpdate u : updates) {
                        Query q = new Query(Criteria.where("_id").is(u.id()));
                        Update update = new Update()
                                .set("email", u.email())
                                .set("name", u.name())
                                .set("phone", u.phone());
                        bulk.updateOne(q, update);
                    }
                    int modified = bulk.execute().getModifiedCount();
                    return new BulkResponse(updates.size(), modified, updates.stream().map(CustomerUpdate::id).toList());
                },
                () -> new BulkResponse(updates.size(), updates.size(),
                        updates.stream().map(CustomerUpdate::id).toList()));
    }

    // ---- mapping / simulated helpers --------------------------------------

    private Customer toEntity(Customer c, CustomerRequest req, boolean isNew) {
        c.setEmail(req.email());
        c.setName(req.name());
        c.setPhone(req.phone());
        if (isNew) {
            c.setCreatedAt(Instant.now());
        }
        return c;
    }

    private CustomerResponse simulated(String id, CustomerRequest req) {
        if (req != null) {
            return new CustomerResponse(id, req.email(), req.name(), req.phone(), Instant.EPOCH);
        }
        return new CustomerResponse(id, "sim@example.com", "Simulated Customer", "000-000-0000", Instant.EPOCH);
    }

    private BulkResponse simulatedBulk(int n) {
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(UUID.randomUUID().toString());
        }
        return new BulkResponse(n, n, ids);
    }

    private List<CustomerResponse> simulatedList(int n) {
        List<CustomerResponse> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(simulated(UUID.randomUUID().toString(), null));
        }
        return out;
    }
}
