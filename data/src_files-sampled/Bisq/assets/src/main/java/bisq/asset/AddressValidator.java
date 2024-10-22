package bisq.asset;

public interface AddressValidator {

    AddressValidationResult validate(String address);
}
