package bisq.asset.coins;

import bisq.asset.AddressValidationResult;
import bisq.asset.Base58AddressValidator;
import bisq.asset.Coin;
import bisq.asset.NetworkParametersAdapter;

public class MobitGlobal extends Coin {

    public MobitGlobal() {
        super("MobitGlobal", "MBGL", new MobitGlobalAddressValidator());
    }


    public static class MobitGlobalAddressValidator extends Base58AddressValidator {

        public MobitGlobalAddressValidator() {
            super(new MobitGlobalParams());
        }

        @Override
        public AddressValidationResult validate(String address) {
            if (!address.matches("^[M][a-zA-Z1-9]{33}$"))
                return AddressValidationResult.invalidStructure();

            return super.validate(address);
        }
    }


    public static class MobitGlobalParams extends NetworkParametersAdapter {

        public MobitGlobalParams() {
            addressHeader = 50;
            p2shHeader = 110;
        }
    }
}
