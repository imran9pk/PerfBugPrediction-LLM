package org.dcache.restful.util.billing;

import diskCacheV111.util.CacheException;
import dmg.cells.nucleus.CellPath;
import dmg.cells.nucleus.NoRouteToCellException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dcache.restful.services.billing.BillingInfoService;
import org.dcache.util.collector.CellMessagingCollector;
import org.dcache.vehicles.billing.BillingDataRequestMessage;
import org.dcache.vehicles.billing.RecordRequestMessage;

public class BillingInfoCollector
      extends CellMessagingCollector<Map<String, Future<BillingDataRequestMessage>>> {

    private CellPath billingPath;

    public Map<String, Future<BillingDataRequestMessage>> collectData() {
        Map<String, Future<BillingDataRequestMessage>> replies = new TreeMap<>();

        List<BillingDataRequestMessage> messages
              = BillingInfoCollectionUtils.generateMessages();

        for (BillingDataRequestMessage message : messages) {
            String key = BillingInfoCollectionUtils.getKey(message);
            try {
                replies.put(key, stub.send(billingPath, message));
            } catch (IllegalStateException e) {
                replies.put(key, new Future<BillingDataRequestMessage>() {
                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return false;
                    }

                    @Override
                    public BillingDataRequestMessage get()
                          throws InterruptedException,
                          ExecutionException {
                        return message;
                    }

                    @Override
                    public BillingDataRequestMessage get(long timeout,
                          TimeUnit unit)
                          throws InterruptedException,
                          ExecutionException,
                          TimeoutException {
                        return message;
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public boolean isDone() {
                        return true;
                    }
                });
            }
        }

        return replies;
    }

    public <M extends RecordRequestMessage> M sendRecordRequest(M message)
          throws InterruptedException, CacheException,
          NoRouteToCellException {
        return stub.sendAndWait(billingPath, message);
    }

    public void setBillingPath(CellPath billingPath) {
        this.billingPath = billingPath;
    }
}
