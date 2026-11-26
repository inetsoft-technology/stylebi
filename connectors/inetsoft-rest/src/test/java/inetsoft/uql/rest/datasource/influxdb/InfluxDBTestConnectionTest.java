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
package inetsoft.uql.rest.datasource.influxdb;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.rest.json.EndpointJsonRuntime;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test Connection must fail for wrong credentials. InfluxDB answers {@code /ping} with 204 whatever
 * credentials are sent, so the test has to probe an endpoint that checks them. {@link FakeInfluxDB}
 * is told which Authorization header {@code /query} accepts and keeps {@code /ping} open.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  InfluxDBTestConnectionTest.CredentialConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Timeout(60)
class InfluxDBTestConnectionTest {
   private static final String BASIC =
      "Basic " + Base64.getEncoder().encodeToString("v1user:v1pass".getBytes(StandardCharsets.UTF_8));

   @BeforeEach
   void startServer() throws Exception {
      server = new FakeInfluxDB(1, 1, 1);
   }

   @AfterEach
   void stopServer() {
      server.close();
   }

   @Test
   void tokenSchemePassesWithTheRightToken() throws Exception {
      server.requireAuthorization("Token right-token");
      test(tokenSource("right-token"));
   }

   @Test
   void tokenSchemeFailsWithAWrongToken() throws Exception {
      server.requireAuthorization("Token right-token");
      assertUnauthorized(tokenSource("wrong-token"));
   }

   @Test
   void basicSchemePassesWithTheRightPassword() throws Exception {
      server.requireAuthorization(BASIC);
      test(basicSource("v1pass"));
   }

   @Test
   void basicSchemeFailsWithAWrongPassword() throws Exception {
      server.requireAuthorization(BASIC);
      assertUnauthorized(basicSource("wrong-pass"));
   }

   @Test
   void aServerWithoutAuthenticationPasses() throws Exception {
      test(tokenSource("anything"));
   }

   private void assertUnauthorized(InfluxDBDataSource source) {
      Exception e = assertThrows(Exception.class, () -> test(source));
      assertTrue(e.getMessage().contains("401"), e.getMessage());
   }

   private static void test(InfluxDBDataSource source) throws Exception {
      new EndpointJsonRuntime().testDataSource(source, new VariableTable());
   }

   private InfluxDBDataSource tokenSource(String token) {
      InfluxDBDataSource source = new InfluxDBDataSource();
      source.setAuthScheme("TOKEN");
      source.setURL(server.url());
      source.setApiToken(token);
      return source;
   }

   private InfluxDBDataSource basicSource(String password) {
      InfluxDBDataSource source = new InfluxDBDataSource();
      source.setAuthScheme("BASIC");
      source.setURL(server.url());
      source.setUser("v1user");
      source.setPassword(password);
      return source;
   }

   @Configuration
   static class CredentialConfig {
      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(CredentialType.class)))
            .thenAnswer(i -> new LocalPasswordAndApiTokenCredential());
         when(service.createCredential(any(CredentialType.class), anyBoolean()))
            .thenAnswer(i -> new LocalPasswordAndApiTokenCredential());
         return service;
      }
   }

   private FakeInfluxDB server;
}
