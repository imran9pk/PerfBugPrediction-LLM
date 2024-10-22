package com.codename1.payment;

import com.codename1.util.SuccessCallback;

public interface ReceiptStore {
    
    public void fetchReceipts(SuccessCallback<Receipt[]> callback);
    
    public void submitReceipt(Receipt receipt, SuccessCallback<Boolean> callback);
}
