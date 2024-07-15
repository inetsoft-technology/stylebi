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

import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.tabular.oauth.OAuthDataSource;
import org.apache.http.nio.client.HttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

public class RestAuthenticatorFactory {
   private RestAuthenticatorFactory() {
   }

   /**
    * Create the appropriate rest authenticator for the data source.
    */
   public static RestAuthenticator createFrom(AbstractRestDataSource ds, HttpAsyncClient client) {
      final AuthType authType = ds.getAuthType();
      final RestAuthenticator restAuth;

      switch(authType) {
         case NONE:
            restAuth = new NoopAuthenticator();
            break;
         case BASIC:
            restAuth = createBasicAuth(ds);
            break;
         case TWO_STEP:
            restAuth = createTwoStepAuth(ds, client);
            break;
         case OAUTH:
            restAuth = createOAuth(ds);
            break;
         case KERBEROS:
            restAuth = createKerberosAuth(ds);
            break;
         default:
            throw new IllegalStateException("Unexpected value: " + authType);
      }

      return restAuth;
   }

   private static RestAuthenticator createKerberosAuth(AbstractRestDataSource ds) {
      if(System.getProperty(SUBJECT_CREDS_ONLY) == null) {
         System.setProperty(SUBJECT_CREDS_ONLY, "false");
      }

      System.setProperty("sun.security.krb5.debug", Boolean.toString(LOG.isDebugEnabled()));
      return new KerberosAuthenticator(ds);
   }

   private static BasicAuthenticator createBasicAuth(AbstractRestDataSource ds) {
      return new BasicAuthenticator(
         BasicAuthConfig.builder()
            .username(ds.getUser())
            .password(ds.getPassword())
            .build());
   }

   private static TwoStepAuthenticator createTwoStepAuth(AbstractRestDataSource ds,
                                                         HttpAsyncClient client)
   {
      if(ds.getAuthURL() == null || ds.getAuthURL().isEmpty()) {
         throw new IllegalArgumentException("Authentication URI must not be empty");
      }

      if(ds.getTokenPattern() == null || ds.getTokenPattern().isEmpty()) {
         throw new IllegalArgumentException("Token pattern must not be empty");
      }

      return new TwoStepAuthenticator(
         new TwoStepAuthConfig(
            ds.getAuthMethod(), ds.getContentType(), ds.getBody(), ds.getAuthURL(),
            ds.getTokenPattern(), Arrays.asList(ds.getAuthenticationHttpParameters()),
            Arrays.asList(ds.getQueryHttpParameters())), client);
   }

   /**
    * Create the authenticator that will refresh OAuth access tokens
    */
   private static RestAuthenticator createOAuth(AbstractRestDataSource ds) {
      if(!(ds instanceof OAuthDataSource)) {
         throw new IllegalArgumentException("OAuth data source type must implement OAuthDataSource");
      }

      return OAuthAuthenticator.create((AbstractRestDataSource & OAuthDataSource) ds);
   }

   private static final String SUBJECT_CREDS_ONLY = "javax.security.auth.useSubjectCredsOnly";
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
