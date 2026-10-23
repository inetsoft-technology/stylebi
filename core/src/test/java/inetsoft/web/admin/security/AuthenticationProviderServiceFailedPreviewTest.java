/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.security;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.Drivers;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79235: the EM preview of a Database provider must report a failed query (or a failed
 * connection) as an error, and keep returning an empty list for a valid query with no rows.
 * Axis: every preview getter x (bad query, no rows, unreachable database).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class,
               AuthenticationProviderServiceFailedPreviewTest.DriversConfig.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AuthenticationProviderServiceFailedPreviewTest {
   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
   private static int counter = 0;

   @Configuration
   static class DriversConfig {
      @Bean
      public Drivers drivers() {
         Drivers drivers = mock(Drivers.class);
         when(drivers.getDriverClassLoader(any(), any()))
            .thenReturn(AuthenticationProviderServiceFailedPreviewTest.class.getClassLoader());
         return drivers;
      }
   }

   private String url;
   private AuthenticationProviderService service;

   @BeforeEach
   void setUp() throws Exception {
      url = "jdbc:derby:memory:preview79235_" + (counter++);

      try(Connection c = DriverManager.getConnection(url + ";create=true");
          Statement s = c.createStatement())
      {
         s.execute("CREATE TABLE U (NAME VARCHAR(50) NOT NULL)");
         s.execute("CREATE TABLE UR (USER_NAME VARCHAR(50), ROLE_NAME VARCHAR(50))");
         s.execute("CREATE TABLE G (GROUP_NAME VARCHAR(50))");
         s.execute("INSERT INTO U VALUES ('u1')");
      }

      service = new AuthenticationProviderService(mock(SecurityEngine.class), null, null, null);
   }

   @AfterEach
   void tearDown() {
      try {
         DriverManager.getConnection(url + ";drop=true");
      }
      catch(SQLException expected) {
         // drop always throws
      }
   }

   @Test
   void failedQueryIsAnErrorForEveryPreviewGetter() {
      AuthenticationProviderModel model = model(url, "SELECT NO_SUCH_COLUMN FROM U");
      IdentityID user = new IdentityID("u1", "host-org");

      assertFails(() -> service.getUsers(model));
      assertFails(() -> service.getGroups(model));
      assertFails(() -> service.getRoles(model));
      assertFails(() -> service.getAllUserRoles(model));
      assertFails(() -> service.getOrganizations(model));
      assertFails(() -> service.getGroupUsers(model, user));
      assertFails(() -> service.getOrganizationMembers(model, "host-org"));
      assertFails(() -> service.getUserEmails(model, user));
   }

   @Test
   void validQueryWithNoRowsIsNotAnError() throws Exception {
      AuthenticationProviderModel model = model(url, "SELECT NAME FROM U WHERE 1 = 0");

      assertEquals(0, service.getUsers(model).ids().length);
      assertEquals(0, service.getGroups(model).ids().length);
      assertEquals(0, service.getRoles(model).ids().length);
      assertEquals(0, service.getAllUserRoles(model).list().size());
   }

   @Test
   void validQueryReturnsRows() throws Exception {
      AuthenticationProviderModel model = model(url, "SELECT NAME FROM U");
      assertEquals(1, service.getUsers(model).ids().length);
   }

   @Test
   void unreachableDatabaseIsAnError() {
      AuthenticationProviderModel model =
         model("jdbc:derby:memory:missing79235_" + counter, "SELECT NAME FROM U");
      assertFails(() -> service.getUsers(model));
      assertFails(() -> service.getRoles(model));
   }

   @Test
   void failureDoesNotLeaveFlagBehindForNextCall() throws Exception {
      // the same service instance serves a failing and then a healthy preview
      assertFails(() -> service.getUsers(model(url, "SELECT NO_SUCH_COLUMN FROM U")));
      assertEquals(1, service.getUsers(model(url, "SELECT NAME FROM U")).ids().length);
   }

   private void assertFails(ThrowingCall call) {
      assertThrows(MessageException.class, call::run);
   }

   private AuthenticationProviderModel model(String url, String listQuery) {
      DatabaseAuthenticationProviderModel db = DatabaseAuthenticationProviderModel.builder()
         .driver(DRIVER)
         .url(url)
         .requiresLogin(false)
         .hashAlgorithm("None")
         .userQuery("SELECT NAME FROM U WHERE NAME = ?")
         .userListQuery(listQuery)
         .groupListQuery(listQuery)
         .roleListQuery(listQuery)
         .userRoleListQuery(listQuery)
         .organizationListQuery(listQuery)
         .organizationMembersQuery(listQuery)
         .groupUsersQuery(listQuery)
         .userEmailsQuery(listQuery)
         .build();

      return AuthenticationProviderModel.builder()
         .providerName("p79235")
         .providerType(SecurityProviderType.DATABASE)
         .dbProviderModel(db)
         .build();
   }

   @FunctionalInterface
   private interface ThrowingCall {
      void run() throws Exception;
   }
}
