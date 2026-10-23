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
package inetsoft.sree.security.db;

import inetsoft.mv.MVManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.web.security.BasicAuthenticationFilter;
import inetsoft.web.security.DefaultAuthorizationFilter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Bug #79268: chain [file provider, provider whose {@code getUser} throws, real database provider
 * on an in-memory Derby database]. A user of the database provider logs in with Basic
 * authentication, and the requests that follow on the session, which run the permission checks
 * of the user, keep working. The mock-provider cases are in {@code ThrowingProviderLoginTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ThrowingProviderDatabaseLoginTest {
   @BeforeEach
   void savePrincipal() {
      threadPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() {
      if(sutil != null) {
         sutil.close();
         sutil = null;
      }

      ThreadContext.setContextPrincipal(threadPrincipal);
      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");
      SreeEnv.remove("security.cache");
   }

   @Test
   void multiTenantDatabaseUserLogsInAndKeepsWorking() throws Exception {
      loginAndSecondRequest(true, "throwdb79268_m");
   }

   @Test
   void singleTenantDatabaseUserLogsInAndKeepsWorking() throws Exception {
      loginAndSecondRequest(false, "throwdb79268_s");
   }

   private void loginAndSecondRequest(boolean multiTenant, String dbName) throws Exception {
      String url = "jdbc:derby:memory:" + dbName + ";create=true";
      createTables(url);

      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(multiTenant);
      sutil.when(() -> SUtil.setAdditionalDatasource(any())).thenAnswer(invocation -> null);
      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("security.users.multiTenant", String.valueOf(multiTenant));
      SreeEnv.setProperty("security.cache", "false");
      SreeEnv.save();

      FileAuthenticationProvider file = new FileAuthenticationProvider();
      file.setProviderName("Primary");
      DatabaseAuthenticationProvider db = createDatabaseProvider(dbName, url, multiTenant);
      AuthenticationChain authc = new AuthenticationChain();
      authc.setProviders(List.of(file, db));
      authc.saveConfiguration();
      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();

      SecurityEngine engine = SecurityEngine.getSecurity();
      engine.init();
      AuthenticationChain chain = (AuthenticationChain)
         engine.getSecurityProvider().getAuthenticationProvider();
      List<AuthenticationProvider> providers = chain.getProviders();
      DatabaseAuthenticationProvider realDb = (DatabaseAuthenticationProvider) providers.get(1);
      realDb.setMultiTenantSupplier(() -> multiTenant);
      realDb.setDriverAvailable(DRIVER::equals);
      realDb.setDriverSupplier(c -> new org.apache.derby.jdbc.EmbeddedDriver());
      realDb.resetConnection();

      try {
         ReflectionTestUtils.invokeMethod(
            chain, "setProviderList", List.of(providers.get(0), createThrowingProvider(), realDb));
         MockMvc mvc = createMockMvc(engine);
         MockHttpSession session = new MockHttpSession();
         String credentials = Base64.getEncoder()
            .encodeToString("prv-basic:pw".getBytes(StandardCharsets.UTF_8));

         assertEquals(200, mvc.perform(get("/api/portal/tree")
               .header("Authorization", "Basic " + credentials).session(session))
            .andReturn().getResponse().getStatus(), "login");
         // the session cookie only: the filters check the permissions of the principal
         assertEquals(200, mvc.perform(get("/api/portal/tree").session(session))
            .andReturn().getResponse().getStatus(), "request on the session");
         assertEquals(200, mvc.perform(get("/api/portal/tree")
               .header("Authorization", "Basic " + credentials).session(session))
            .andReturn().getResponse().getStatus(), "second login on the session");
      }
      finally {
         ReflectionTestUtils.invokeMethod(chain, "setProviderList", providers);
         realDb.tearDown();

         try {
            DriverManager.getConnection("jdbc:derby:memory:" + dbName + ";drop=true");
         }
         catch(SQLException expected) {
            // drop always throws
         }
      }
   }

   private MockMvc createMockMvc(SecurityEngine engine) {
      AuthenticationService service = new AuthenticationService(
         engine, mock(MVManager.class), mock(DataSourceRegistry.class),
         mock(XSessionService.class), mock(LocaleService.class),
         mock(SessionLicenseServiceProvider.class), mock(ApplicationEventPublisher.class),
         mock(IndexedStorage.class));
      SessionLicenseServiceProvider license = mock(SessionLicenseServiceProvider.class);
      return MockMvcBuilders.standaloneSetup(new Ok())
         .addFilters(new BasicAuthenticationFilter(license, service),
                     new DefaultAuthorizationFilter(license, service))
         .build();
   }

   private DatabaseAuthenticationProvider createDatabaseProvider(
      String dbName, String url, boolean multiTenant)
   {
      DatabaseAuthenticationProvider db = new DatabaseAuthenticationProvider();
      db.setProviderName("DB79268_" + dbName);
      db.setDriver(DRIVER);
      db.setUrl(url);
      db.setRequiresLogin(false);
      db.setHashAlgorithm("None");

      if(multiTenant) {
         db.setUserQuery("SELECT NAME, PW FROM U WHERE ORG = ? AND NAME = ?");
         db.setUserListQuery("SELECT NAME, ORG FROM U");
         db.setUserRolesQuery("SELECT ROLE_NAME FROM UR WHERE ORG = ? AND USER_NAME = ?");
         db.setRoleListQuery("SELECT DISTINCT ROLE_NAME, ORG FROM UR");
         db.setUserEmailsQuery("SELECT EMAIL FROM UE WHERE ORG = ? AND USER_NAME = ?");
         db.setGroupListQuery("SELECT GROUP_NAME, ORG FROM G");
         db.setGroupUsersQuery("SELECT USER_NAME, ORG FROM GU WHERE ORG = ? AND GROUP_NAME = ?");
      }
      else {
         db.setUserQuery("SELECT NAME, PW FROM U WHERE NAME = ?");
         db.setUserListQuery("SELECT NAME FROM U");
         db.setUserRolesQuery("SELECT ROLE_NAME FROM UR WHERE USER_NAME = ?");
         db.setRoleListQuery("SELECT DISTINCT ROLE_NAME FROM UR");
         db.setUserEmailsQuery("SELECT EMAIL FROM UE WHERE USER_NAME = ?");
         db.setGroupListQuery("SELECT GROUP_NAME FROM G");
         db.setGroupUsersQuery("SELECT USER_NAME FROM GU WHERE GROUP_NAME = ?");
      }

      return db;
   }

   /**
    * A custom provider like the test plugin's: no users, non-throwing defaults for the other
    * lookups, and a {@code getUser} that always throws.
    */
   private AuthenticationProvider createThrowingProvider() {
      AuthenticationProvider provider = mock(AuthenticationProvider.class);
      when(provider.getProviderName()).thenReturn("throwing");
      when(provider.getUser(any())).thenAnswer(invocation -> {
         throw new RuntimeException("simulated failure looking up " + invocation.getArgument(0));
      });
      when(provider.getOrganizationIDs()).thenReturn(new String[] { HOST_ORG });
      when(provider.getOrganizationNames()).thenReturn(new String[] { "host" });
      when(provider.getOrganization(any())).thenAnswer(invocation ->
         HOST_ORG.equals(invocation.getArgument(0)) ?
            Organization.getDefaultOrganization() : null);
      when(provider.getRoles()).thenReturn(new IdentityID[0]);
      when(provider.getRoles(any())).thenReturn(new IdentityID[0]);
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getUserGroups(any())).thenReturn(new String[0]);
      when(provider.getEmails(any())).thenReturn(new String[0]);
      when(provider.isAuthenticationCaseSensitive()).thenReturn(true);
      return provider;
   }

   private void createTables(String url) throws SQLException {
      try(Connection connection = DriverManager.getConnection(url);
          Statement statement = connection.createStatement())
      {
         String[] sql = {
            "CREATE TABLE U (NAME VARCHAR(50) NOT NULL, PW VARCHAR(50), ORG VARCHAR(50))",
            "CREATE TABLE UR (USER_NAME VARCHAR(50), ROLE_NAME VARCHAR(50), ORG VARCHAR(50))",
            "CREATE TABLE UE (USER_NAME VARCHAR(50), EMAIL VARCHAR(80), ORG VARCHAR(50))",
            "CREATE TABLE G (GROUP_NAME VARCHAR(50), ORG VARCHAR(50))",
            "CREATE TABLE GU (GROUP_NAME VARCHAR(50), USER_NAME VARCHAR(50), ORG VARCHAR(50))",
            "INSERT INTO U VALUES ('prv-basic', 'pw', '" + HOST_ORG + "')",
            "INSERT INTO UR VALUES ('prv-basic', 'r-db', '" + HOST_ORG + "')",
            "INSERT INTO UE VALUES ('prv-basic', 'db@x', '" + HOST_ORG + "')"
         };

         for(String statementSql : sql) {
            statement.execute(statementSql);
         }
      }
   }

   @RestController
   static class Ok {
      @RequestMapping("/**")
      public String handle() {
         return "ok";
      }
   }

   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();
   private MockedStatic<SUtil> sutil;
   private Principal threadPrincipal;
}
