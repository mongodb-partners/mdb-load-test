package com.example.loadtest.service;

import com.example.loadtest.config.AppProperties;
import com.example.loadtest.dto.BulkResponse;
import com.example.loadtest.dto.OrderRequest;
import com.example.loadtest.dto.OrderResponse;
import com.example.loadtest.exception.NotFoundException;
import com.example.loadtest.model.Order;
import com.example.loadtest.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService extends BaseDataService {

    private final OrderRepository repository;

    public OrderService(AppProperties props, OrderRepository repository) {
        super(props);
        this.repository = repository;
    }

    public OrderResponse create(OrderRequest req) {
        return execute(
                () -> OrderResponse.from(repository.save(toEntity(req))),
                () -> simulated(UUID.randomUUID().toString(), req));
    }

    public BulkResponse bulkCreate(List<OrderRequest> reqs) {
        return execute(
                () -> {
                    List<Order> saved = repository.saveAll(reqs.stream().map(this::toEntity).toList());
                    return new BulkResponse(reqs.size(), saved.size(), saved.stream().map(Order::getId).toList());
                },
                () -> simulatedBulk(reqs.size()));
    }

    public OrderResponse get(String id) {
        return execute(
                () -> repository.findById(id).map(OrderResponse::from)
                        .orElseThrow(() -> new NotFoundException("Order not found: " + id)),
                () -> simulated(id, null));
    }

    public List<OrderResponse> bulkGet(List<String> ids) {
        return execute(
                () -> {
                    List<OrderResponse> out = new ArrayList<>();
                    repository.findAllById(ids).forEach(o -> out.add(OrderResponse.from(o)));
                    return out;
                },
                () -> ids.stream().map(id -> simulated(id, null)).toList());
    }

    public List<OrderResponse> list(int page, int size) {
        return execute(
                () -> repository.findAll(PageRequest.of(page, size)).map(OrderResponse::from).getContent(),
                () -> simulatedList(size));
    }

    // ---- mapping / simulated helpers --------------------------------------

    private Order toEntity(OrderRequest req) {
        Order o = new Order();
        o.setCustomerId(req.customerId());
        List<Order.OrderItem> items = req.items().stream().map(i -> {
            Order.OrderItem oi = new Order.OrderItem();
            oi.setProductId(i.productId());
            oi.setQuantity(i.quantity());
            oi.setUnitPrice(i.unitPrice());
            return oi;
        }).toList();
        o.setItems(items);
        o.setStatus(req.status() == null || req.status().isBlank() ? "NEW" : req.status());
        o.setTotalAmount(req.items().stream().mapToDouble(i -> i.quantity() * i.unitPrice()).sum());
        o.setCreatedAt(Instant.now());
        return o;
    }

    private OrderResponse simulated(String id, OrderRequest req) {
        double total = req == null ? 0.0
                : req.items().stream().mapToDouble(i -> i.quantity() * i.unitPrice()).sum();
        List<OrderResponse.Item> items = req == null ? List.of()
                : req.items().stream().map(i -> new OrderResponse.Item(i.productId(), i.quantity(), i.unitPrice())).toList();
        return new OrderResponse(id, req == null ? "sim-customer" : req.customerId(),
                items, "NEW", total, Instant.EPOCH);
    }

    private BulkResponse simulatedBulk(int n) {
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(UUID.randomUUID().toString());
        }
        return new BulkResponse(n, n, ids);
    }

    private List<OrderResponse> simulatedList(int n) {
        List<OrderResponse> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(simulated(UUID.randomUUID().toString(), null));
        }
        return out;
    }
}
