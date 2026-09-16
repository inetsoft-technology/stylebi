/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.auth;

import com.sun.security.jgss.ExtendedGSSCredential;
import inetsoft.uql.rest.AbstractRestDataSource;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.KerberosCredentials;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.ietf.jgss.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.lang.invoke.MethodHandles;
import java.security.*;

/**
 * Configure the execution context to authenticate to kerberos via GSSAPI
 */
public class KerberosAuthenticator implements RestAuthenticator {
   public KerberosAuthenticator(AbstractRestDataSource ds) {
      this.ds = ds;
   }

   @Override
   public void authenticateRequest(HttpRequestBase request, HttpClientContext context) {
      final GSSManager manager = GSSManager.getInstance();

      try {
         final String spn = ds.getServicePrincipalName();
         final GSSCredential serviceCredentials;

         if(ds.isConstrainedDelegation()) {
            // resolved by the data source so that the identity impersonated here and the
            // one used to discriminate the query cache key cannot drift apart (Bug #76658)
            final String impersonate = ds.getImpersonatedIdentity();

            // may need callback handler if keytab is out of order
            final String serviceName = ds.getConfigurationServiceName();
            final LoginContext lc = new LoginContext(serviceName, callbacks -> {});
            lc.login();
            final Subject serviceSubject = lc.getSubject();

            // auth impersonated
            serviceCredentials =
               Subject.doAs(serviceSubject, (PrivilegedExceptionAction<GSSCredential>) () -> {
                  final GSSCredential cred = getGSSCredential(manager, spn);
                  final GSSName other = manager.createName(impersonate, GSSName.NT_USER_NAME);
                  return ((ExtendedGSSCredential) cred).impersonate(other);
               });

         }
         else {
            serviceCredentials = getGSSCredential(manager, spn);
         }

         final BasicCredentialsProvider cp = new BasicCredentialsProvider();
         final AuthScope authscope = new AuthScope(null, -1, null);
         final KerberosCredentials credentials = new KerberosCredentials(serviceCredentials);
         cp.setCredentials(authscope, credentials);
         context.setCredentialsProvider(cp);
      }
      catch(GSSException | LoginException | PrivilegedActionException e) {
         LOG.error("Failed to authenticate to the KDC", e);
      }
   }

   /**
    * Try to find the TGT for the SPN
    *
    * @param manager GSS manager
    * @param spn the SPN to use or null for the default principal (JAAS or system property)
    *
    * @return the GSS credential for the principal
    */
   private GSSCredential getGSSCredential(GSSManager manager, String spn) throws GSSException {
      final GSSName name = spn != null ? manager.createName(spn, GSSName.NT_USER_NAME) : null;
      return manager.createCredential(name,
                                      GSSCredential.DEFAULT_LIFETIME,
                                      (Oid) null,
                                      GSSCredential.INITIATE_ONLY);
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private final AbstractRestDataSource ds;
}
