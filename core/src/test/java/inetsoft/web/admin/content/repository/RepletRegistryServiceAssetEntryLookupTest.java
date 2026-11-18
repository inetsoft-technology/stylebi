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

import inetsoft.sree.*;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80317, #80318: moving or deleting a dashboard looked it up through a listing of its whole
 * parent folder that parsed every sibling sheet, and every move or delete step wrote storage, which
 * dropped the sheet cache, so each step re-parsed the whole folder. The lookup without
 * loadProperties must not load any sibling sheet; the default lookup keeps its behavior.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepletRegistryServiceAssetEntryLookupTest {
   private static final String MARK = "zz_sheet";

   @BeforeEach
   void setUp(TestInfo info) throws Exception {
      repo = AssetUtil.getAssetRepository(false);
      orgId = Organization.getDefaultOrganizationID();
      prefix = "b80317" + info.getTestMethod().orElseThrow().getName();
      security = permissionEngine();
      istoreField = AbstractAssetEngine.class.getDeclaredField("istore");
      istoreField.setAccessible(true);
      originalStore = (IndexedStorage) istoreField.get(repo);
      counting = new CountingStorage(blobStorageManager);
      istoreField.set(repo, counting);
      // the static mock is thread-local, so the tests run on the thread of the set up
      staticSecurity = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      staticSecurity.when(SecurityEngine::getSecurity).thenReturn(security);
   }

   @AfterEach
   void tearDown() throws Exception {
      staticSecurity.close();
      istoreField.set(repo, originalStore);
   }

   @Test
   @Timeout(value = 60)
   void lookupWithoutPropertiesLoadsNoSiblingSheet() throws Exception {
      String folder = prefix + "F";
      addFolder(folder);
      AssetEntry[] sheets = addSheets(folder, 8);

      counting.sheetLoads.set(0);
      AssetEntry found = service().getAssetEntry(sheets[3].toIdentifier(), admin(), false);

      assertNotNull(found);
      assertEquals(sheets[3].toIdentifier(), found.toIdentifier());
      assertEquals(AssetEntry.Type.VIEWSHEET, found.getType());
      assertEquals(0, counting.sheetLoads.get(), "no sheet may be parsed by the lookup");
   }

   @Test
   @Timeout(value = 60)
   void defaultLookupStillLoadsTheListingProperties() throws Exception {
      String folder = prefix + "F";
      addFolder(folder);
      AssetEntry[] sheets = addSheets(folder, 8);

      counting.sheetLoads.set(0);
      AssetEntry found = service().getAssetEntry(sheets[3].toIdentifier(), admin());

      assertNotNull(found);
      assertNotNull(found.getProperty("onReport"));
      assertTrue(counting.sheetLoads.get() >= sheets.length,
                 "the default lookup keeps loading the sibling sheets");
   }

   @Test
   @Timeout(value = 60)
   void missingEntryAndMissingAdminPermissionGiveNull() throws Exception {
      String folder = prefix + "F";
      addFolder(folder);
      AssetEntry[] sheets = addSheets(folder, 2);
      AssetEntry missing = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                          folder + "/nothing", null, orgId);

      assertNull(service().getAssetEntry(missing.toIdentifier(), admin(), false));

      SecurityEngine deny = mock(SecurityEngine.class, withSettings()
         .spiedInstance(SecurityEngine.getSecurity()).defaultAnswer(inv ->
            "checkPermission".equals(inv.getMethod().getName()) ? Boolean.FALSE :
               inv.callRealMethod()));
      RepletRegistryService denied = new RepletRegistryService(
         deny, null, mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         RepletRegistryManager.getInstance());
      Principal user = new SRPrincipal(new IdentityID("nobody", orgId), new IdentityID[0],
                                       new String[0], orgId, 2L);

      staticSecurity.when(SecurityEngine::getSecurity).thenReturn(deny);
      assertNull(denied.getAssetEntry(sheets[0].toIdentifier(), user, false));
   }

   @Test
   @Timeout(value = 120)
   void movingKOfMDashboardsParsesOrderKSheets() throws Exception {
      int m = 20;
      int k = 5;
      String from = prefix + "From";
      String to = prefix + "To";
      addFolder(from);
      addFolder(to);
      AssetEntry[] sheets = addSheets(from, m);
      RepletRegistryService service = service();
      // warm the sheet cache, as a previous listing of the folder does
      service.getAssetEntry(sheets[0].toIdentifier(), admin());

      counting.sheetLoads.set(0);
      moveAll(service, sheets, k, to);
      int loads = counting.sheetLoads.get();

      // before the fix every step re-parsed all siblings: about k * m
      assertTrue(loads <= 6 * k, "moving " + k + " of " + m + " parsed " + loads + " sheets");

      for(int i = 0; i < m; i++) {
         AssetEntry now = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                         (i < k ? to : from) + "/" + MARK + i, null, orgId);
         AssetEntry old = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                         (i < k ? from : to) + "/" + MARK + i, null, orgId);
         assertNotNull(service.getAssetEntry(now.toIdentifier(), admin(), false), "sheet " + i);
         assertNull(service.getAssetEntry(old.toIdentifier(), admin(), false), "sheet " + i);
      }
   }

   @Test
   @Timeout(value = 120)
   void deletingKOfMDashboardsParsesOrderKSheets() throws Exception {
      int m = 20;
      int k = 5;
      String folder = prefix + "F";
      addFolder(folder);
      AssetEntry[] sheets = addSheets(folder, m);
      ContentRepositoryTreeService tree = mock(ContentRepositoryTreeService.class);
      when(tree.getAssetScope(anyString())).thenReturn(AssetRepository.GLOBAL_SCOPE);
      when(tree.getUnscopedPath(anyString())).thenAnswer(inv -> inv.getArgument(0));
      ResourcePermissionService resources = mock(ResourcePermissionService.class);
      when(resources.getRepositoryResourceType(anyInt(), anyString()))
         .thenReturn(new Resource(ResourceType.REPORT, "x"));
      RepositoryObjectService objects = new RepositoryObjectService(
         service(), tree, mock(SecurityProvider.class), resources,
         null, null, null, null, null, mock(inetsoft.web.RecycleBin.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         RepletRegistryManager.getInstance(), null);
      TreeNodeInfo[] nodes = new TreeNodeInfo[k];

      for(int i = 0; i < k; i++) {
         nodes[i] = TreeNodeInfo.builder().label(MARK + i).path(sheets[i].getPath())
            .type(RepositoryEntry.VIEWSHEET).build();
      }

      service().getAssetEntry(sheets[m - 1].toIdentifier(), admin());
      counting.sheetLoads.set(0);

      for(TreeNodeInfo node : nodes) {
         objects.deleteNodes(new TreeNodeInfo[] { node }, admin(), false, true);
      }

      int loads = counting.sheetLoads.get();
      assertTrue(loads <= 6 * k, "deleting " + k + " of " + m + " parsed " + loads + " sheets");

      for(int i = 0; i < m; i++) {
         assertEquals(i >= k, service().getAssetEntry(sheets[i].toIdentifier(), admin(), false)
            != null, "sheet " + i);
      }
   }

   @Test
   @Timeout(value = 60)
   void renameThroughUpdateSheetKeepsOnReport() throws Exception {
      String folder = prefix + "F";
      addFolder(folder);
      AssetEntry[] sheets = addSheets(folder, 3);

      AssetEntry updated = service().updateSheet(sheets[1].toIdentifier(), MARK + "1",
                                                 MARK + "renamed", null, "d", admin());

      assertNotNull(updated.getProperty("onReport"));
   }

   private void moveAll(RepletRegistryService service, AssetEntry[] sheets, int k, String to)
      throws Exception
   {
      for(int i = 0; i < k; i++) {
         String id = sheets[i].toIdentifier();
         assertTrue(service.copyFile(id, sheets[i].getPath(), null,
                                     RepositoryEntry.VIEWSHEET, to, null,
                                     RepositoryEntry.VIEWSHEET, true, null, admin()));
      }
   }

   private void addFolder(String path) throws Exception {
      RepletRegistry reg = RepletRegistryManager.getInstance().getRegistry(orgId);
      reg.addFolder(path);
      reg.save();
   }

   private AssetEntry[] addSheets(String folder, int count) throws Exception {
      AssetEntry[] result = new AssetEntry[count];

      for(int i = 0; i < count; i++) {
         result[i] = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                    folder + "/" + MARK + i, null, orgId);
         repo.setSheet(result[i], new Viewsheet(), admin(), true);
      }

      return result;
   }

   private SRPrincipal admin() {
      IdentityID id = new IdentityID("admin", orgId);
      return new SRPrincipal(id, new IdentityID[] { new IdentityID("Administrator", null) },
                             new String[0], orgId, 1L);
   }

   private RepletRegistryService service() {
      return new RepletRegistryService(
         security, null, mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         RepletRegistryManager.getInstance());
   }

   private SecurityEngine permissionEngine() {
      SecurityEngine real = SecurityEngine.getSecurity();
      return mock(SecurityEngine.class, withSettings().spiedInstance(real).defaultAnswer(inv ->
         "checkPermission".equals(inv.getMethod().getName()) ? Boolean.TRUE :
            inv.callRealMethod()));
   }

   private static final class CountingStorage extends BlobIndexedStorage {
      CountingStorage(BlobStorageManager manager) {
         super(manager);
      }

      @Override
      public XMLSerializable getXMLSerializable(String key, TransformListener trans)
         throws Exception
      {
         count(key);
         return super.getXMLSerializable(key, trans);
      }

      @Override
      public XMLSerializable getXMLSerializable(String key, TransformListener trans, String orgID)
         throws Exception
      {
         count(key);
         return super.getXMLSerializable(key, trans, orgID);
      }

      private void count(String key) {
         if(key != null && key.contains(MARK)) {
            sheetLoads.incrementAndGet();
         }
      }

      final AtomicInteger sheetLoads = new AtomicInteger();
   }

   @Autowired
   private BlobStorageManager blobStorageManager;

   private AssetRepository repo;
   private Field istoreField;
   private IndexedStorage originalStore;
   private CountingStorage counting;
   private SecurityEngine security;
   private MockedStatic<SecurityEngine> staticSecurity;
   private String orgId;
   private String prefix;
}
