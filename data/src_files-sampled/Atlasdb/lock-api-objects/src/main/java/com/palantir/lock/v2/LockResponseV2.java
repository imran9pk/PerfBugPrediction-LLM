package com.palantir.lock.v2;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.function.Function;
import org.immutables.value.Value;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(LockResponseV2.Successful.class),
    @JsonSubTypes.Type(LockResponseV2.Unsuccessful.class)
})
public interface LockResponseV2 {
    <T> T accept(Visitor<T> visitor);

    @Value.Immutable
    @JsonSerialize(as = ImmutableSuccessful.class)
    @JsonDeserialize(as = ImmutableSuccessful.class)
    @JsonTypeName("success")
    interface Successful extends LockResponseV2 {
        LockToken getToken();

        Lease getLease();

        @Override
        default <T> T accept(Visitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    @Value.Immutable(singleton = true)
    @JsonSerialize(as = ImmutableUnsuccessful.class)
    @JsonDeserialize(as = ImmutableUnsuccessful.class)
    @JsonTypeName("failure")
    interface Unsuccessful extends LockResponseV2 {
        @Override
        default <T> T accept(Visitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    interface Visitor<T> {
        T visit(Successful successful);

        T visit(Unsuccessful failure);

        static <T> Visitor<T> of(Function<Successful, T> successFunction, Function<Unsuccessful, T> failureFunction) {
            return new Visitor<T>() {
                @Override
                public T visit(Successful successful) {
                    return successFunction.apply(successful);
                }

                @Override
                public T visit(Unsuccessful failure) {
                    return failureFunction.apply(failure);
                }
            };
        }
    }

    static LockResponseV2 successful(LockToken lockToken, Lease lease) {
        return ImmutableSuccessful.builder().token(lockToken).lease(lease).build();
    }

    static LockResponseV2 timedOut() {
        return ImmutableUnsuccessful.of();
    }
}
