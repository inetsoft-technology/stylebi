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
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.web.admin.sheet.Sheet;
import inetsoft.web.admin.sheet.SheetList;
import inetsoft.web.admin.sheet.ws.WorksheetService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Coverage for {@code WorksheetListController}, the non-admin worksheet listing behind the
 * composer-chat {@code list_worksheets} tool (Redmine #78813).
 */
@Tag("core")
class WorksheetListControllerTest {
   @Test
   void mapsGlobalAndUserEntriesWithBareOwnerName() throws Exception {
      Fixture f = new Fixture();
      f.sheets.add(global("Examples/Census"));
      f.sheets.add(user("Mine/WS1", "bob", "org1"));

      try(MockedStatic<SreeEnv> env = secured(); MockedStatic<SecurityEngine> sec = allowAll(f)) {
         List<WorksheetListController.WorksheetListEntry> r =
            f.controller.listWorksheets(f.principal);

         assertEquals(2, r.size());
         assertEquals("global", r.get(0).scope());
         assertEquals("Examples/Census", r.get(0).path());
         assertNull(r.get(0).owner());
         assertEquals("user", r.get(1).scope());
         assertEquals("bob", r.get(1).owner());
      }
   }

   @Test
   void dropsRecycleBinEntries() throws Exception {
      Fixture f = new Fixture();
      f.sheets.add(global("Recycle Bin/Old"));
      f.sheets.add(global("Keep/Me"));

      try(MockedStatic<SreeEnv> env = secured(); MockedStatic<SecurityEngine> sec = allowAll(f)) {
         List<WorksheetListController.WorksheetListEntry> r =
            f.controller.listWorksheets(f.principal);
         assertEquals(1, r.size());
         assertEquals("Keep/Me", r.get(0).path());
      }
   }

   @Test
   void dropsGlobalWorksheetWhenAParentFolderIsNotReadable() throws Exception {
      Fixture f = new Fixture();
      f.sheets.add(global("Locked/WS"));
      f.sheets.add(global("Open/WS"));

      try(MockedStatic<SreeEnv> env = secured();
          MockedStatic<SecurityEngine> sec = mockStatic(SecurityEngine.class))
      {
         SecurityEngine engine = mock(SecurityEngine.class);
         sec.when(SecurityEngine::getSecurity).thenReturn(engine);
         when(engine.checkPermission(any(), eq(ResourceType.ASSET), anyString(), any()))
            .thenReturn(true);
         when(engine.checkPermission(any(), eq(ResourceType.ASSET), eq("Locked"), any()))
            .thenReturn(false);

         List<WorksheetListController.WorksheetListEntry> r =
            f.controller.listWorksheets(f.principal);
         assertEquals(1, r.size());
         assertEquals("Open/WS", r.get(0).path());
      }
   }

   @Test
   void dropsUserWorksheetWhenPrivateReadCheckFails() throws Exception {
      Fixture f = new Fixture();
      f.sheets.add(user("Mine/WS1", "bob", "org1"));
      doThrow(new RuntimeException("denied")).when(f.repository)
         .checkAssetPermission(any(), any(AssetEntry.class), eq(ResourceAction.READ), eq(true));

      try(MockedStatic<SreeEnv> env = secured(); MockedStatic<SecurityEngine> sec = allowAll(f)) {
         assertTrue(f.controller.listWorksheets(f.principal).isEmpty());
      }
   }

   @Test
   void onlyListsWhatTheServiceReturnedForTheCaller() throws Exception {
      Fixture f = new Fixture();
      f.sheets.add(user("Mine/WS1", "bob", "org1"));

      try(MockedStatic<SreeEnv> env = secured(); MockedStatic<SecurityEngine> sec = allowAll(f)) {
         f.controller.listWorksheets(f.principal);
         verify(f.service).getWorksheets(f.principal);
         verifyNoMoreInteractions(f.service);
      }
   }

   @Test
   void securityOffSkipsPermissionChecks() throws Exception {
      Fixture f = new Fixture();
      f.sheets.add(global("A/B"));

      try(MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class);
          MockedStatic<VpmProcessor> vpm = mockStatic(VpmProcessor.class))
      {
         env.when(() -> SreeEnv.getProperty("security.provider")).thenReturn("");
         vpm.when(VpmProcessor::useVpmSecurity).thenReturn(false);
         assertEquals(1, f.controller.listWorksheets(f.principal).size());
      }
   }

   /**
    * With the real {@code WorksheetService} over a mocked repository: a non-admin caller whose
    * principal name is a {@code name~;~org} key only ever has its own private root requested, so
    * another user's private worksheets cannot be listed.
    */
   @Test
   void nonAdminCallerOnlyRequestsGlobalAndOwnUserRoots() throws Exception {
      AssetRepository repo = mock(AssetRepository.class);
      when(repo.getAllEntries(any(), any(), any(), any())).thenReturn(new AssetEntry[0]);
      WorksheetService real = new WorksheetService(repo, null);
      WorksheetListController controller = new WorksheetListController(real, repo);
      Principal caller = mock(Principal.class);
      when(caller.getName()).thenReturn(new IdentityID("bob", "org1").convertToKey());

      try(MockedStatic<SreeEnv> env = secured()) {
         assertTrue(controller.listWorksheets(caller).isEmpty());
      }

      ArgumentCaptor<AssetEntry> roots = ArgumentCaptor.forClass(AssetEntry.class);
      verify(repo, times(2)).getAllEntries(roots.capture(), eq(caller), any(), any());
      assertEquals(AssetRepository.GLOBAL_SCOPE, roots.getAllValues().get(0).getScope());
      assertEquals(AssetRepository.USER_SCOPE, roots.getAllValues().get(1).getScope());
      assertEquals("bob", roots.getAllValues().get(1).getUser().getName());
      assertEquals("org1", roots.getAllValues().get(1).getUser().getOrgID());
   }

   private static MockedStatic<SreeEnv> secured() {
      MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class);
      env.when(() -> SreeEnv.getProperty("security.provider")).thenReturn("custom");
      return env;
   }

   private static MockedStatic<SecurityEngine> allowAll(Fixture f) throws Exception {
      MockedStatic<SecurityEngine> sec = mockStatic(SecurityEngine.class);
      SecurityEngine engine = mock(SecurityEngine.class);
      sec.when(SecurityEngine::getSecurity).thenReturn(engine);
      when(engine.checkPermission(any(), any(ResourceType.class), anyString(), any()))
         .thenReturn(true);
      return sec;
   }

   private static Sheet global(String path) {
      AssetEntry e = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null);
      return new Sheet(e.toIdentifier(), path, e.toView(), true, null, null);
   }

   private static Sheet user(String path, String name, String org) {
      IdentityID id = new IdentityID(name, org);
      AssetEntry e = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET, path, id);
      return new Sheet(e.toIdentifier(), path, e.toView(), false, id, null);
   }

   private static final class Fixture {
      final WorksheetService service = mock(WorksheetService.class);
      final AssetRepository repository = mock(AssetRepository.class);
      final Principal principal = mock(Principal.class);
      final SheetList list = new SheetList();
      final List<Sheet> sheets = list.getSheets();
      final WorksheetListController controller;

      Fixture() throws Exception {
         when(service.getWorksheets(any())).thenReturn(list);
         controller = new WorksheetListController(service, repository);
      }
   }
}
