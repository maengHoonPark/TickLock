package com.ticklock.order;

import com.ticklock.common.ApiException;
import com.ticklock.tick.StockCatalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final int MAX_KEY_LENGTH = 64;

    /** 주문 등록 요청 본문. 검증 규칙을 필드에 선언해 두면 컨트롤러 코드에 if문이 쌓이지 않는다. */
    public record CreateOrderRequest(
            @NotNull(message = "종목을 선택해 주세요.") String symbol,
            @NotNull(message = "매수/매도를 선택해 주세요.") OrderSide side,
            @NotNull(message = "발동 조건을 선택해 주세요.") TriggerCondition condition,
            @Positive(message = "목표 가격은 1원 이상이어야 해요.")
            @Max(value = 100_000_000, message = "목표 가격이 너무 커요.") long targetPrice,
            @Positive(message = "수량은 1주 이상이어야 해요.")
            @Max(value = 1_000_000, message = "수량이 너무 많아요.") long quantity) {
    }

    /** 화면에 내려주는 주문 모양. 엔티티를 직접 노출하지 않아 DB 구조가 바뀌어도 API는 유지된다. */
    public record OrderView(long id, String symbol, String name, OrderSide side, TriggerCondition condition,
                            long targetPrice, long quantity, OrderStatus status, Instant createdAt) {
    }

    private final OrderService orderService;
    private final StockCatalog stockCatalog;

    public OrderController(OrderService orderService, StockCatalog stockCatalog) {
        this.orderService = orderService;
        this.stockCatalog = stockCatalog;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                            @Valid @RequestBody CreateOrderRequest request) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key는 1~" + MAX_KEY_LENGTH + "자여야 해요.");
        }
        TriggerOrder order = orderService.create(new OrderService.CreateCommand(
                request.symbol(), request.side(), request.condition(),
                request.targetPrice(), request.quantity(), idempotencyKey));
        return toView(order);
    }

    @GetMapping
    public List<OrderView> list() {
        List<OrderView> views = new ArrayList<>();
        for (TriggerOrder order : orderService.list()) {
            views.add(toView(order));
        }
        return views;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable long id) {
        orderService.cancel(id);
    }

    private OrderView toView(TriggerOrder order) {
        return new OrderView(order.getId(), order.getSymbol(), stockCatalog.nameOf(order.getSymbol()),
                order.getSide(), order.getTriggerCondition(), order.getTargetPrice(), order.getQuantity(),
                order.getStatus(), order.getCreatedAt());
    }
}
