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
package inetsoft.uql.asset;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.*;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81546: renaming a folder rewrote the dependency storage once per renamed sheet. The folder
 * rename now rewrites it once, and queues the worksheet transforms after that, so a worksheet's
 * transform names the worksheets that depend on it by their new path whatever the order of the sheets.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  AbstractAssetEngineFolderRenameDependenciesTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractAssetEngineFolderRenameDependenciesTest {
   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp(TestInfo info) throws Exception {
      principal = ThreadContext.getContextPrincipal();
      orgId = Organization.getDefaultOrganizationID();
      prefix = "b81546" + info.getTestMethod().orElseThrow().getName();
      store.clear();
      transformDependents.clear();
      reset(dependencyHandler, xRepository);
      LocalDependencyHandler real = new LocalDependencyHandler(xRepository);
      doAnswer(i -> real.updateDependencies((AssetObject) i.getArgument(0),
                                            (AssetObject) i.getArgument(1)))
         .when(dependencyHandler).updateDependencies(any(AssetObject.class),
                                                     any(AssetObject.class));
      doAnswer(i -> real.updateDependencies((Map<AssetObject, AssetObject>) i.getArgument(0)))
         .when(dependencyHandler).updateDependencies(any(Map.class));
      doAnswer(i -> real.getRenameDependencyInfo((AssetObject) i.getArgument(0),
                                                 (AssetObject) i.getArgument(1)))
         .when(dependencyHandler).getRenameDependencyInfo(any(), any());
      doAnswer(i -> {
         RenameInfo rinfo = i.getArgument(0);
         transformDependents.put(rinfo.getOldName(),
                                 DependencyTool.getDependencies(rinfo.getOldName()));
         return null;
      }).when(xRepository).renameTransform(any(RenameInfo.class));
      storage = new BlobIndexedStorage(blobStorageManager);
      engine = new Engine(storage);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(principal);
   }

   @Test
   void transformNamesTheMovedDependentWhenTheDependentIsRenamedFirst() throws Exception {
      checkTransformTarget("a", "b");
   }

   @Test
   void transformNamesTheMovedDependentWhenTheBoundWorksheetIsRenamedFirst() throws Exception {
      checkTransformTarget("b", "a");
   }

   private void checkTransformTarget(String dependentName, String boundName) throws Exception {
      String oldFolder = prefix + "Old";
      String newFolder = prefix + "New";
      AssetEntry folder = folder(oldFolder);
      AssetEntry dependent = sheet(AssetEntry.Type.WORKSHEET, oldFolder + "/" + dependentName);
      AssetEntry bound = sheet(AssetEntry.Type.WORKSHEET, oldFolder + "/" + boundName);
      store.put(bound.toIdentifier(), dependents(dependent));

      engine.changeFolder(folder, target(newFolder), null, true, false);

      AssetEntry movedDependent = sheet(AssetEntry.Type.WORKSHEET,
                                        newFolder + "/" + dependentName);
      assertTrue(transformDependents.containsKey(bound.toIdentifier()));
      List<String> ids = transformDependents.get(bound.toIdentifier()).stream()
         .map(a -> ((AssetEntry) a).toIdentifier()).toList();
      assertEquals(List.of(movedDependent.toIdentifier()), ids);
   }

   // the dependency storage is read once for the folder, not once per renamed sheet
   @Test
   void folderRenameScansTheDependencyStorageOnce() throws Exception {
      String oldFolder = prefix + "Old";
      AssetEntry folder = folder(oldFolder);

      for(int i = 0; i < 5; i++) {
         sheet(AssetEntry.Type.WORKSHEET, oldFolder + "/d" + i);
      }

      store.put("other", dependents(sheet(AssetEntry.Type.WORKSHEET, "unrelated" + prefix)));
      clearInvocations(dependencyStorageService);

      engine.changeFolder(folder, target(prefix + "New"), null, true, false);

      verify(dependencyStorageService, times(1)).getKeys(any());
   }

   // the stored dependency is the new entry as it was when recorded, not one carrying properties
   // the rename adds to the entry later
   @Test
   void storedDependencyDoesNotCarryLaterEntryProperties() throws Exception {
      String oldFolder = prefix + "Old";
      AssetEntry folder = folder(oldFolder);
      AssetEntry dependent = sheet(AssetEntry.Type.WORKSHEET, oldFolder + "/d");
      AssetEntry bound = sheet(AssetEntry.Type.WORKSHEET, "other" + prefix);
      store.put(bound.toIdentifier(), dependents(dependent));

      engine.changeFolder(folder, target(prefix + "New"), null, true, false);

      AssetEntry stored = (AssetEntry) ((DependenciesInfo) store.get(bound.toIdentifier()))
         .getDependencies().get(0);
      assertEquals(sheet(AssetEntry.Type.WORKSHEET, prefix + "New/d").toIdentifier(),
                   stored.toIdentifier());
      assertNull(stored.getProperty(AssetEntry.SHEET_DESCRIPTION));
      assertNull(stored.getProperty(AssetEntry.WORKSHEET_TYPE));
   }

   private DependenciesInfo dependents(AssetEntry... entries) {
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of(entries)));
      return info;
   }

   private AssetEntry target(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, path, null,
                            orgId);
   }

   private AssetEntry folder(String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
                                        path, null, orgId);

      if(!engine.containsEntry(entry)) {
         engine.addFolder(entry, null);
      }

      return entry;
   }

   private AssetEntry sheet(AssetEntry.Type type, String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, path, null, orgId);
      int index = path.lastIndexOf('/');
      AssetEntry parent = index < 0 ? entry.getParent() : target(path.substring(0, index));

      if(!storage.contains(entry.toIdentifier())) {
         storage.putXMLSerializable(entry.toIdentifier(),
                                    new Worksheet());
         AssetFolder folder = (AssetFolder) storage.getXMLSerializable(parent.toIdentifier(), null);
         folder.addEntry(entry);
         storage.putXMLSerializable(parent.toIdentifier(), folder);
      }

      return entry;
   }

   private static final class Engine extends AbstractAssetEngine {
      Engine(IndexedStorage storage) {
         super((LibManagerProvider) null, Cluster.getInstance());
         istore = storage;
         scopes = new int[] { GLOBAL_SCOPE, REPORT_SCOPE, USER_SCOPE };
         Arrays.sort(scopes);
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type,
                                     IdentityID resource, EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source,
                                                       Principal user)
      {
         return true;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return true;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return true;
      }
   }

   private static final Map<String, RenameTransformObject> store = new HashMap<>();
   private static final Map<String, List<AssetObject>> transformDependents = new HashMap<>();

   @Configuration
   static class Beans {
      @Bean
      ScheduleManager scheduleManager() {
         return mock(ScheduleManager.class);
      }

      @Bean
      MVManager mvManager() {
         MVManager manager = mock(MVManager.class);
         when(manager.list(anyBoolean(), any(MVManager.MVFilter.class))).thenReturn(new MVDef[0]);
         return manager;
      }

      @Bean
      XRepository xRepository() {
         return mock(XRepository.class);
      }

      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      @Bean
      DependencyStorageService dependencyStorageService() throws Exception {
         DependencyStorageService service = mock(DependencyStorageService.class);
         when(service.getKeys(any())).thenAnswer(i -> new LinkedHashSet<>(store.keySet()));
         when(service.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
         when(service.getWithOrg(anyString(), any())).thenAnswer(
            i -> store.get(i.<String>getArgument(0)));
         doAnswer(i -> store.put(i.getArgument(0), i.getArgument(1)))
            .when(service).put(anyString(), any());
         return service;
      }
   }

   @Autowired private BlobStorageManager blobStorageManager;
   @Autowired private DependencyHandler dependencyHandler;
   @Autowired private XRepository xRepository;
   @Autowired private DependencyStorageService dependencyStorageService;

   private Principal principal;
   private String orgId;
   private String prefix;
   private IndexedStorage storage;
   private Engine engine;
}
