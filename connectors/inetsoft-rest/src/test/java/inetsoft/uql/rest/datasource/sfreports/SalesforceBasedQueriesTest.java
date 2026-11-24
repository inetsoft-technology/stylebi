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
package inetsoft.uql.rest.datasource.sfreports;

import com.sun.net.httpserver.HttpServer;
import inetsoft.test.*;
import inetsoft.uql.rest.datasource.remedyforce.RemedyforceDataSource;
import inetsoft.uql.rest.datasource.remedyforce.RemedyforceQuery;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Salesforce Reports and Remedyforce queries after a legacy login against a local server: the
 * requests go to the instance the login returned, with the session id as bearer token, under
 * /services/data (Reports) and /services/apexrest/BMCServiceDesk (Remedyforce).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SalesforceBasedQueriesTest {
   static class ReportsSource extends SalesforceReportsDataSource {
      ReportsSource(String loginUrl) {
         this.loginUrl = loginUrl;
      }

      @Override
      protected String getLoginUrl() {
         return loginUrl;
      }

      private final String loginUrl;
   }

   static class RemedySource extends RemedyforceDataSource {
      RemedySource(String loginUrl) {
         this.loginUrl = loginUrl;
      }

      @Override
      protected String getLoginUrl() {
         return loginUrl;
      }

      private final String loginUrl;
   }

   @BeforeEach
   void startServer() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
         byte[] body = ("<?xml version=\"1.0\"?><soapenv:Envelope xmlns:soapenv=" +
            "\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns=\"urn:partner.soap.sforce.com\">" +
            "<soapenv:Body><loginResponse><result><serverUrl>https://na99.example.com/services/" +
            "Soap/u/35.0/00Dxx</serverUrl><sessionId>SID-9</sessionId></result></loginResponse>" +
            "</soapenv:Body></soapenv:Envelope>").getBytes(StandardCharsets.UTF_8);
         exchange.sendResponseHeaders(200, body.length);

         try(OutputStream output = exchange.getResponseBody()) {
            output.write(body);
         }
      });
      server.start();
   }

   @AfterEach
   void stopServer() {
      server.stop(0);
   }

   private String login() {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/services/Soap/u/35.0";
   }

   private static void fill(inetsoft.uql.rest.json.EndpointJsonQuery<?> query, String endpoint) {
      query.setJsonPath("$");
      query.setEndpoint(endpoint);
      RestParameters parameters = query.getParameters();

      for(RestParameter parameter : parameters.getParameters()) {
         if(parameter.isRequired()) {
            parameter.setValue("1");
         }
      }

      query.setParameters(parameters);
   }

   private ReportsSource reports(String apiVersion) {
      ReportsSource ds = new ReportsSource(login());
      ds.setName("sfr-" + System.nanoTime());
      ds.setUser("u-" + System.nanoTime());
      ds.setPassword("pw");
      ds.setSecurityToken("T");

      if(apiVersion != null) {
         ds.setApiVersion(apiVersion);
      }

      return ds;
   }

   @Test
   void everyReportsEndpointGoesToTheInstanceWithTheSessionAndNoLeftoverTemplate()
      throws Exception
   {
      for(String name : new SalesforceReportsQuery().getEndpointMap().keySet()) {
         SalesforceReportsQuery query = new SalesforceReportsQuery();
         query.setDataSource(reports("v50.0"));
         fill(query, name);
         ScriptedHttpHandler http = new ScriptedHttpHandler().on(url -> true, "{}");
         ReplaySupport.runTable(query, http);

         assertFalse(http.requests().isEmpty(), name);
         URI uri = URI.create(http.requests().get(0));
         assertEquals("na99.example.com", uri.getHost(), name);
         assertTrue(uri.getPath().startsWith("/services/data/v"), name + " " + uri);
         assertFalse(http.requests().get(0).contains("@apiVersion@"), name);

         if(query.getEndpointMap().get(name).getSuffix().contains("@apiVersion@")) {
            assertTrue(uri.getPath().startsWith("/services/data/v50.0/analytics/"),
                       name + " " + uri);
         }
      }
   }

   @Test
   void remedyforceEndpointsGoToTheApexRestPath() throws Exception {
      for(String name : new RemedyforceQuery().getEndpointMap().keySet()) {
         RemedyforceQuery query = new RemedyforceQuery();
         RemedySource ds = new RemedySource(login());
         ds.setName("rf-" + System.nanoTime());
         ds.setUser("u-" + System.nanoTime());
         ds.setPassword("pw");
         ds.setSecurityToken("T");
         query.setDataSource(ds);
         fill(query, name);
         ScriptedHttpHandler http = new ScriptedHttpHandler().on(url -> true, "{}");
         ReplaySupport.runTable(query, http);

         assertFalse(http.requests().isEmpty(), name);
         URI uri = URI.create(http.requests().get(0));
         assertEquals("na99.example.com", uri.getHost(), name);
         assertTrue(uri.getPath().startsWith("/services/apexrest/BMCServiceDesk/"),
                    name + " " + uri);
      }
   }

   private HttpServer server;
}
