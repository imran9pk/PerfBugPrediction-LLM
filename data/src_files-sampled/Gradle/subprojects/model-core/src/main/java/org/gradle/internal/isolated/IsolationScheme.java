package org.gradle.internal.isolated;

import com.google.common.reflect.TypeToken;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;

public class IsolationScheme<IMPLEMENTATION, PARAMS> {
    private final Class<IMPLEMENTATION> interfaceType;
    private final Class<PARAMS> paramsType;
    private final Class<? extends PARAMS> noParamsType;

    public IsolationScheme(Class<IMPLEMENTATION> interfaceType, Class<PARAMS> paramsType, Class<? extends PARAMS> noParamsType) {
        this.interfaceType = interfaceType;
        this.paramsType = paramsType;
        this.noParamsType = noParamsType;
    }

    @Nullable
    public <T extends IMPLEMENTATION, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType) {
        return parameterTypeFor(implementationType, 0);
    }

    @Nullable
    public <T extends IMPLEMENTATION, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType, int typeArgumentIndex) {
        if (implementationType == interfaceType) {
            return null;
        }
        Class<P> parametersType = inferParameterType(implementationType, typeArgumentIndex);
        if (parametersType == paramsType) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Could not create the parameters for ");
            formatter.appendType(implementationType);
            formatter.append(": must use a sub-type of ");
            formatter.appendType(parametersType);
            formatter.append(" as the parameters type. Use ");
            formatter.appendType(noParamsType);
            formatter.append(" as the parameters type for implementations that do not take parameters.");
            throw new IllegalArgumentException(formatter.toString());
        }
        if (parametersType == noParamsType) {
            return null;
        }
        return parametersType;
    }

    @NotNull
    private <T extends IMPLEMENTATION, P extends PARAMS> Class<P> inferParameterType(Class<T> implementationType, int typeArgumentIndex) {
        for (Type superType : implementationType.getGenericInterfaces()) {
            if (superType instanceof ParameterizedType) {
                ParameterizedType parameterizedSuperType = (ParameterizedType) superType;
                if (parameterizedSuperType.getRawType().equals(interfaceType)) {
                    Type argument = parameterizedSuperType.getActualTypeArguments()[typeArgumentIndex];
                    if (argument instanceof Class) {
                        return Cast.uncheckedCast(argument);
                    }
                }
            }
        }
        ParameterizedType superType = (ParameterizedType) TypeToken.of(implementationType).getSupertype(interfaceType).getType();
        return Cast.uncheckedNonnullCast(TypeToken.of(superType.getActualTypeArguments()[typeArgumentIndex]).getRawType());
    }

    public ServiceLookup servicesForImplementation(@Nullable PARAMS params, ServiceLookup allServices) {
        return servicesForImplementation(params, allServices, Collections.emptyList(), c -> false);
    }

    public ServiceLookup servicesForImplementation(@Nullable PARAMS params, ServiceLookup allServices, Collection<? extends Class<?>> additionalWhiteListedServices, Spec<Class<?>> whiteListPolicy) {
        return new ServicesForIsolatedObject(interfaceType, noParamsType, params, allServices, additionalWhiteListedServices, whiteListPolicy);
    }

    private static class ServicesForIsolatedObject implements ServiceLookup {
        private final Class<?> interfaceType;
        private final Class<?> noParamsType;
        private final Collection<? extends Class<?>> additionalWhiteListedServices;
        private final ServiceLookup allServices;
        private final Object params;
        private final Spec<Class<?>> whiteListPolicy;

        public ServicesForIsolatedObject(
            Class<?> interfaceType,
            Class<?> noParamsType,
            @Nullable Object params,
            ServiceLookup allServices,
            Collection<? extends Class<?>> additionalWhiteListedServices,
            Spec<Class<?>> whiteListPolicy
        ) {
            this.interfaceType = interfaceType;
            this.noParamsType = noParamsType;
            this.additionalWhiteListedServices = additionalWhiteListedServices;
            this.allServices = allServices;
            this.params = params;
            this.whiteListPolicy = whiteListPolicy;
        }

        @Nullable
        @Override
        public Object find(Type serviceType) throws ServiceLookupException {
            if (serviceType instanceof Class) {
                Class<?> serviceClass = Cast.uncheckedNonnullCast(serviceType);
                if (serviceClass.isInstance(params)) {
                    return params;
                }
                if (serviceClass.isAssignableFrom(noParamsType)) {
                    throw new ServiceLookupException(String.format("Cannot query the parameters of an instance of %s that takes no parameters.", interfaceType.getSimpleName()));
                }
                if (serviceClass.isAssignableFrom(ExecOperations.class)) {
                    return allServices.find(ExecOperations.class);
                }
                if (serviceClass.isAssignableFrom(FileSystemOperations.class)) {
                    return allServices.find(FileSystemOperations.class);
                }
                if (serviceClass.isAssignableFrom(ArchiveOperations.class)) {
                    return allServices.find(ArchiveOperations.class);
                }
                if (serviceClass.isAssignableFrom(ObjectFactory.class)) {
                    return allServices.find(ObjectFactory.class);
                }
                if (serviceClass.isAssignableFrom(ProviderFactory.class)) {
                    return allServices.find(ProviderFactory.class);
                }
                for (Class<?> whiteListedService : additionalWhiteListedServices) {
                    if (serviceClass.isAssignableFrom(whiteListedService)) {
                        return allServices.find(whiteListedService);
                    }
                }
                if (whiteListPolicy.isSatisfiedBy(serviceClass)) {
                    return allServices.find(serviceClass);
                }
            }
            return null;
        }

        @Override
        public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
            Object result = find(serviceType);
            if (result == null) {
                notFound(serviceType);
            }
            return result;
        }

        @Override
        public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
            return notFound(serviceType);
        }

        private Object notFound(Type serviceType) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Services of type ");
            formatter.appendType(serviceType);
            formatter.append(" are not available for injection into instances of type ");
            formatter.appendType(interfaceType);
            formatter.append(".");
            throw new UnknownServiceException(serviceType, formatter.toString());
        }
    }
}
