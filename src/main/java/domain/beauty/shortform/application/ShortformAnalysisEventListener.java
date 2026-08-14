package domain.beauty.shortform.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ShortformAnalysisEventListener {

    private final ShortformAnalysisJobHandler jobHandler;

    public ShortformAnalysisEventListener(ShortformAnalysisJobHandler jobHandler) {
        this.jobHandler = jobHandler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ShortformAnalysisRequested event) {
        jobHandler.analyze(event.analysisId());
    }
}
