package com.ticklock.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<TriggerOrder, Long> {

    Optional<TriggerOrder> findByUserIdAndRequestKey(long userId, String requestKey);

    List<TriggerOrder> findByUserIdOrderByIdDesc(long userId);

    List<TriggerOrder> findByStatus(OrderStatus status);

    /**
     * "조건부 UPDATE": 상태가 expected일 때만 next로 바꾸고, 바꾼 행 수를 돌려준다.
     *
     * 왜 조회 후 수정이 아니라 한 줄 UPDATE인가?
     *  "조회 → 확인 → 수정" 사이에 다른 요청(예: 체결)이 끼어들면 이미 체결된 주문을 취소해 버릴 수 있다.
     *  WHERE에 상태 조건을 넣으면 DB가 검사와 변경을 한 번에(원자적으로) 처리하므로 그런 틈이 없다.
     *  반환값 0 = "이미 다른 상태라서 아무것도 바꾸지 않았다".
     */
    @Transactional
    @Modifying
    @Query("update TriggerOrder o set o.status = :next where o.id = :id and o.userId = :userId and o.status = :expected")
    int changeStatus(long id, long userId, OrderStatus expected, OrderStatus next);
}
