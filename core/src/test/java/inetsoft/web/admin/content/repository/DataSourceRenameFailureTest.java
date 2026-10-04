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

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.DataSourceRenameException;
import inetsoft.uql.util.Config;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.DataSourceFolderMoveAdditionalConnectionTest.TestTabularDataSource;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77704: a storage write that fails partway through a data source folder move or rename, or
 * a data source move, must not lose a data source, must stop, must reach the caller as an error,
 * and must leave each data source, with its permission, its model and its additional connection,
 * either at its old or at its new path. The dependencies must be renamed for exactly the data
 * sources that were moved.
 *
 * The registry is the real one. Its storage fails the n-th write (once, or that one and every
 * later one), for every n up to the number of writes of the operation, so every write of the
 * operation fails once in turn.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceFolderMoveAdditionalConnectionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceRenameFailureTest {
   private static final String URL = "jdbc:derby:memory:bug77704;create=true";
   // the data sources of a scenario, by their path in the moved folder
   private static final List<String> SOURCES = List.of("A", "B", "G/C", "D");
   private static final Set<String> TABULAR = Set.of("A", "B", "G/C");
   // the type of TestTabularDataSource
   private static final String TABULAR_TYPE = "folderMoveTabular";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private RenameTransformHandler transformHandler;
   @Autowired
   private Config config;
   private final Map<String, Permission> store = new HashMap<>();
   private final List<String> audits = new ArrayList<>();
   private SecurityEngine security;
   private SecurityProvider provider;
   private MockedStatic<SecurityEngine> securityStatic;
   private MockedStatic<Audit> auditStatic;
   private Principal principal;
   private Field storageField;
   private IndexedStorage storage;
   private int writes;
   private int failAt;
   private boolean persistent;
   private String failKey;
   // the permission whose save fails
   private String failPermission;
   // whether a failed permission save throws, which the real provider doesn't do
   private boolean failPermissionThrows;
   // the connector of the tabular data sources isn't installed while an operation runs
   private boolean uninstalled;
   // checks each index that is saved, while set
   private java.util.function.Consumer<AssetFolder> indexCheck;
   private static int seq;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      store.clear();
      audits.clear();
      provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      // a non-virtual engine over the map, which the registry also gets from getSecurity()
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      when(security.isSecurityEnabled()).thenReturn(true);
      when(security.getPermission(any(ResourceType.class), anyString()))
         .thenAnswer(inv -> store.get(key(inv.getArgument(0), inv.getArgument(1))));
      doAnswer(inv -> {
         String key = key(inv.getArgument(0), inv.getArgument(1));

         // FileAuthorizationProvider logs a failed save and doesn't throw it
         if(key.equals(failPermission)) {
            if(failPermissionThrows) {
               throw new IllegalStateException("simulated permission storage failure");
            }

            return null;
         }

         return store.put(key, inv.getArgument(2));
      }).when(security).setPermission(any(ResourceType.class), anyString(), any());
      doAnswer(inv -> store.remove(key(inv.getArgument(0), inv.getArgument(1))))
         .when(security).removePermission(any(ResourceType.class), anyString());
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);
      Audit audit = mock(Audit.class);
      doAnswer(inv -> audits.add(inv.<ActionRecord>getArgument(0).getActionStatus()))
         .when(audit).auditAction(any(), any());
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());

      // the storage of the registry, failing the failAt-th write when armed
      storageField = DataSourceRegistry.class.getDeclaredField("indexedStorage");
      storageField.setAccessible(true);
      storage = (IndexedStorage) storageField.get(registry);
      IndexedStorage failing = mock(IndexedStorage.class, delegatesTo(storage));
      doAnswer(inv -> {
         if(inv.getArgument(0).equals(failKey)) {
            throw new IOException("simulated storage write failure");
         }

         if(failAt > 0) {
            writes++;

            if(writes == failAt || persistent && writes > failAt) {
               throw new IOException("simulated storage write failure");
            }
         }

         if(indexCheck != null && inv.getArgument(1) instanceof AssetFolder root) {
            indexCheck.accept(root);
         }

         storage.putXMLSerializable(inv.getArgument(0), inv.getArgument(1));
         return null;
      }).when(failing).putXMLSerializable(anyString(), any());
      storageField.set(registry, failing);
   }

   @AfterEach
   void tearDown() throws Exception {
      failKey = null;
      failPermission = null;
      failPermissionThrows = false;
      uninstalled = false;
      storageField.set(registry, storage);
      auditStatic.close();
      securityStatic.close();
   }

   // EM Move of a folder into another one
   @Test
   void emFolderMove() throws Exception {
      forEveryWrite(p -> emMove(p + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p + "Dest"),
                    p -> p + "Dest/" + p + "F", true, true);
   }

   // the reported case: the write of a data source under its new path fails, every time. The
   // move used to remove it from its old path first and report success.
   @Test
   void emFolderMoveKeepsADataSourceWhoseWriteFails() throws Exception {
      String p = scenario();
      String nfolder = p + "Dest/" + p + "F";
      failKey = new AssetEntry(inetsoft.uql.asset.AssetRepository.QUERY_SCOPE,
                               AssetEntry.Type.DATA_SOURCE, nfolder + "/B", null).toIdentifier();
      Throwable thrown =
         run(0, false, () -> emMove(p + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p + "Dest"));
      failKey = null;

      DataSourceRenameException error = assertInstanceOf(DataSourceRenameException.class, thrown);
      assertEquals(p + "F/B", error.getFailedPath());
      assertTrue(error.getMessage().contains(p + "F/A"), error.getMessage());
      checkFolder(p, nfolder, thrown, true, "B fails");
      registry.clearCache();
      assertNotNull(registry.getDataSource(p + "F/B"));
      assertNull(registry.getDataSource(nfolder + "/B"));
      assertNotNull(registry.getDataSource(nfolder + "/A"));
      assertTrue(audits.contains(ActionRecord.ACTION_STATUS_FAILURE));
   }

   // Bug #77704 r1: an EM Move of a folder whose tabular data sources can't be loaded, so they are
   // moved with the rest after the loadable ones. A later failure must report them as moved.
   @Test
   void emFolderMoveUninstalled() throws Exception {
      uninstalled = true;
      forEveryWrite(p -> emMove(p + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p + "Dest"),
                    p -> p + "Dest/" + p + "F", true, true);
   }

   // Bug #77704 r1: the same through XEngine.updateDataSourceFolder
   @Test
   void folderRenameUninstalled() throws Exception {
      uninstalled = true;
      forEveryWrite(p -> repository.updateDataSourceFolder(
         new DataSourceFolder(p + "R", LocalDateTime.now(), null), p + "F"), p -> p + "R",
                    false, false);
   }

   // Bug #77704 r1/r2: the permission of a data source can't be saved once the data source has
   // been moved. It must be reported as moved, its dependencies renamed and its permission kept.
   // The real provider only logs the failure, a throwing one is checked too.
   @Test
   void emFolderMoveWithAFailedPermissionSave() throws Exception {
      for(boolean throwing : new boolean[] { false, true }) {
         String p = scenario();
         String nfolder = p + "Dest/" + p + "F";
         String label = throwing ? "the save throws" : "the save is only logged";
         failPermission = key(ResourceType.DATA_SOURCE, nfolder + "/B");
         failPermissionThrows = throwing;
         Throwable thrown =
            run(0, false, () -> emMove(p + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p + "Dest"));
         failPermission = null;

         DataSourceRenameException error =
            assertInstanceOf(DataSourceRenameException.class, thrown, label);
         assertEquals(p + "F/B", error.getFailedPath(), label);
         registry.clearCache();
         assertNull(registry.getDataSource(p + "F/B"), label);
         assertNotNull(registry.getDataSource(nfolder + "/B"), label);
         assertTrue(error.isMoved(p + "F/A"), error.getMessage());
         assertTrue(error.isMoved(p + "F/B"), error.getMessage());
         assertFalse(error.isMoved(p + "F/D"), error.getMessage());
         // not lost, still under the old key
         assertSame(perm(ResourceType.DATA_SOURCE, p + "F/B#grant"),
                    perm(ResourceType.DATA_SOURCE, p + "F/B"), label);
         checkTransforms(Map.of(p + "F/A", true, p + "F/B", true, p + "F/G/C", false), label);
         assertTrue(audits.contains(ActionRecord.ACTION_STATUS_FAILURE), label);

         for(String source : List.of("A", "D", "G/C")) {
            checkSource(p, source, p + "F/" + source, nfolder + "/" + source, label);
         }
      }
   }

   // Bug #77704 r1/r2: the permission of the new folder can't be saved, the new folder is removed
   @Test
   void emFolderMoveWithAFailedFolderPermissionCopy() throws Exception {
      for(boolean throwing : new boolean[] { false, true }) {
         String p = scenario();
         String nfolder = p + "Dest/" + p + "F";
         String label = throwing ? "the copy throws" : "the copy is only logged";
         failPermission = key(ResourceType.DATA_SOURCE_FOLDER, nfolder);
         failPermissionThrows = throwing;
         Throwable thrown =
            run(0, false, () -> emMove(p + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p + "Dest"));
         failPermission = null;

         assertInstanceOf(DataSourceRenameException.class, thrown, label);
         checkFolder(p, nfolder, thrown, false, label);
         assertNull(registry.getDataSourceFolder(nfolder), label);
      }
   }

   // Bug #77704 r2: the "parent::name" permission of an additional connection whose parent can't
   // be loaded is moved with the rest, even when a permission of the rest isn't saved
   @Test
   void emFolderMoveUninstalledWithAFailedPermissionSave() throws Exception {
      String p = scenario();
      String nfolder = p + "Dest/" + p + "F";
      uninstalled = true;
      failPermission = key(ResourceType.DATA_SOURCE, nfolder + "/B");
      // thrown, so the batch is reported as committed on both versions
      failPermissionThrows = true;
      Throwable thrown =
         run(0, false, () -> emMove(p + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p + "Dest"));
      failPermission = null;
      uninstalled = false;

      DataSourceRenameException error = assertInstanceOf(DataSourceRenameException.class, thrown);
      assertTrue(error.isMoved(p + "F/A"), error.getMessage());
      assertTrue(error.isMoved(p + "F/B"), error.getMessage());
      assertSame(perm(ResourceType.DATA_SOURCE, p + "F/B#grant"),
                 perm(ResourceType.DATA_SOURCE, p + "F/B"));
      checkSource(p, "A", p + "F/A", nfolder + "/A", "uninstalled");
   }

   // Bug #77704: no index saved during a move, failed or not, lists a data source whose object
   // isn't stored or lists it at both paths, and a failed move leaves no stored object that the
   // index doesn't list
   @Test
   void emFolderMoveSavesNoIndexWithAMissingOrMovedTwiceDataSource() throws Exception {
      String p0 = scenario();
      assertNull(run(Integer.MAX_VALUE, false,
                     () -> emMove(p0 + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p0 + "Dest")));
      int count = writes;

      // the last one fails no write
      for(int n = 1; n <= count + 1; n++) {
         for(boolean always : new boolean[] { false, true }) {
            String p = scenario();
            String label = "write " + n + (always ? " and later" : "") + " of " + count;
            String ofolder = p + "F/";
            String nfolder = p + "Dest/" + p + "F/";
            List<String> errors = new ArrayList<>();
            // a failed assertion would be taken for a failed write, so it is collected
            indexCheck = root -> {
               Map<String, List<String>> paths = new HashMap<>();

               for(AssetEntry entry : root.getEntries()) {
                  String path = entry.getPath();
                  String source = path.startsWith(ofolder) ?
                     path.substring(ofolder.length()) : path.startsWith(nfolder) ?
                     path.substring(nfolder.length()) : null;

                  if(source == null || !entry.isDataSource()) {
                     continue;
                  }

                  if(!storage.contains(entry.toIdentifier())) {
                     errors.add(path + " is listed but not stored");
                  }

                  paths.computeIfAbsent(source, k -> new ArrayList<>()).add(path);
               }

               paths.values().stream().filter(list -> list.size() > 1)
                  .forEach(list -> errors.add("listed at both paths: " + list));
            };

            try {
               run(n, always,
                   () -> emMove(p + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p + "Dest"));
            }
            finally {
               indexCheck = null;
            }

            assertEquals(List.of(), errors, label);
            registry.clearCache();
            Set<String> listed = new HashSet<>();

            for(AssetEntry entry : registry.getEntries(p)) {
               listed.add(entry.toIdentifier());
            }

            Set<String> unlisted = new TreeSet<>(storage.getKeys(key -> key.contains(p)));
            unlisted.removeAll(listed);
            assertEquals(Set.of(), unlisted, label + ": stored but not listed");
         }
      }
   }

   // Bug #77704: a folder move that failed can be completed. The same move again once the
   // storage works, if it moved nothing and left no new folder, otherwise the data sources still
   // at the old path, moved on their own.
   @Test
   void emFolderMoveCanBeCompletedAfterAFailure() throws Exception {
      String p0 = scenario();
      assertNull(run(Integer.MAX_VALUE, false,
                     () -> emMove(p0 + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p0 + "Dest")));
      int count = writes;

      for(int n = 1; n <= count; n++) {
         for(boolean always : new boolean[] { false, true }) {
            String p = scenario();
            String label = "write " + n + (always ? " and later" : "") + " of " + count;
            String nfolder = p + "Dest/" + p + "F";
            DataSourceRenameException error = assertInstanceOf(
               DataSourceRenameException.class, run(n, always, () -> emMove(
                  p + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p + "Dest")), label);

            if(error.getMovedDataSources().isEmpty() &&
               registry.getDataSourceFolder(nfolder) == null)
            {
               assertNull(run(Integer.MAX_VALUE, false, () -> emMove(
                  p + "F", RepositoryEntry.DATA_SOURCE_FOLDER, p + "Dest")), label + ": again");
               checkFolder(p, nfolder, null, false, label + ": again");
               continue;
            }

            for(String source : List.of("A", "B", "D")) {
               if(registry.getDataSource(p + "F/" + source) != null) {
                  emMove(p + "F/" + source, RepositoryEntry.DATA_SOURCE, nfolder);
               }
            }

            if(registry.getDataSource(p + "F/G/C") != null) {
               if(registry.getDataSourceFolder(nfolder + "/G") != null) {
                  emMove(p + "F/G/C", RepositoryEntry.DATA_SOURCE, nfolder + "/G");
               }
               else {
                  emMove(p + "F/G", RepositoryEntry.DATA_SOURCE_FOLDER, nfolder);
               }
            }

            for(String source : SOURCES) {
               assertTrue(checkSource(p, source, p + "F/" + source, nfolder + "/" + source,
                                      label + ": completed"), label + ": " + source);
            }
         }
      }
   }

   // portal move of a folder into another one
   @Test
   void portalFolderMove() throws Exception {
      forEveryWrite(p -> {
         DataSourceBrowserService service = new DataSourceBrowserService(
            security, objectService(), repository, mock(DataSourceService.class), registry,
            mock(Config.class), transformHandler);
         MoveCommand move = new MoveCommand();
         move.setOldPath(p + "F");
         move.setPath(p + "Dest/" + p + "F");
         move.setName(p + "F");
         move.setType(PortalDataType.DATA_SOURCE_FOLDER.name());
         service.moveDataSource(new MoveCommand[] { move }, principal);
      }, p -> p + "Dest/" + p + "F", true, false);
   }

   // a folder rename, through XEngine.updateDataSourceFolder as the EM and the portal rename it,
   // which moves each data source with XEngine.updateDataSource
   @Test
   void folderRename() throws Exception {
      forEveryWrite(p -> repository.updateDataSourceFolder(
         new DataSourceFolder(p + "R", LocalDateTime.now(), null), p + "F"), p -> p + "R",
                    false, false);
   }

   // EM Move of a data source with a model and of a data source with an additional connection,
   // through XEngine.updateDataSource
   @Test
   void emDataSourceMove() throws Exception {
      for(String name : List.of("D", "A")) {
         String p0 = scenario();
         Throwable thrown = run(Integer.MAX_VALUE, false,
                                () -> emMove(p0 + "F/" + name, RepositoryEntry.DATA_SOURCE,
                                             p0 + "Dest"));
         assertNull(thrown, "the move of " + name + " without a failure");
         int count = writes;
         assertTrue(count > 3, "the writes of the move of " + name + ": " + count);
         checkMove(p0, name, null, "no failure");
         int failed = 0;

         for(int n = 1; n <= count; n++) {
            for(boolean always : new boolean[] { false, true }) {
               String p = scenario();
               String label = name + " write " + n + (always ? " and later" : "") + " of " + count;
               Throwable error = run(n, always, () -> emMove(
                  p + "F/" + name, RepositoryEntry.DATA_SOURCE, p + "Dest"));
               checkMove(p, name, error, label);
               failed += error != null ? 1 : 0;
            }
         }

         assertTrue(failed > 0, "no write failed the move of " + name);
      }
   }

   private void checkMove(String p, String name, Throwable thrown, String label) {
      String oname = p + "F/" + name;
      boolean moved = checkSource(p, name, oname, p + "Dest/" + name, label);
      checkReported(thrown, moved ? Set.of(oname) : Set.of(), Set.of(oname), label);
      checkTransforms(TABULAR.contains(name) ? Map.of(oname, moved) : Map.of(), label);

      if(thrown != null) {
         assertTrue(audits.contains(ActionRecord.ACTION_STATUS_FAILURE), label + ": audit");
      }
   }

   /**
    * Runs an operation on a new scenario for each write of the operation, failing that write
    * once, then that write and every later one.
    *
    * @param operation the operation, given the prefix of the scenario.
    * @param target    the new path of the folder, given the prefix.
    * @param strict    {@code true} if every write is part of the move, so any failure must fail
    *                  it. Otherwise, a data source is saved again after it is moved, which
    *                  logs a failure.
    * @param em        {@code true} if the failure must be audited.
    */
   private void forEveryWrite(Operation operation, Function<String, String> target,
                              boolean strict, boolean em) throws Exception
   {
      // the writes of the operation, without a failure
      String p0 = scenario();
      Throwable thrown = run(Integer.MAX_VALUE, false, () -> operation.run(p0));
      assertNull(thrown, "the operation without a failure");
      int count = writes;
      assertTrue(count > 5, "the writes of the operation: " + count);
      checkFolder(p0, target.apply(p0), null, false, "no failure");
      int failed = 0;

      for(int n = 1; n <= count; n++) {
         for(boolean always : new boolean[] { false, true }) {
            String p = scenario();
            String label = "write " + n + (always ? " and later" : "") + " of " + count;
            Throwable error = run(n, always, () -> operation.run(p));
            failed += error != null ? 1 : 0;

            if(strict) {
               assertNotNull(error, label + " didn't fail the operation");
            }

            checkFolder(p, target.apply(p), error, always, label);

            if(em && error != null) {
               assertTrue(audits.contains(ActionRecord.ACTION_STATUS_FAILURE), label);
            }
         }
      }

      assertTrue(failed > 0, "no write failed the operation");
   }

   // checks a folder move or rename of a scenario
   private void checkFolder(String p, String nfolder, Throwable thrown, boolean always,
                            String label)
   {
      String ofolder = p + "F";
      Set<String> moved = new HashSet<>();
      Set<String> all = new HashSet<>();
      Map<String, Boolean> tabular = new HashMap<>();

      for(String source : SOURCES) {
         String oname = ofolder + "/" + source;
         String nname = nfolder + "/" + source;
         all.add(oname);

         if(checkSource(p, source, oname, nname, label)) {
            moved.add(oname);
         }

         if(TABULAR.contains(source)) {
            tabular.put(oname, moved.contains(oname));
         }
      }

      checkReported(thrown, moved, all, label);
      checkTransforms(tabular, label);

      // a folder exists with the permission of the old one wherever a data source is
      for(String folder : List.of("", "/G")) {
         DataSourceFolder ofound = registry.getDataSourceFolder(ofolder + folder);
         DataSourceFolder nfound = registry.getDataSourceFolder(nfolder + folder);
         assertTrue(ofound != null || nfound != null, label + ": folder " + folder);
         Permission permission = perm(ResourceType.DATA_SOURCE_FOLDER, p + "F" + folder);
         Permission npermission = perm(ResourceType.DATA_SOURCE_FOLDER, nfolder + folder);

         if(ofound != null) {
            assertNotNull(permission, label + ": permission of " + ofolder + folder);
            assertEquals(ofolder + folder, ofound.getFullName(), label);
         }

         if(nfound != null) {
            assertNotNull(npermission, label + ": permission of " + nfolder + folder);
            assertEquals(nfolder + folder, nfound.getFullName(), label);
         }
      }

      // nothing moved and the failure was a single one: the new folders were removed again
      if(thrown != null && moved.isEmpty() && !always) {
         assertNull(registry.getDataSourceFolder(nfolder), label + ": the new folder is left");
         assertNull(perm(ResourceType.DATA_SOURCE_FOLDER, nfolder), label);
      }

      // all moved and no failure: the old folders are gone
      if(thrown == null) {
         assertNull(registry.getDataSourceFolder(ofolder), label);
         assertNull(registry.getDataSourceFolder(ofolder + "/G"), label);
         assertNull(perm(ResourceType.DATA_SOURCE_FOLDER, ofolder), label);
      }
   }

   /**
    * Checks that a data source is at exactly one of its paths, read again from the storage, with
    * its name, its index entry, its folder, its permission, and its model or its additional
    * connection.
    *
    * @return {@code true} if it is at its new path.
    */
   private boolean checkSource(String p, String source, String oname, String nname,
                               String label)
   {
      registry.clearCache();
      XDataSource old = registry.getDataSource(oname);
      XDataSource moved = registry.getDataSource(nname);
      label = label + ": " + source;
      assertTrue(old != null ^ moved != null,
                 label + " old=" + (old != null) + " new=" + (moved != null));
      String path = moved != null ? nname : oname;
      String other = moved != null ? oname : nname;
      XDataSource found = moved != null ? moved : old;
      assertEquals(path, found.getFullName(), label);
      assertTrue(Arrays.stream(registry.getEntries(path))
                    .anyMatch(e -> e.isDataSource() && e.getPath().equals(path)),
                 label + " isn't listed at " + path);
      assertFalse(Arrays.stream(registry.getEntries(other))
                     .anyMatch(e -> e.getPath().equals(other)),
                  label + " is still listed at " + other);
      assertNotNull(registry.getDataSourceFolder(path.substring(0, path.lastIndexOf('/'))),
                    label + " has no folder");
      assertSame(store.get(key(ResourceType.DATA_SOURCE, p + "F/" + source + "#grant")),
                 perm(ResourceType.DATA_SOURCE, path), label + " permission");
      assertNull(perm(ResourceType.DATA_SOURCE, other), label + " permission left");

      if("A".equals(source)) {
         XDataSource additional = registry.getDataSource(path + "/add");
         assertNotNull(additional, label + " additional connection");
         assertEquals("add", additional.getFullName(), label);
         assertNotNull(perm(ResourceType.DATA_SOURCE, path + "::add"), label);
         assertNull(perm(ResourceType.DATA_SOURCE, other + "::add"), label);
         assertNull(registry.getDataSource(other + "/add"), label);
      }

      if("D".equals(source)) {
         XDataModel model = registry.getDataModel(path);
         assertNotNull(model, label + " model");
         assertEquals(path, model.getDataSource(), label);
         assertNull(registry.getDataModel(other), label);
      }

      return moved != null;
   }

   // the error reaches the caller and names exactly the data sources that were moved
   private void checkReported(Throwable thrown, Set<String> moved, Set<String> all,
                              String label)
   {
      if(thrown == null) {
         assertEquals(all, moved, label + ": not all were moved, with no error");
         return;
      }

      DataSourceRenameException error =
         assertInstanceOf(DataSourceRenameException.class, thrown, label);
      assertEquals(moved, error.getMovedDataSources().keySet(), label + ": the reported moves");
      assertFalse(error.getMessage().isEmpty(), label);
   }

   // the dependencies are renamed for exactly the data sources that were moved
   private void checkTransforms(Map<String, Boolean> sources, String label) {
      ArgumentCaptor<RenameDependencyInfo> captor =
         ArgumentCaptor.forClass(RenameDependencyInfo.class);
      verify(transformHandler, atLeast(0)).addTransformTask(captor.capture());
      Set<String> renamed = new HashSet<>();

      for(RenameDependencyInfo info : captor.getAllValues()) {
         if(info != null && info.getRenameInfos() != null) {
            info.getRenameInfos().forEach(rinfo -> renamed.add(rinfo.getOldName()));
         }
      }

      sources.forEach((source, moved) ->
         assertEquals(moved, renamed.contains(source), label + ": dependencies of " + source));
   }

   // runs an operation with the n-th write failing, and returns what it threw
   private Throwable run(int n, boolean always, Action operation) {
      clearInvocations(transformHandler);
      audits.clear();
      writes = 0;
      failAt = n;
      persistent = always;

      if(uninstalled) {
         doReturn(null).when(config).getDataSourceClass(TABULAR_TYPE);
         registry.clearCache();
      }

      try {
         operation.run();
         return null;
      }
      catch(Throwable e) {
         return e;
      }
      finally {
         failAt = 0;

         if(uninstalled) {
            doReturn(TestTabularDataSource.class.getName()).when(config)
               .getDataSourceClass(TABULAR_TYPE);
            registry.clearCache();
         }
      }
   }

   private String scenario() throws Exception {
      String p = "rf" + (++seq) + "x";
      addFolder(p + "F");
      addFolder(p + "F/G");
      addFolder(p + "Dest");
      folderGrant(p + "F");
      folderGrant(p + "F/G");

      TestTabularDataSource a = tabular(p + "F/A");
      TestTabularDataSource add = new TestTabularDataSource();
      add.setName("add");
      a.addDatasource(add);
      tabular(p + "F/B");
      tabular(p + "F/G/C");
      registry.setDataSource(source(p + "F/D"), false);
      XDataModel model = new XDataModel(p + "F/D");
      registry.setDataModel(model);
      assertNotNull(registry.getDataModel(p + "F/D"));

      for(String source : SOURCES) {
         grant(p + "F/" + source);
      }

      grant(p + "F/A::add");
      return p;
   }

   private TestTabularDataSource tabular(String path) {
      TestTabularDataSource dataSource = new TestTabularDataSource();
      dataSource.setName(path);
      registry.setDataSource(dataSource, false);
      return (TestTabularDataSource) registry.getDataSource(path);
   }

   private void emMove(String path, int type, String destination) throws Exception {
      int index = path.lastIndexOf('/');
      ContentRepositoryTreeNode node = ContentRepositoryTreeNode.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path)
         .type(type)
         .build();
      ContentRepositoryTreeNode target = ContentRepositoryTreeNode.builder()
         .label(destination)
         .path(destination)
         .type(RepositoryEntry.DATA_SOURCE_FOLDER)
         .build();
      MoveCopyTreeNodesRequest request = MoveCopyTreeNodesRequest.builder()
         .source(List.of(node))
         .destination(target)
         .build();
      objectService().moveFiles(request, true, principal);
   }

   private RepositoryObjectService objectService() throws Exception {
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE_FOLDER, inv.<String>getArgument(1)));
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      return new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), provider,
         permissions, repository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), registry, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), transformHandler,
         repletRegistries, mock(DashboardRegistryManager.class));
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   // a permission of its own for each data source, kept under "<old path>#grant" to compare
   private void grant(String resource) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, Set.of(new Permission.PermissionIdentity(
         "alice", Organization.getDefaultOrganizationID())));
      store.put(key(ResourceType.DATA_SOURCE, resource), permission);
      store.put(key(ResourceType.DATA_SOURCE, resource + "#grant"), permission);
   }

   private void folderGrant(String path) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, Set.of(new Permission.PermissionIdentity(
         "bob", Organization.getDefaultOrganizationID())));
      store.put(key(ResourceType.DATA_SOURCE_FOLDER, path), permission);
   }

   private Permission perm(ResourceType type, String resource) {
      return store.get(key(type, resource));
   }

   private static String key(ResourceType type, String resource) {
      return type + ":" + resource;
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

   @FunctionalInterface
   private interface Operation {
      void run(String prefix) throws Exception;
   }

   @FunctionalInterface
   private interface Action {
      void run() throws Exception;
   }
}
