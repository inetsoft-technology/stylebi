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
package inetsoft.web.admin.security.user;

/*
 * Bug #77098: the role rename VPM migration must write the VPMs of the role's org through the real
 * DataSourceRegistry, whose reads and writes resolve the current org. Unlike
 * UserTreeServiceIdentityRenameOrgTest, the OrganizationManager, the XPrincipal org precedence, the
 * registry and XDataModel are real here; only the storage is an in-memory map.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.HiddenColumns;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.*;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.sree.internal.cluster.Cluster;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceRoleRenameVpmRegistryTest {
   @BeforeEach
   void setUp() throws Exception {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));

      // in-memory storage; lastModified is always newer than a cached object, so every read goes
      // to the map and the registry cache can't hide a write to the wrong org
      IndexedStorage indexedStorage = mock(IndexedStorage.class, withSettings().lenient());
      doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
         .when(indexedStorage).putXMLSerializable(anyString(), any());
      when(indexedStorage.getXMLSerializable(anyString(), any(), any()))
         .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
      when(indexedStorage.contains(anyString()))
         .thenAnswer(inv -> store.containsKey(inv.<String>getArgument(0)));
      when(indexedStorage.lastModified(anyString(), any()))
         .thenAnswer(inv -> store.containsKey(inv.<String>getArgument(0)) ? Long.MAX_VALUE : 0L);

      Config uqlConfig = mock(Config.class, withSettings().lenient());
      when(uqlConfig.getDataSourceClass(anyString())).thenReturn(JDBCDataSource.class.getName());
      registry = new DataSourceRegistry(indexedStorage, uqlConfig, mock(Cluster.class));

      // XDataModel.getRegistry() and the registry's Cluster/Drivers lookups go through Spring
      Map<Class<?>, Object> beans = new ConcurrentHashMap<>();
      beans.put(DataSourceRegistry.class, registry);
      ApplicationContext appContext = mock(ApplicationContext.class, withSettings().lenient());
      when(appContext.getBean(any(Class.class)))
         .thenAnswer(inv -> beans.computeIfAbsent(inv.getArgument(0), c -> mock((Class<?>) c)));
      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      ConfigurationContext.getContext().setApplicationContext(appContext);

      // the session's current org is ORG_A; the real XPrincipal.getCurrentOrgId() checks the
      // OrganizationContextHolder first
      XPrincipal principal = mock(XPrincipal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("admin", ORG_A).convertToKey());
      when(principal.getProperty("curr_org_id")).thenReturn(ORG_A);
      when(principal.getCurrentOrgId()).thenCallRealMethod();
      savedPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      // UserTreeService only calls getDataSourceFullNames() and getDataModel(), which XEngine
      // delegates to the registry
      XRepository xRepository = mock(XRepository.class, withSettings().lenient());
      when(xRepository.getDataSourceFullNames(any(IdentityID.class)))
         .thenAnswer(inv -> registry.getDataSourceFullNames(inv.<IdentityID>getArgument(0)));
      when(xRepository.getDataModel(anyString()))
         .thenAnswer(inv -> registry.getDataModel(inv.getArgument(0)));

      service = new UserTreeService(
         null, null, mock(IdentityService.class), null, securityEngine,
         mock(IdentityThemeService.class), null, mock(FavoritesService.class), null, null,
         null, null, null, null, xRepository, null, null);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
      sUtilStatic.close();
      OrganizationContextHolder.clear();
   }

   @Test
   void siteAdmin_renamesRoleOfOtherOrg_writesVpmOfRoleOrgThroughRegistry() throws Exception {
      addVpm(ORG_A, "analyst");
      addVpm(ORG_B, "analyst");
      assertEquals(ORG_A, OrganizationManager.getInstance().getCurrentOrgID());

      service.migrateRoleRename(new IdentityID("analyst", ORG_B),
                                new IdentityID("analyst2", ORG_B));

      assertEquals(List.of("analyst2"), storedVpmRoles(ORG_B));
      assertEquals(List.of("analyst"), storedVpmRoles(ORG_A));
      assertEquals(ORG_A, OrganizationManager.getInstance().getCurrentOrgID(),
                   "the org scope must be restored");
   }

   // stores a data source "ds" with a data model and a VPM "v" in the org, like the registry would
   private void addVpm(String orgID, String... roles) throws Exception {
      OrganizationManager.runInOrgScope(orgID, () -> {
         registry.init();
         JDBCDataSource dataSource = new JDBCDataSource();
         dataSource.setName("ds");
         registry.setObject(entry(AssetEntry.Type.DATA_SOURCE, "ds"),
                            new XDataSourceWrapper(dataSource));
         registry.setObject(entry(AssetEntry.Type.DATA_MODEL, "ds"), new XDataModel("ds"));

         VirtualPrivateModel vpm = new VirtualPrivateModel("v");
         HiddenColumns hiddenColumns = new HiddenColumns();

         for(String role : roles) {
            hiddenColumns.addRole(role);
         }

         vpm.setHiddenColumns(hiddenColumns);
         registry.setObject(entry(AssetEntry.Type.VPM, "ds/v"), vpm);
         return null;
      });
   }

   // reads the VPM from the storage key of the org, bypassing the registry
   private List<String> storedVpmRoles(String orgID) {
      String key = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM, "ds/v", null,
                                  orgID).toIdentifier();
      VirtualPrivateModel vpm = (VirtualPrivateModel) store.get(key);
      assertNotNull(vpm, "no VPM stored under " + key);
      return Collections.list(vpm.getHiddenColumns().getRoles());
   }

   private static AssetEntry entry(AssetEntry.Type type, String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);
   }

   private static final String ORG_A = "orga";
   private static final String ORG_B = "orgb";
   private final Map<String, XMLSerializable> store = new ConcurrentHashMap<>();
   private DataSourceRegistry registry;
   private UserTreeService service;
   private ApplicationContext savedAppContext;
   private java.security.Principal savedPrincipal;
   private MockedStatic<SUtil> sUtilStatic;
}
