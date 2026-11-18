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
package inetsoft.util.dep;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.report.Hyperlink;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79435: ViewsheetAsset.getDependencies scanned every key of the org store
 * (IndexedStorage.getKeys) once per dashboard, although the key set is only read to validate
 * viewsheet hyperlinks. The axis is selected dashboards x total keys. Runs against the real
 * asset repository and storage; the storage bean is wrapped only to count getKeys calls.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetAssetDependencyKeyScanTest {
   private AssetRepository repository;
   private SRPrincipal admin;
   private String orgId;
   private final List<AssetEntry> cleanup = new ArrayList<>();

   @BeforeEach
   void setUp() {
      AssetRepository.IGNORE_PERM.set(true);
      repository = AssetUtil.getAssetRepository(false);
      orgId = OrganizationManager.getInstance().getCurrentOrgID();
      admin = new SRPrincipal(new IdentityID("admin", orgId),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], orgId, 1L);
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         for(AssetEntry entry : cleanup) {
            if(repository.containsEntry(entry)) {
               repository.removeSheet(entry, null, true);
            }
         }
      }
      finally {
         cleanup.clear();
         AssetRepository.IGNORE_PERM.remove();
      }
   }

   @Test
   void dashboardsWithoutViewsheetLinksNeverScanKeys() throws Exception {
      List<ViewsheetAsset> assets = new ArrayList<>();

      for(int i = 0; i < 20; i++) {
         AssetEntry entry = viewsheetEntry("vs79435plain" + i);
         save(entry, new Viewsheet());
         assets.add(new ViewsheetAsset(entry));
      }

      IndexedStorage real = IndexedStorage.getIndexedStorage();
      IndexedStorage spy = Mockito.spy(real);

      try(MockedStatic<IndexedStorage> mocked = Mockito.mockStatic(IndexedStorage.class)) {
         mocked.when(IndexedStorage::getIndexedStorage).thenReturn(spy);

         for(ViewsheetAsset asset : assets) {
            asset.getDependencies(new ArrayList<>());
         }
      }

      assertEquals(0, keyScans(spy), "no getKeys scan for dashboards with no viewsheet link");
   }

   @Test
   void viewsheetLinkIsStillResolvedWithOneScanPerDashboard() throws Exception {
      AssetEntry target = viewsheetEntry("vs79435target");
      save(target, new Viewsheet());

      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      Hyperlink link = new Hyperlink(target.toIdentifier(), Hyperlink.VIEWSHEET_LINK);
      ((TextVSAssemblyInfo) text.getVSAssemblyInfo()).setHyperlinkValue(link);
      vs.addAssembly(text);
      AssetEntry source = viewsheetEntry("vs79435source");
      save(source, vs);

      IndexedStorage spy = Mockito.spy(IndexedStorage.getIndexedStorage());

      try(MockedStatic<IndexedStorage> mocked = Mockito.mockStatic(IndexedStorage.class)) {
         mocked.when(IndexedStorage::getIndexedStorage).thenReturn(spy);
         new ViewsheetAsset(source).getDependencies(new ArrayList<>());
      }

      assertEquals(1, keyScans(spy), "one scan, shared by all links of the dashboard");
   }

   private static long keyScans(IndexedStorage spy) {
      return Mockito.mockingDetails(spy).getInvocations().stream()
         .filter(i -> "getKeys".equals(i.getMethod().getName()))
         // the storage delegates getKeys(filter) to getKeys(filter, orgID) on itself; count only
         // the calls made by ViewsheetAsset
         .filter(i -> i.getLocation().toString().contains("ViewsheetAsset.getDependencies"))
         .count();
   }

   private void save(AssetEntry entry, AbstractSheet sheet) throws Exception {
      cleanup.add(entry);
      repository.setSheet(entry, sheet, admin, true);
   }

   private AssetEntry viewsheetEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null,
                            orgId);
   }
}
