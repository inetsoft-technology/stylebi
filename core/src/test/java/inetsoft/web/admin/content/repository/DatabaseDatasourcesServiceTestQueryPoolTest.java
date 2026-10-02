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

import com.zaxxer.hikari.HikariConfig;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.jdbc.DefaultConnectionPoolFactory;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.util.Config;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77536: the Test Query of a custom JDBC data source must reach the connection pool. The
 * pool reads the connectionTestQuery pool property of the data source, so the editor saves the
 * test query there instead of in SreeEnv.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DatabaseDatasourcesServiceTestQueryOrgScopeTest.CredentialServiceConfig.class,
                                  DatabaseDatasourcesServiceTestQueryPoolTest.ConfigConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseDatasourcesServiceTestQueryPoolTest {
   private static final String DS = "testQueryPool";
   private static final String LEGACY_KEY = "inetsoft.uql.jdbc.pool." + DS + ".connectionTestQuery";
   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
   private static final String URL = "jdbc:derby:memory:bug77536;create=true";
   private static final String PROPERTY = JDBCUtil.CONNECTION_TEST_QUERY_PROPERTY;

   private DatabaseDatasourcesService service;
   private Principal oldContext;

   @BeforeEach
   void setUp() throws Exception {
      service = mock(DatabaseDatasourcesService.class,
                     withSettings().defaultAnswer(CALLS_REAL_METHODS));
      Field field = DatabaseDatasourcesService.class.getDeclaredField("databaseTypeService");
      field.setAccessible(true);
      field.set(service, new DatabaseTypeService(
         List.of(new CustomDatabaseType(), new AccessDatabaseType())));
      oldContext = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(null);
   }

   @AfterEach
   void tearDown() {
      SreeEnv.remove(LEGACY_KEY);
      ThreadContext.setContextPrincipal(oldContext);
   }

   @Test
   void savedTestQueryIsUsedByThePool() throws Exception {
      DatabaseDefinition definition = edit(customSource(poolProperties()));
      customInfo(definition).setTestQuery("VALUES 2");
      JDBCDataSource saved = save(definition);

      assertEquals("VALUES 2", saved.getPoolProperties().get(PROPERTY));
      assertEquals("VALUES 2", poolConfig(saved).getConnectionTestQuery());
      assertNull(SreeEnv.getProperty(LEGACY_KEY), "the test query is saved in SreeEnv");
   }

   @Test
   void editorShowsTheSavedTestQueryOnlyInTheField() throws Exception {
      JDBCDataSource source = customSource(poolProperties(PROPERTY, "VALUES 2", "maximumPoolSize", "5"));
      DatabaseDefinition definition = edit(source);

      assertEquals("VALUES 2", customInfo(definition).getTestQuery());
      assertFalse(definition.getInfo().getPoolProperties().containsKey(PROPERTY),
                  "the test query is listed in the pool properties table too");
      assertEquals("5", definition.getInfo().getPoolProperties().get("maximumPoolSize"));
      assertEquals("VALUES 2", source.getPoolProperties().get(PROPERTY),
                   "loading the editor changed the pool properties of the data source");

      JDBCDataSource saved = save(definition);
      assertEquals(poolProperties(PROPERTY, "VALUES 2", "maximumPoolSize", "5"),
                   saved.getPoolProperties());
   }

   @Test
   void clearedTestQueryIsRemovedFromThePool() throws Exception {
      DatabaseDefinition definition = edit(customSource(poolProperties(PROPERTY, "VALUES 2")));
      customInfo(definition).setTestQuery("");
      JDBCDataSource saved = save(definition);

      assertFalse(saved.getPoolProperties().containsKey(PROPERTY));
      assertNull(poolConfig(saved).getConnectionTestQuery());
   }

   @Test
   void emptyTestQueryIsNotSaved() throws Exception {
      DatabaseDefinition definition = edit(customSource(poolProperties()));
      customInfo(definition).setTestQuery("");
      JDBCDataSource saved = save(definition);

      assertEquals(poolProperties(), saved.getPoolProperties());
      assertNull(poolConfig(saved).getConnectionTestQuery());
   }

   @Test
   void testQueryFieldWinsOverPoolPropertyRow() throws Exception {
      DatabaseDefinition definition = edit(customSource(poolProperties()));
      definition.getInfo().setPoolProperties(poolProperties(PROPERTY, "VALUES 3"));
      customInfo(definition).setTestQuery("VALUES 2");

      assertEquals("VALUES 2", poolConfig(save(definition)).getConnectionTestQuery());

      // a row that the user adds while the field is empty is kept
      customInfo(definition).setTestQuery("");
      assertEquals("VALUES 3", poolConfig(save(definition)).getConnectionTestQuery());
   }

   @Test
   void legacyTestQueryIsShownAndOnlyUsedAfterSaving() throws Exception {
      SreeEnv.setProperty(LEGACY_KEY, "VALUES 4");
      JDBCDataSource source = customSource(poolProperties());

      assertNull(poolConfig(source).getConnectionTestQuery(),
                 "the legacy SreeEnv test query is used without saving");

      DatabaseDefinition definition = edit(source);
      assertEquals("VALUES 4", customInfo(definition).getTestQuery());
      assertEquals("VALUES 4", poolConfig(save(definition)).getConnectionTestQuery());
   }

   @Test
   void poolPropertyWinsOverLegacyTestQuery() throws Exception {
      SreeEnv.setProperty(LEGACY_KEY, "VALUES 4");
      DatabaseDefinition definition = edit(customSource(poolProperties(PROPERTY, "VALUES 2")));
      assertEquals("VALUES 2", customInfo(definition).getTestQuery());
   }

   @Test
   void accessKeepsTheTestQueryInThePoolPropertiesTable() throws Exception {
      JDBCDataSource source = customSource(poolProperties(PROPERTY, "VALUES 2"));
      source.setCustom(false);
      DatabaseDefinition definition = JDBCUtil.buildDatabaseDefinition(
         source, JDBCUtil.getJDBCDatabaseType(AccessDatabaseType.TYPE));

      assertNull(((AccessDatabaseType.AccessDatabaseInfo) definition.getInfo()).getTestQuery());
      assertEquals("VALUES 2", definition.getInfo().getPoolProperties().get(PROPERTY));
   }

   private static JDBCDataSource customSource(TreeMap<String, String> poolProperties) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(DS);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver(DRIVER);
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      dataSource.setPoolProperties(poolProperties);
      return dataSource;
   }

   // the definition the data source editor loads
   private static DatabaseDefinition edit(JDBCDataSource dataSource) {
      return JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
   }

   private static CustomDatabaseType.CustomDatabaseInfo customInfo(DatabaseDefinition definition) {
      return (CustomDatabaseType.CustomDatabaseInfo) definition.getInfo();
   }

   // the data source that saving the editor stores
   private JDBCDataSource save(DatabaseDefinition definition) throws Exception {
      Method method = DatabaseDatasourcesService.class.getDeclaredMethod(
         "getDatabase", String.class, DatabaseDefinition.class, boolean.class, boolean.class,
         Predicate.class, Principal.class);
      method.setAccessible(true);
      return (JDBCDataSource) method.invoke(service, DS, definition, false, false, null, null);
   }

   private static HikariConfig poolConfig(JDBCDataSource dataSource) throws Exception {
      Method method = DefaultConnectionPoolFactory.class.getDeclaredMethod(
         "createDataSourceConfig", JDBCDataSource.class, boolean.class);
      method.setAccessible(true);
      return (HikariConfig) method.invoke(new DefaultConnectionPoolFactory(), dataSource, true);
   }

   private static TreeMap<String, String> poolProperties(String... keyValues) {
      TreeMap<String, String> properties = new TreeMap<>();

      for(int i = 0; i < keyValues.length; i += 2) {
         properties.put(keyValues[i], keyValues[i + 1]);
      }

      return properties;
   }

   // the connection pool factory reads the default pool properties of the driver from Config
   @Configuration
   static class ConfigConfig {
      @Bean
      public Config config() {
         return mock(Config.class);
      }
   }
}
