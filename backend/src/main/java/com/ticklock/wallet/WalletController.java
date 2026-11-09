package com.ticklock.wallet;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    /** 화면에 보여줄 지갑 정보. 엔티티를 그대로 내보내지 않고 필요한 값만 담은 record로 응답한다. */
    public record WalletView(long balance) {
    }

    /** 정합성 검사 결과. consistent=false이면 실제 잔액과 체결 기록이 맞지 않는다는 뜻. */
    public record AuditView(long balance, long expectedBalance, long difference, long executedCount,
                            boolean consistent) {
    }

    private final DemoAccount demoAccount;
    private final WalletAudit walletAudit;

    public WalletController(DemoAccount demoAccount, WalletAudit walletAudit) {
        this.demoAccount = demoAccount;
        this.walletAudit = walletAudit;
    }

    @GetMapping
    public WalletView get() {
        return new WalletView(demoAccount.wallet().getBalance());
    }

    @GetMapping("/audit")
    public AuditView audit() {
        WalletAudit.Result result = walletAudit.run();
        return new AuditView(result.balance(), result.expectedBalance(), result.difference(),
                result.executedCount(), result.consistent());
    }
}
