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
package inetsoft.uql.rest.datasource.salesforce;

import com.sun.net.httpserver.HttpServer;
import inetsoft.test.*;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81959: a failed legacy Salesforce login must report the reason from the SOAP fault in the
 * response body, not only the HTTP status.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SalesforceLoginFaultTest.CredentialConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class SalesforceLoginFaultTest {
   @Configuration
   static class CredentialConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(
            eq(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS), anyBoolean()))
            .thenAnswer(invocation -> new LocalPasswordAndOAuth2WithFlagCredentialsGrant());
         return credentialService;
      }
   }

   static class TestSource extends SalesforceDataSource<TestSource> {
      TestSource(String loginUrl) {
         super("Rest.Remedyforce", TestSource.class);
         this.loginUrl = loginUrl;
      }

      @Override
      protected String getUrlSuffix() {
         return "/data/v50.0";
      }

      @Override
      protected String getTestSuffix() {
         return "/data";
      }

      @Override
      protected String getLoginUrl() {
         return loginUrl;
      }

      private final String loginUrl;
   }

   @BeforeEach
   void startServer() throws Exception {
      status = 500;
      body = "";
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
         exchange.getRequestBody().readAllBytes();
         byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=UTF-8");
         exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);

         try(OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
         }
      });
      server.start();
   }

   @AfterEach
   void stopServer() {
      server.stop(0);
   }

   private TestSource source() {
      TestSource ds = new TestSource("http://127.0.0.1:" + server.getAddress().getPort() +
                                     "/services/Soap/u/35.0");
      ds.setName("sf-" + System.nanoTime());
      // the login result is cached per data source definition, keep every test's key unique
      ds.setUser("user-" + System.nanoTime());
      ds.setPassword("SECRETPW");
      ds.setSecurityToken("SECRETTOKEN");
      return ds;
   }

   private String failureChain() {
      RuntimeException e = assertThrows(RuntimeException.class, () -> source().getURL());
      StringBuilder chain = new StringBuilder(e.getMessage());

      for(Throwable c = e.getCause(); c != null; c = c.getCause()) {
         chain.append(" | ").append(c.getMessage());
      }

      return chain.toString();
   }

   // the fault Salesforce really returns for bad credentials: HTTP 500 with a SOAP fault
   private static final String LOGIN_FAULT =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soapenv:Envelope " +
      "xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
      "xmlns:sf=\"urn:fault.partner.soap.sforce.com\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><soapenv:Body><soapenv:Fault>" +
      "<faultcode>sf:INVALID_LOGIN</faultcode><faultstring>INVALID_LOGIN: Invalid username, " +
      "password, security token; or user locked out.</faultstring><detail>" +
      "<sf:LoginFault xsi:type=\"sf:LoginFault\"><sf:exceptionCode>INVALID_LOGIN" +
      "</sf:exceptionCode><sf:exceptionMessage>Invalid username, password, security token; " +
      "or user locked out.</sf:exceptionMessage></sf:LoginFault></detail></soapenv:Fault>" +
      "</soapenv:Body></soapenv:Envelope>";

   @Test
   void failedLoginErrorCarriesTheSalesforceFaultText() {
      body = LOGIN_FAULT;
      String chain = failureChain();

      assertTrue(chain.contains(
         "INVALID_LOGIN: Invalid username, password, security token; or user locked out."), chain);
      assertTrue(chain.contains("500"), chain);
   }

   @Test
   void faultTextReachesTheTopLevelMessage() {
      // the data source status in the UI shows only the outermost message
      body = LOGIN_FAULT;
      RuntimeException e = assertThrows(RuntimeException.class, () -> source().getURL());
      assertTrue(e.getMessage().contains("INVALID_LOGIN"), e.getMessage());
   }

   @Test
   void faultTextIsFoundInTheDetailWhenThereIsNoFaultString() {
      body = "<Envelope xmlns=\"http://schemas.xmlsoap.org/soap/envelope/\"><Body><Fault>" +
         "<detail><exceptionMessage>Locked out</exceptionMessage></detail></Fault></Body>" +
         "</Envelope>";
      assertTrue(failureChain().contains("Locked out"));
   }

   @Test
   void failedLoginWithoutABodyStillReportsTheStatus() {
      body = "";
      String chain = failureChain();
      assertTrue(chain.contains("500"), chain);
   }

   @Test
   void failedLoginWithANonXmlBodyStillReportsTheStatus() {
      body = "<html><body>HTMLBODYMARKER";
      String chain = failureChain();
      assertTrue(chain.contains("500"), chain);
      assertFalse(chain.contains("HTMLBODYMARKER"), chain);
   }

   @Test
   void doctypeInTheFaultBodyIsNotProcessed() {
      body = "<!DOCTYPE x [<!ENTITY e \"EXPANDED\">]><Fault><faultstring>&e;</faultstring></Fault>";
      String chain = failureChain();
      assertFalse(chain.contains("EXPANDED"), chain);
      assertTrue(chain.contains("500"), chain);
   }

   @Test
   void veryLongFaultTextIsBounded() {
      body = "<Fault><faultstring>" + "x".repeat(5000) + "</faultstring></Fault>";
      String chain = failureChain();
      assertTrue(chain.contains("xxx"), chain);
      assertTrue(chain.length() < 1500, "length " + chain.length());
   }

   @Test
   void credentialsAreNotEchoedInTheFailureMessage() {
      body = LOGIN_FAULT;
      String chain = failureChain();
      assertFalse(chain.contains("SECRETPW"), chain);
      assertFalse(chain.contains("SECRETTOKEN"), chain);
   }

   @Test
   void successfulLoginIsUnchanged() {
      status = 200;
      body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soapenv:Envelope " +
         "xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
         "xmlns=\"urn:partner.soap.sforce.com\"><soapenv:Body><loginResponse><result>" +
         "<serverUrl>https://na99.example.com/services/Soap/u/35.0/00Dxx</serverUrl>" +
         "<sessionId>SESSION-1</sessionId></result></loginResponse></soapenv:Body>" +
         "</soapenv:Envelope>";
      assertEquals("https://na99.example.com/services/data/v50.0", source().getURL());
   }

   private HttpServer server;
   private volatile int status;
   private volatile String body;
}
