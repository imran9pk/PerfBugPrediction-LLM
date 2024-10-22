package bisq.desktop.main.funds.transactions;

import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.model.Tradable;

import org.bitcoinj.core.Transaction;

class TransactionAwareOpenOffer implements TransactionAwareTradable {
    private final OpenOffer delegate;

    TransactionAwareOpenOffer(OpenOffer delegate) {
        this.delegate = delegate;
    }

    public boolean isRelatedToTransaction(Transaction transaction) {
        Offer offer = delegate.getOffer();
        String paymentTxId = offer.getOfferFeePaymentTxId();

        String txId = transaction.getTxId().toString();

        return paymentTxId != null && paymentTxId.equals(txId);
    }

    public Tradable asTradable() {
        return delegate;
    }
}
