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

package inetsoft.web.wiz.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.admin.sheet.Sheet;
import inetsoft.web.admin.sheet.SheetList;
import inetsoft.web.admin.sheet.vs.ViewsheetService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Coverage for {@code ViewsheetListController}, the non-admin viewsheet listing behind the
 * composer-chat {@code list_viewsheets} tool (Redmine #81016). The filtering itself is shared with
 * the worksheet listing ({@code SheetListSupport}) and covered in depth by
 * {@code WorksheetListControllerTest}; this pins that the viewsheet listing uses it.
 */
@Tag("core")
class ViewsheetListControllerTest {
   @Test
   void mapsGlobalAndUserViewsheetsAndDropsRecycleBinEntries() throws Exception {
      Fixture f = new Fixture();
      f.sheets.add(global("Sales/Dashboard"));
      f.sheets.add(global("Recycle Bin/Old"));
      f.sheets.add(user("Mine/VS1", "bob", "org1"));

      try(MockedStatic<SreeEnv> env = secured(); MockedStatic<SecurityEngine> sec = allowAll()) {
         List<ViewsheetListController.ViewsheetListEntry> r =
            f.controller.listViewsheets(f.principal);

         assertEquals(2, r.size());
         assertEquals("Sales/Dashboard", r.get(0).path());
         assertEquals("global", r.get(0).scope());
         assertNull(r.get(0).owner());
         assertEquals("user", r.get(1).scope());
         assertEquals("bob", r.get(1).owner());
      }
   }

   @Test
   void dropsAViewsheetWhoseParentFolderIsNotReadable() throws Exception {
      Fixture f = new Fixture();
      f.sheets.add(global("Locked/VS"));
      f.sheets.add(global("Open/VS"));

      try(MockedStatic<SreeEnv> env = secured();
          MockedStatic<SecurityEngine> sec = mockStatic(SecurityEngine.class))
      {
         SecurityEngine engine = mock(SecurityEngine.class);
         sec.when(SecurityEngine::getSecurity).thenReturn(engine);
         when(engine.checkPermission(any(), eq(ResourceType.ASSET), anyString(), any()))
            .thenReturn(true);
         when(engine.checkPermission(any(), eq(ResourceType.ASSET), eq("Locked"), any()))
            .thenReturn(false);

         List<ViewsheetListController.ViewsheetListEntry> r =
            f.controller.listViewsheets(f.principal);
         assertEquals(1, r.size());
         assertEquals("Open/VS", r.get(0).path());
      }
   }

   @Test
   void dropsAUserViewsheetWhenThePrivateReadCheckFails() throws Exception {
      Fixture f = new Fixture();
      f.sheets.add(user("Mine/VS1", "bob", "org1"));
      doThrow(new RuntimeException("denied")).when(f.repository)
         .checkAssetPermission(any(), any(AssetEntry.class), eq(ResourceAction.READ), eq(true));

      try(MockedStatic<SreeEnv> env = secured(); MockedStatic<SecurityEngine> sec = allowAll()) {
         assertTrue(f.controller.listViewsheets(f.principal).isEmpty());
      }
   }

   private static MockedStatic<SreeEnv> secured() {
      MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class);
      env.when(() -> SreeEnv.getProperty("security.provider")).thenReturn("custom");
      return env;
   }

   private static MockedStatic<SecurityEngine> allowAll() throws Exception {
      MockedStatic<SecurityEngine> sec = mockStatic(SecurityEngine.class);
      SecurityEngine engine = mock(SecurityEngine.class);
      sec.when(SecurityEngine::getSecurity).thenReturn(engine);
      when(engine.checkPermission(any(), any(ResourceType.class), anyString(), any()))
         .thenReturn(true);
      return sec;
   }

   private static Sheet global(String path) {
      AssetEntry e = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null);
      return new Sheet(e.toIdentifier(), path, e.toView(), true, null, null);
   }

   private static Sheet user(String path, String name, String org) {
      IdentityID id = new IdentityID(name, org);
      AssetEntry e = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, path, id);
      return new Sheet(e.toIdentifier(), path, e.toView(), false, id, null);
   }

   private static final class Fixture {
      final ViewsheetService service = mock(ViewsheetService.class);
      final AssetRepository repository = mock(AssetRepository.class);
      final Principal principal = mock(Principal.class);
      final SheetList list = new SheetList();
      final List<Sheet> sheets = list.getSheets();
      final ViewsheetListController controller;

      Fixture() throws Exception {
         when(service.getViewsheets(any())).thenReturn(list);
         controller = new ViewsheetListController(service, repository);
      }
   }
}
