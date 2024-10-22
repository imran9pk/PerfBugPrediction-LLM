package org.dcache.gplazma.strategies;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import org.dcache.auth.attributes.Restriction;
import org.dcache.gplazma.AuthenticationException;
import org.dcache.gplazma.monitor.LoginMonitor;
import org.dcache.gplazma.monitor.LoginMonitor.Result;
import org.dcache.gplazma.plugins.GPlazmaAuthenticationPlugin;
import org.dcache.gplazma.plugins.GPlazmaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultAuthenticationStrategy implements AuthenticationStrategy {

    private static final Logger LOGGER =
          LoggerFactory.getLogger(DefaultAuthenticationStrategy.class);

    private volatile PAMStyleStrategy<GPlazmaAuthenticationPlugin> pamStyleAuthentiationStrategy;

    @Override
    public void setPlugins(List<GPlazmaPluginService<GPlazmaAuthenticationPlugin>> plugins) {
        pamStyleAuthentiationStrategy = new PAMStyleStrategy<>(plugins);
    }

    @Override
    public void authenticate(final LoginMonitor monitor,
          final Set<Object> publicCredential,
          final Set<Object> privateCredential,
          final Set<Principal> identifiedPrincipals,
          final Set<Restriction> restrictionStore)
          throws AuthenticationException {
        pamStyleAuthentiationStrategy.callPlugins(service -> {
            monitor.authPluginBegins(service.getName(), service.getControl(),
                  publicCredential, privateCredential,
                  identifiedPrincipals);

            GPlazmaAuthenticationPlugin plugin = service.getPlugin();

            Result result = Result.FAIL;
            String error = null;
            try {
                plugin.authenticate(publicCredential, privateCredential,
                      identifiedPrincipals, restrictionStore);
                result = Result.SUCCESS;
            } catch (AuthenticationException e) {
                error = e.getMessage();
                throw e;
            } finally {
                monitor.authPluginEnds(service.getName(), service.getControl(),
                      result, error, publicCredential, privateCredential,
                      identifiedPrincipals);
            }
        });
    }
}
