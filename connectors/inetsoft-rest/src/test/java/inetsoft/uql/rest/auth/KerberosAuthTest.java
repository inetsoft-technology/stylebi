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

import com.sun.security.jgss.ExtendedGSSContext;
import com.sun.security.jgss.ExtendedGSSCredential;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.KerberosCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.ietf.jgss.*;
import org.junit.jupiter.api.*;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import java.security.PrivilegedExceptionAction;
import java.util.Base64;
import java.util.concurrent.Future;

/**
 * Test basic kerberos auth and impersonated auth
 *
 * disabled - requires a really complex setup to run but here for reference
 */
public class KerberosAuthTest {
   @BeforeEach
   void setUp() {
      System.setProperty("java.security.auth.login.config", getClass().getResource("login.conf").getPath());
//      System.setProperty("java.security.krb5.conf", "/etc/krb5.conf");
      System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
      System.setProperty("sun.security.krb5.debug", "false");
   }

   @Disabled
   @Test
   public void makeKerberosRequest() {
      GSSManager manager = GSSManager.getInstance();
      try {
         CloseableHttpAsyncClient client;

         // if principal/storeKey not listed in login.conf
         final GSSCredential serviceCredentials = manager.createCredential(GSSCredential.INITIATE_ONLY);

         final BasicCredentialsProvider cp = new BasicCredentialsProvider();
         cp.setCredentials(new AuthScope(null, -1, null), new KerberosCredentials(serviceCredentials));
         client = HttpAsyncClients.custom()
            .setDefaultCredentialsProvider(cp)
            .build();
         client.start();
         final Future<HttpResponse> execute = client.execute(HttpHost.create("http://localhost"), new HttpGet(), null);
         final HttpResponse httpResponse = execute.get();
         Assertions.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
      }
      catch(Exception e) {
         e.printStackTrace();
      }
   }

   @Disabled
   @Test
   public void makeImpersonatedRequest() throws Exception {
      final GSSManager manager = GSSManager.getInstance();

      // may need callback handler if keytab is out of order
      final LoginContext lc = new LoginContext("javaservice", callbacks -> {});
      lc.login();
      final Subject serviceSubject = lc.getSubject();

      // auth impersonated
      final GSSCredential impersonated = Subject.doAs(serviceSubject, (PrivilegedExceptionAction<GSSCredential>) () -> {
         GSSCredential serviceCredentials = manager.createCredential(GSSCredential.INITIATE_ONLY);
         GSSName other = manager.createName("inetsoft", GSSName.NT_USER_NAME);
         return ((ExtendedGSSCredential) serviceCredentials).impersonate(other);
      });

      // proxy impersonated to service
      final ExtendedGSSContext context = Subject.doAs(serviceSubject, (PrivilegedExceptionAction<ExtendedGSSContext>) () -> {
         GSSName servicePrincipal = manager.createName("HTTP/localhost", new Oid("1.2.840.113554.1.2.2.1"));
         final ExtendedGSSContext extendedContext = (ExtendedGSSContext) manager.createContext(servicePrincipal,
                                                                                        new Oid("1.3.6.1.5.5.2"),
                                                                                        impersonated,
                                                                                        GSSContext.DEFAULT_LIFETIME);
         extendedContext.requestMutualAuth(true);
         return extendedContext;

      });

      byte[] token = new byte[0];
      token = context.initSecContext(token, 0, token.length);
      final String result = Base64.getEncoder().encodeToString(token);
      System.out.println("Token " + result);
      Assertions.assertNotNull(result);

      final BasicCredentialsProvider cp = new BasicCredentialsProvider();
      cp.setCredentials(new AuthScope(null, -1, null), new KerberosCredentials(impersonated));
      CloseableHttpAsyncClient client = HttpAsyncClients.custom()
         .setDefaultCredentialsProvider(cp)
         .build();
      client.start();
      final Future<HttpResponse> execute = client.execute(HttpHost.create("http://localhost"), new HttpGet(), null);
      final HttpResponse httpResponse = execute.get();
      Assertions.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
   }
}
