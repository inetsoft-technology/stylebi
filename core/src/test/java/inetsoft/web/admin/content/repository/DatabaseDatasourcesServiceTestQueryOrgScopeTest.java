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
package inetsoft.web.admin.content.repository;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77506: the connection test query of a JDBC data source is kept in the SreeEnv key
 * inetsoft.uql.jdbc.pool.&lt;fullName&gt;.connectionTestQuery. Data source names are only
 * unique within an organization, so the key must be organization scoped. Legacy global values
 * belong to the host organization only.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DatabaseDatasourcesServiceTestQueryOrgScopeTest.CredentialServiceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseDatasourcesServiceTestQueryOrgScopeTest {
   private static final String DS = "sharedName";
   private static final String KEY = "inetsoft.uql.jdbc.pool." + DS + ".connectionTestQuery";
   private static final String HOST = Organization.getDefaultOrganizationID();

   private DatabaseDatasourcesService service;
   private Principal oldContext;

   @BeforeEach
   void setUp() {
      service = mock(DatabaseDatasourcesService.class,
                     withSettings().defaultAnswer(CALLS_REAL_METHODS));
      oldContext = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(null);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      SreeEnv.remove(KEY);

      for(String org : new String[] { "orga", "orgb", HOST }) {
         SreeEnv.remove(orgKey(org));
      }

      ThreadContext.setContextPrincipal(oldContext);
   }

   @Test
   void testQuerySavedInOrgAIsNotVisibleInOrgB() throws Exception {
      asOrg("orga");
      saveTestQuery(DS, DS, "SELECT 1 FROM ORG_A_ONLY");

      asOrg("orgb");
      assertNull(SreeEnv.getProperty(KEY),
                 "org B's same-named data source reads org A's connection test query");
      assertNull(editorTestQuery(), "org B's editor shows org A's connection test query");

      asOrg("orga");
      assertEquals("SELECT 1 FROM ORG_A_ONLY", editorTestQuery());
   }

   @Test
   void testQuerySavedInOrgBDoesNotOverwriteOrgA() throws Exception {
      asOrg("orga");
      saveTestQuery(DS, DS, "SELECT 1 FROM ORG_A_ONLY");

      asOrg("orgb");
      saveTestQuery(DS, DS, "");   // org B clears the field on its own data source

      asOrg("orga");
      assertEquals("SELECT 1 FROM ORG_A_ONLY", SreeEnv.getProperty(KEY),
                   "org B's save removed org A's connection test query");
      assertEquals("SELECT 1 FROM ORG_A_ONLY", editorTestQuery());
   }

   @Test
   void nonHostOrgNeverReadsLegacyGlobalKey() throws Exception {
      SreeEnv.setProperty(KEY, "SELECT LEGACY");

      asOrg("orgb");
      assertNull(editorTestQuery(), "a non-host org reads the legacy global test query");

      saveTestQuery(DS, DS, "");
      assertEquals("SELECT LEGACY", globalValue(), "a non-host org removed the global key");
   }

   @Test
   void hostOrgFallsBackToLegacyGlobalKey() throws Exception {
      SreeEnv.setProperty(KEY, "SELECT LEGACY");

      asOrg(HOST);
      assertEquals("SELECT LEGACY", editorTestQuery());
   }

   @Test
   void hostOrgSaveClearsLegacyGlobalKey() throws Exception {
      SreeEnv.setProperty(KEY, "SELECT LEGACY");

      asOrg(HOST);
      saveTestQuery(DS, DS, "SELECT NEW");
      assertNull(globalValue(), "the host org save left the legacy global key");
      assertEquals("SELECT NEW", SreeEnv.getProperty(orgKey(HOST), false, false));
      assertEquals("SELECT NEW", editorTestQuery());

      saveTestQuery(DS, DS, "");
      assertNull(globalValue());
      assertNull(SreeEnv.getProperty(orgKey(HOST), false, false));
      assertNull(editorTestQuery(), "the cleared test query is shown again");
   }

   @Test
   void hostOrgIsMatchedIgnoringCase() throws Exception {
      // the enterprise organization manager keeps the case of the organization ID
      OrganizationManager manager = mock(OrganizationManager.class);
      when(manager.getCurrentOrgID()).thenReturn(HOST.toUpperCase());
      SreeEnv.setProperty(KEY, "SELECT LEGACY");
      asOrg(HOST);

      try(MockedStatic<OrganizationManager> mocked =
             mockStatic(OrganizationManager.class, CALLS_REAL_METHODS))
      {
         mocked.when(OrganizationManager::getInstance).thenReturn(manager);
         assertEquals("SELECT LEGACY", editorTestQuery());

         saveTestQuery(DS, DS, "SELECT NEW");
         assertNull(globalValue(), "the host org save left the legacy global key");
         assertEquals("SELECT NEW", editorTestQuery());
      }

      assertEquals("SELECT NEW", SreeEnv.getProperty(orgKey(HOST), false, false));
   }

   @Test
   void mixedCaseOrgIdRoundTrip() throws Exception {
      asOrg("OrgB");
      saveTestQuery(DS, DS, "SELECT B");
      assertEquals("SELECT B", editorTestQuery());
      assertEquals("SELECT B", SreeEnv.getProperty(orgKey("orgb"), false, false));
      assertNull(globalValue());

      asOrg("orgb");
      assertEquals("SELECT B", editorTestQuery());
   }

   @Test
   void siteAdminSwitchedIntoOrgUsesTheSwitchedOrgKey() throws Exception {
      SreeEnv.setProperty(KEY, "SELECT LEGACY");
      asOrg("orga");
      saveTestQuery(DS, DS, "SELECT A");

      asSwitchedSiteAdmin("orgb");
      assertNull(editorTestQuery(), "the switched site admin reads another org's test query");
      saveTestQuery(DS, DS, "SELECT B");
      assertEquals("SELECT B", SreeEnv.getProperty(orgKey("orgb"), false, false));
      assertNull(SreeEnv.getProperty(orgKey(HOST), false, false));
      assertEquals("SELECT B", editorTestQuery());

      asOrg("orgb");
      assertEquals("SELECT B", editorTestQuery());

      asSwitchedSiteAdmin("orgb");
      deleteDataSource();
      assertNull(SreeEnv.getProperty(orgKey("orgb"), false, false));
      assertEquals("SELECT LEGACY", globalValue(), "the switched site admin removed the global key");

      asOrg("orga");
      assertEquals("SELECT A", editorTestQuery());
   }

   @Test
   void renameMovesTheOrgKey() throws Exception {
      String renamedKey = "inetsoft.org.orga.inetsoft.uql.jdbc.pool.renamed.connectionTestQuery";

      try {
         asOrg("orga");
         saveTestQuery(DS, DS, "SELECT A");
         saveTestQuery(DS, "renamed", "SELECT A");
         assertNull(SreeEnv.getProperty(orgKey("orga"), false, false));
         assertEquals("SELECT A", SreeEnv.getProperty(renamedKey, false, false));
      }
      finally {
         SreeEnv.remove(renamedKey);
      }
   }

   @Test
   void singleTenantWithoutPrincipalUsesGlobalKey() throws Exception {
      saveTestQuery(DS, DS, "SELECT GLOBAL");
      assertEquals("SELECT GLOBAL", globalValue());
      assertEquals("SELECT GLOBAL", editorTestQuery());

      saveTestQuery(DS, DS, "");
      assertNull(globalValue());
      assertNull(editorTestQuery());
   }

   @Test
   void deleteRemovesOnlyTheCurrentOrgKey() throws Exception {
      asOrg("orga");
      saveTestQuery(DS, DS, "SELECT A");
      asOrg("orgb");
      saveTestQuery(DS, DS, "SELECT B");

      deleteDataSource();

      assertNull(editorTestQuery());
      asOrg("orga");
      assertEquals("SELECT A", editorTestQuery(), "org B's delete removed org A's test query");
   }

   @Test
   void hostOrgDeleteRemovesLegacyGlobalKey() throws Exception {
      SreeEnv.setProperty(KEY, "SELECT LEGACY");
      asOrg("orgb");
      deleteDataSource();
      assertEquals("SELECT LEGACY", globalValue(), "a non-host org delete removed the global key");

      asOrg(HOST);
      deleteDataSource();
      assertNull(globalValue());
      assertNull(editorTestQuery());
   }

   private static String orgKey(String org) {
      return "inetsoft.org." + org.toLowerCase() + "." + KEY;
   }

   private static String globalValue() {
      return SreeEnv.getProperty(KEY, false, false);
   }

   private void asOrg(String org) {
      ThreadContext.setContextPrincipal(
         new SRPrincipal(new IdentityID("admin", org), new IdentityID[0], new String[0], org,
                         Tool.getSecureRandom().nextLong()));
   }

   // a host organization site admin that switched into another organization in the EM
   private void asSwitchedSiteAdmin(String org) {
      SRPrincipal principal = new SRPrincipal(
         new IdentityID("admin", HOST), new IdentityID[0], new String[0], HOST,
         Tool.getSecureRandom().nextLong());
      principal.setProperty("curr_org_id", org);
      ThreadContext.setContextPrincipal(principal);
   }

   private void saveTestQuery(String oldSource, String newSource, String query) throws Exception {
      Method method = DatabaseDatasourcesService.class.getDeclaredMethod(
         "saveTestQuery", String.class, String.class, String.class);
      method.setAccessible(true);
      method.invoke(service, oldSource, newSource, query);
   }

   // the test query shown in the data source editor
   private static String editorTestQuery() {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(DS);
      dataSource.setCustom(true);
      dataSource.setDriver("org.example.Driver");
      dataSource.setURL("jdbc:example://localhost/db");
      DatabaseDefinition definition = JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
      return ((CustomDatabaseType.CustomDatabaseInfo) definition.getInfo()).getTestQuery();
   }

   private static void deleteDataSource() throws Exception {
      DatasourcesService datasourcesService = new DatasourcesService(
         mock(XRepository.class), mock(SecurityEngine.class), mock(DataSourceStatusService.class),
         mock(DataSourceRegistry.class), mock(Config.class));
      datasourcesService.deleteDataSource(DS, DS, false);
   }

   // JDBCDataSource's constructor needs the CredentialService bean, whose constructor is
   // package private
   @Configuration
   static class CredentialServiceConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }
}
