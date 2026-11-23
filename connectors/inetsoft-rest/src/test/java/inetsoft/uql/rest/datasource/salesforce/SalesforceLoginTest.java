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
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Legacy username/password login of the Salesforce connectors. The expected request and response
 * formats come from the Salesforce SOAP API login() call: partner namespace
 * urn:partner.soap.sforce.com, a SOAPAction header, username and password elements (password
 * followed by the security token), and a result with serverUrl and sessionId.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SalesforceLoginTest.CredentialConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SalesforceLoginTest {
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
      hits.set(0);
      lastBody = null;
      lastSoapAction = null;
      status = 200;
      body = loginResponse("https://na99.example.com/services/Soap/u/35.0/00Dxx0000001gPL", "SESSION-1");
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
         hits.incrementAndGet();
         lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
         lastSoapAction = exchange.getRequestHeaders().getFirst("SOAPAction");
         byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=UTF-8");
         exchange.sendResponseHeaders(status, bytes.length);

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

   private static String loginResponse(String serverUrl, String sessionId) {
      return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
         "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
         "xmlns=\"urn:partner.soap.sforce.com\"><soapenv:Body><loginResponse><result>" +
         "<serverUrl>" + serverUrl + "</serverUrl><sessionId>" + sessionId + "</sessionId>" +
         "</result></loginResponse></soapenv:Body></soapenv:Envelope>";
   }

   private TestSource source(String user, String password, String token) {
      TestSource ds = new TestSource("http://127.0.0.1:" + server.getAddress().getPort() +
                                     "/services/Soap/u/35.0");
      ds.setName("sf-" + System.nanoTime());
      // the login result is cached per data source definition, keep every test's key unique
      ds.setUser(user + "-" + System.nanoTime());
      ds.setPassword(password);
      ds.setSecurityToken(token);
      return ds;
   }

   private Document parseRequest() throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      return factory.newDocumentBuilder().parse(
         new ByteArrayInputStream(lastBody.getBytes(StandardCharsets.UTF_8)));
   }

   private static String text(Document doc, String ns, String name) {
      return doc.getElementsByTagNameNS(ns, name).item(0).getTextContent();
   }

   private static final String PARTNER = "urn:partner.soap.sforce.com";

   @Test
   void loginRequestIsASoapLoginWithPasswordFollowedByToken() throws Exception {
      TestSource ds = source("user@example.com", "pw", "TOKEN");
      ds.getURL();

      assertEquals(1, hits.get());
      assertEquals("urn:partner.soap.sforce.com#login", lastSoapAction);
      Document doc = parseRequest();
      assertEquals(1, doc.getElementsByTagNameNS(
         "http://schemas.xmlsoap.org/soap/envelope/", "Envelope").getLength());
      assertEquals(1, doc.getElementsByTagNameNS(PARTNER, "login").getLength());
      assertEquals(ds.getUser(), text(doc, PARTNER, "username"));
      assertEquals("pwTOKEN", text(doc, PARTNER, "password"));
   }

   @Test
   void specialCharactersInCredentialsAreEscapedInTheXml() throws Exception {
      TestSource ds = source("a&b<c>@example.com", "p&w<>'\"é", "T&K<>");
      ds.getURL();

      Document doc = parseRequest();
      assertEquals(ds.getUser(), text(doc, PARTNER, "username"));
      assertEquals("p&w<>'\"éT&K<>", text(doc, PARTNER, "password"));
   }

   @Test
   void sessionIdAndServerUrlFromTheResponseAreUsed() {
      TestSource ds = source("user", "pw", "T");
      assertEquals("https://na99.example.com/services/data/v50.0", ds.getURL());
      HttpParameter[] params = ds.getQueryHttpParameters();
      assertEquals(1, params.length);
      assertEquals("Authorization", params[0].getName());
      assertEquals("Bearer SESSION-1", params[0].getValue());
   }

   @Test
   void serverUrlWithoutServicesPathResolvesToServicesRoot() {
      body = loginResponse("https://na99.example.com/", "SESSION-2");
      TestSource ds = source("user", "pw", "T");
      assertEquals("https://na99.example.com/services/data/v50.0", ds.getURL());
   }

   @Test
   void loginResultIsCachedAcrossCalls() {
      TestSource ds = source("user", "pw", "T");
      ds.getURL();
      ds.getQueryHttpParameters();
      ds.getURL();
      assertEquals(1, hits.get());
   }

   // the SOAP login() fault for bad credentials is an HTTP 500 with a Fault body
   @Test
   void failedLoginIsReportedAndNotCached() {
      status = 500;
      body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soapenv:Envelope " +
         "xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>" +
         "<soapenv:Fault><faultcode>INVALID_LOGIN</faultcode><faultstring>INVALID_LOGIN: " +
         "Invalid username, password, security token; or user locked out.</faultstring>" +
         "</soapenv:Fault></soapenv:Body></soapenv:Envelope>";
      TestSource ds = source("user", "pw", "T");

      RuntimeException e = assertThrows(RuntimeException.class, ds::getURL);
      Throwable cause = e;
      StringBuilder chain = new StringBuilder();

      while(cause != null) {
         chain.append(cause.getMessage()).append(" | ");
         cause = cause.getCause();
      }

      assertTrue(chain.toString().contains("500"), chain.toString());

      // a later successful login must work, the failure is not remembered
      status = 200;
      body = loginResponse("https://na99.example.com/services/Soap/u/35.0/x", "SESSION-3");
      assertEquals("https://na99.example.com/services/data/v50.0", ds.getURL());
      assertEquals(2, hits.get());
   }

   // the fault text tells the user why the login failed (bad password, locked out, ...)
   @Test
   void failedLoginErrorCarriesTheSalesforceFaultText() {
      status = 500;
      body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soapenv:Envelope " +
         "xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>" +
         "<soapenv:Fault><faultcode>INVALID_LOGIN</faultcode><faultstring>INVALID_LOGIN: " +
         "Invalid username, password, security token; or user locked out.</faultstring>" +
         "</soapenv:Fault></soapenv:Body></soapenv:Envelope>";
      RuntimeException e = assertThrows(RuntimeException.class, () -> source("user", "pw", "T").getURL());
      StringBuilder chain = new StringBuilder();

      for(Throwable c = e; c != null; c = c.getCause()) {
         chain.append(c.getMessage()).append(" | ");
      }

      assertTrue(chain.toString().contains("INVALID_LOGIN"), chain.toString());
   }

   @Test
   void nonXmlLoginResponseIsReported() {
      body = "not xml";
      TestSource ds = source("user", "pw", "T");
      assertThrows(RuntimeException.class, ds::getURL);
   }

   // Salesforce: the security token is appended to the password only when the org needs one
   @Test
   @Disabled("Bug #81882: a null security token is sent as the text 'null' in the login password; fixed on epic-test-coverage, enable when that branch reaches main")
   void passwordIsSentUnchangedWhenThereIsNoSecurityToken() throws Exception {
      TestSource ds = source("user", "pw", null);
      ds.getURL();
      assertEquals("pw", text(parseRequest(), PARTNER, "password"));
   }

   private HttpServer server;
   private final AtomicInteger hits = new AtomicInteger();
   private volatile String lastBody;
   private volatile String lastSoapAction;
   private volatile int status = 200;
   private volatile String body;
}
