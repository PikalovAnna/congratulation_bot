package org.pikalovanna.congratulationsbot.config;

import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TransactionalTemplate extends TransactionTemplate {

    public TransactionalTemplate(PlatformTransactionManager transactionManager) {
        super(transactionManager);
    }

    public void invoke(Callback callback) throws TransactionException {
        this.execute(new TransactionCallbackWithoutResult() {
            @Override
            @SneakyThrows
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                callback.call(status);
            }
        });
    }

    @FunctionalInterface
    public interface Callback {
        void call(TransactionStatus status) throws Throwable;
    }
}
