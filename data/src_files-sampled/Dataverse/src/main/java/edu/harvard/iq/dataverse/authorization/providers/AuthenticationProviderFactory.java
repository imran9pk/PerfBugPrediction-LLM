package edu.harvard.iq.dataverse.authorization.providers;

import edu.harvard.iq.dataverse.authorization.AuthenticationProvider;
import edu.harvard.iq.dataverse.authorization.exceptions.AuthorizationSetupException;

public interface AuthenticationProviderFactory {
    String getAlias();
    
    String getInfo();
    
    AuthenticationProvider buildProvider( AuthenticationProviderRow aRow ) throws AuthorizationSetupException;
    
}
