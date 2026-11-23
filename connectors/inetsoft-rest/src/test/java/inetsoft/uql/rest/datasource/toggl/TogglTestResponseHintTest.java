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
package inetsoft.uql.rest.datasource.toggl;

import com.sun.net.httpserver.HttpServer;
import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.rest.RestResponseException;
import inetsoft.uql.rest.json.EndpointJsonRuntime;
import inetsoft.uql.XRepository;
import inetsoft.web.portal.data.DataSourceStatus;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real connection-test path (EndpointJsonRuntime, HttpHandler, then the Status column
 * text of DataSourceStatusService) against a local server that answers like Toggl Track does for
 * a bad key.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class TogglTestResponseHintTest {
   @BeforeEach
   void startServer() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
         exchange.getRequestBody().readAllBytes();
         byte[] body = "Incorrect username and/or password".getBytes(StandardCharsets.UTF_8);
         exchange.sendResponseHeaders(status, body.length);

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

   @Test
   void hintReachesStatusMessageForToggl2Key() {
      String message = statusMessage("toggl_sk_" + "0".repeat(32));
      // DataSourceStatusService lower-cases the connector message
      assertTrue(message.contains(TogglDataSource.TOGGL_2_KEY_HINT.toLowerCase()), message);
      assertFalse(message.contains("0000"), message);
   }

   @Test
   void hintForUnauthorizedToo() {
      status = 401;
      assertTrue(statusMessage("toggl_sk_" + "0".repeat(32)).toLowerCase()
                    .contains("toggl 2.0 api keys"));
   }

   @Test
   void classicTokenKeepsLoginFailedStatus() {
      String message = statusMessage("1971800d4d82861d8f2c1651fea4d212");
      assertFalse(message.toLowerCase().contains("toggl 2.0"), message);
      assertTrue(message.startsWith("Login failed"), message);
   }

   @Test
   void classicTokenExceptionIsUnchangedRestResponseException() {
      TogglDataSource ds = dataSource("1971800d4d82861d8f2c1651fea4d212");
      RestResponseException e = assertThrows(RestResponseException.class, () -> runTest(ds));
      assertTrue(e.getMessage().contains("403"));
      assertTrue(e.getMessage().contains("Incorrect username and/or password"));
   }

   @Test
   void toggl2KeyExceptionKeepsCauseAndOmitsMaskedWords() {
      TogglDataSource ds = dataSource("toggl_sk_" + "0".repeat(32));
      Exception e = assertThrows(Exception.class, () -> runTest(ds));
      assertInstanceOf(RestResponseException.class, e.getCause());
      assertTrue(e.getMessage().startsWith("Test request returned 403: "), e.getMessage());
      assertFalse(e.getMessage().toLowerCase().contains("username"));
      assertFalse(e.getMessage().toLowerCase().contains("password"));
   }

   @Test
   void nonAuthFailureKeepsOriginalException() {
      status = 500;
      TogglDataSource ds = dataSource("toggl_sk_" + "0".repeat(32));
      assertThrows(RestResponseException.class, () -> runTest(ds));
   }

   @Test
   void successfulResponseNotRejectedEvenWithToggl2Prefix() {
      status = 200;
      TogglDataSource ds = dataSource("toggl_sk_" + "0".repeat(32));
      assertDoesNotThrow(() -> runTest(ds));
   }

   private String statusMessage(String token) {
      TogglDataSource ds = dataSource(token);
      XRepository repository = Mockito.mock(XRepository.class);

      try {
         Mockito.doAnswer(inv -> {
            new EndpointJsonRuntime().testDataSource(ds, new VariableTable());
            return null;
         }).when(repository).testDataSource(Mockito.any(), Mockito.any(), Mockito.any());
         DataSourceStatusService service = new DataSourceStatusService(repository, null, null);
         service.updateStatus(ds);
         DataSourceStatus model = service.getStatusModel(ds.getStatus(), "UTC", null);
         assertFalse(model.connected());
         return model.message();
      }
      catch(Exception e) {
         throw new AssertionError(e);
      }
   }

   private TogglDataSource dataSource(String token) {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      TogglDataSource ds = new TogglDataSource() {
         @Override
         public String getURL() {
            return base;
         }
      };
      ds.setApiToken(token);
      return ds;
   }

   private void runTest(TogglDataSource ds) throws Exception {
      new EndpointJsonRuntime().testDataSource(ds, new VariableTable());
   }

   private HttpServer server;
   private int status = 403;
}
