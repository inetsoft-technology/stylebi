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
package inetsoft.uql.service;

import inetsoft.sree.security.*;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.portal.data.ImmutableDataSourceConnectionStatusRequest;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77672: an additional connection's full name is only its own name, but it is stored at
 * parent/name. A save keyed by its full name, as the connectors do after refreshing an OAuth
 * token and as the statuses endpoint does, must update parent/name and must not create or
 * overwrite a top-level data source of that name. JDBC additional connections stand in for the
 * tabular ones, the save layer does not look at the type. The registry and the repository are
 * the real ones.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  XEngineAdditionalConnectionSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XEngineAdditionalConnectionSaveTest {
   private static final String URL = "jdbc:derby:memory:bug77672;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
   }

   // a connector refreshes the token of the additional connection it was given at query time
   // (a clone of the instance read through the parent, so its base is set) and saves it by its
   // full name: the refreshed value is stored under the parent and no top-level copy appears
   @Test
   void saveByBareNameIsStoredUnderTheParent() throws Exception {
      addParent("tokP", "tokAdd", "tokKeep");
      long parentModified = storedLastModified("tokP");
      JDBCDataSource additional = queryTimeCopy("tokP", "tokAdd");
      assertNotNull(additional.getBaseDatasource());

      additional.setDefaultDatabase("refreshed");
      repository.updateDataSource(additional, additional.getFullName());

      registry.clearCache();
      assertNull(registry.getDataSource("tokAdd"), "stray top-level copy");
      assertEquals("refreshed",
                   ((JDBCDataSource) registry.getDataSource("tokP/tokAdd")).getDefaultDatabase());
      assertChildren("tokP", "tokAdd", "tokKeep");
      // the parent is not saved again
      assertEquals(parentModified, storedLastModified("tokP"));
      assertFalse(Arrays.asList(registry.getDataSourceFullNames()).contains("tokAdd"));
   }

   // a save without an old name of a copy that still has its base, made before the cache was
   // cleared, is stored under the parent too
   @Test
   void saveWithoutOldNameIsStoredUnderTheParent() throws Exception {
      addParent("onP", "onAdd");
      JDBCDataSource additional = queryTimeCopy("onP", "onAdd");
      registry.clearCache();

      additional.setDefaultDatabase("refreshed");
      repository.updateDataSource(additional, null);

      registry.clearCache();
      assertNull(registry.getDataSource("onAdd"), "stray top-level copy");
      assertEquals("refreshed",
                   ((JDBCDataSource) registry.getDataSource("onP/onAdd")).getDefaultDatabase());
   }

   // an unrelated top-level data source with the same name as the additional connection is not
   // overwritten
   @Test
   void unrelatedTopLevelDataSourceIsNotOverwritten() throws Exception {
      JDBCDataSource unrelated = source("ovAdd");
      unrelated.setURL("jdbc:derby:memory:unrelated;create=true");
      unrelated.setDefaultDatabase("unrelated");
      registry.setDataSource(unrelated, false);
      addParent("ovP", "ovAdd");
      JDBCDataSource additional = queryTimeCopy("ovP", "ovAdd");

      additional.setDefaultDatabase("refreshed");
      repository.updateDataSource(additional, additional.getFullName());

      registry.clearCache();
      JDBCDataSource topLevel = (JDBCDataSource) registry.getDataSource("ovAdd");
      assertEquals("unrelated", topLevel.getDefaultDatabase());
      assertEquals("jdbc:derby:memory:unrelated;create=true", topLevel.getURL());
      assertEquals("refreshed",
                   ((JDBCDataSource) registry.getDataSource("ovP/ovAdd")).getDefaultDatabase());
   }

   // a save without a change (a per-request save of an authenticator) does not write
   @Test
   void unchangedSaveDoesNotWrite() throws Exception {
      addParent("ucP", "ucAdd");
      long modified = storedLastModified("ucP/ucAdd");
      JDBCDataSource additional = queryTimeCopy("ucP", "ucAdd");

      repository.updateDataSource(additional, additional.getFullName());

      assertEquals(modified, storedLastModified("ucP/ucAdd"));
      assertNull(registry.getDataSource("ucAdd"));
   }

   // an additional connection removed since the copy was made is not brought back, and no
   // top-level copy is created
   @Test
   void removedAdditionalConnectionIsNotSaved() throws Exception {
      addParent("rmP", "rmAdd", "rmKeep");
      JDBCDataSource additional = queryTimeCopy("rmP", "rmAdd");
      ((JDBCDataSource) registry.getDataSource("rmP")).removeDatasource("rmAdd");

      additional.setDefaultDatabase("refreshed");
      repository.updateDataSource(additional, additional.getFullName());

      registry.clearCache();
      assertNull(registry.getDataSource("rmAdd"));
      assertChildren("rmP", "rmKeep");
   }

   // POST /api/data/datasources/statuses with the path of an additional connection, read after
   // the cache was cleared so that its base is null, stores the status under the parent
   @Test
   void statusOfAnAdditionalConnectionIsStoredUnderTheParent() throws Exception {
      addParent("stP", "stAdd");
      registry.clearCache();
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      DataSourceStatusService service = new DataSourceStatusService(repository, security, registry);

      service.getDataSourceConnectionStatuses(
         ImmutableDataSourceConnectionStatusRequest.builder()
            .addPaths("stP/stAdd")
            .updateStatus(true)
            .timeZone("UTC")
            .build(),
         principal());

      registry.clearCache();
      assertNull(registry.getDataSource("stAdd"), "stray top-level copy");
      assertNotNull(registry.getDataSource("stP/stAdd").getStatus());
      assertChildren("stP", "stAdd");
   }

   // a top-level data source is still saved at its name
   @Test
   void topLevelDataSourceIsSavedAsBefore() throws Exception {
      registry.setDataSource(source("tlDs"), false);
      JDBCDataSource ds = (JDBCDataSource) registry.getDataSource("tlDs").clone();

      ds.setDefaultDatabase("changed");
      repository.updateDataSource(ds, ds.getFullName());

      registry.clearCache();
      assertEquals("changed", ((JDBCDataSource) registry.getDataSource("tlDs")).getDefaultDatabase());
   }

   // the parent is in a data source folder: the save is stored at folder/parent/name, and no copy
   // appears at the top level or in the folder
   @Test
   void saveOfAnAdditionalConnectionOfAParentInAFolderIsStoredUnderTheParent() throws Exception {
      registry.setDataSourceFolder(new DataSourceFolder("fldF", LocalDateTime.now(), null));
      addParent("fldF/fldP", "fldAdd");
      JDBCDataSource additional = queryTimeCopy("fldF/fldP", "fldAdd");
      assertEquals("fldAdd", additional.getFullName());

      additional.setDefaultDatabase("refreshed");
      repository.updateDataSource(additional, additional.getFullName());

      registry.clearCache();
      assertNull(registry.getDataSource("fldAdd"), "stray top-level copy");
      assertNull(registry.getDataSource("fldF/fldAdd"), "stray copy in the folder");
      assertEquals("refreshed",
                   ((JDBCDataSource) registry.getDataSource("fldF/fldP/fldAdd")).getDefaultDatabase());
      assertChildren("fldF/fldP", "fldAdd");
   }

   // a data source in a folder is still saved at its path
   @Test
   void dataSourceInAFolderIsSavedAsBefore() throws Exception {
      registry.setDataSourceFolder(new DataSourceFolder("fsF", LocalDateTime.now(), null));
      registry.setDataSource(source("fsF/fsDs"), false);
      JDBCDataSource ds = (JDBCDataSource) registry.getDataSource("fsF/fsDs").clone();

      ds.setDefaultDatabase("changed");
      repository.updateDataSource(ds, ds.getFullName());

      registry.clearCache();
      assertEquals("changed",
                   ((JDBCDataSource) registry.getDataSource("fsF/fsDs")).getDefaultDatabase());
      assertNull(registry.getDataSource("fsDs"));
   }

   // a token refresh writes once; the following per-request saves of an authenticator, of the
   // same instance or of a new copy read through the parent, compare equal to parent/name and
   // do not write again
   @Test
   void identicalSaveAfterARefreshDoesNotWriteAgain() throws Exception {
      addParent("idP", "idAdd");
      long original = storedLastModified("idP/idAdd");
      JDBCDataSource refreshed = queryTimeCopy("idP", "idAdd");
      Thread.sleep(5);

      refreshed.setDefaultDatabase("refreshed");
      repository.updateDataSource(refreshed, refreshed.getFullName());
      long written = storedLastModified("idP/idAdd");
      assertNotEquals(original, written, "the refresh is written");

      Thread.sleep(5);
      repository.updateDataSource(refreshed, refreshed.getFullName());
      assertEquals(written, storedLastModified("idP/idAdd"), "same instance saved again");

      JDBCDataSource next = queryTimeCopy("idP", "idAdd");
      assertEquals("refreshed", next.getDefaultDatabase());
      repository.updateDataSource(next, next.getFullName());
      assertEquals(written, storedLastModified("idP/idAdd"), "new copy saved unchanged");

      registry.clearCache();
      assertNull(registry.getDataSource("idAdd"), "stray top-level copy");
   }

   // a rename of a top-level data source is not affected
   @Test
   void renameOfTopLevelDataSourceIsUnchanged() throws Exception {
      registry.setDataSource(source("rnOld"), false);
      JDBCDataSource ds = (JDBCDataSource) registry.getDataSource("rnOld").clone();

      ds.setName("rnNew");
      repository.updateDataSource(ds, "rnOld");

      registry.clearCache();
      assertNull(registry.getDataSource("rnOld"));
      assertNotNull(registry.getDataSource("rnNew"));
   }

   private JDBCDataSource queryTimeCopy(String parent, String name) {
      JDBCDataSource ds = (JDBCDataSource) registry.getDataSource(parent);
      return (JDBCDataSource) ds.getDataSource(name).clone();
   }

   private long storedLastModified(String path) {
      registry.clearCache();
      return registry.getDataSource(path).getLastModified();
   }

   // the names of the additional connections start with a prefix of their own, since every test
   // of the class saves to the same storage
   private void addParent(String path, String... additionals) {
      registry.setDataSource(source(path), false);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(path);

      for(String name : additionals) {
         JDBCDataSource additional = source(name);
         additional.setLastModified(System.currentTimeMillis());
         parent.addDatasource(additional);
      }
   }

   private void assertChildren(String parentPath, String... names) {
      registry.clearCache();
      XDataSource parent = registry.getDataSource(parentPath);
      assertNotNull(parent, parentPath);
      String[] expected = names.clone();
      Arrays.sort(expected);
      String[] children = ((JDBCDataSource) parent).getDataSourceNames();
      Arrays.sort(children);
      assertArrayEquals(expected, children, "additional connections of " + parentPath);
   }

   private static Principal principal() {
      return new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                             new IdentityID[0], new String[0],
                             Organization.getDefaultOrganizationID(),
                             Tool.getSecureRandom().nextLong());
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }

   @Configuration
   static class Beans {
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      // the constructors of these beans are package private
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      // loads the embedded Derby driver of the test sources
      @Bean
      @Primary
      public Drivers testDrivers() throws Exception {
         Drivers drivers = mock(Drivers.class);
         when(drivers.getDriverClass(anyString()))
            .thenAnswer(inv -> Class.forName(inv.<String>getArgument(0)));
         return drivers;
      }
   }
}
