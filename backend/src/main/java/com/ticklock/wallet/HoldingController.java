package com.ticklock.wallet;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/holdings")
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    /** 보유 종목 목록. 현재가는 계속 변하므로 서버가 아니라 화면이 실시간 시세로 평가손익을 계산한다. */
    @GetMapping
    public List<HoldingService.HoldingView> list() {
        return holdingService.list();
    }
}
