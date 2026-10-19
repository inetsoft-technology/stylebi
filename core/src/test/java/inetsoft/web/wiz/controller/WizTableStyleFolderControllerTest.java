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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WizTableStyleFolderControllerTest {
   @Test
   void list_includesEmptyAndNestedFoldersWithDirectCounts() {
      Fixture f = new Fixture();
      f.folders(null, "Sales", "Empty");
      f.folders("Sales", "Sales~Q1");
      f.styles("Sales", style("1", "Sales~Bands"));

      List<WizTableStyleFolderController.TableStyleFolderSummary> result =
         f.controller.list(f.principal);

      assertEquals(List.of("Empty", "Sales", "Sales/Q1"),
                   result.stream().map(WizTableStyleFolderController.TableStyleFolderSummary::path).toList());
      assertEquals(1, result.get(1).styleCount());
      assertEquals(1, result.get(1).subfolderCount());
      assertEquals("Sales", result.get(2).parent());
      assertEquals(0, result.get(0).styleCount());
   }

   @Test
   void create_addsFolderAndSaves() throws Exception {
      Fixture f = new Fixture();
      f.folders(null, "Sales");

      f.controller.create(new WizTableStyleFolderController.CreateFolderRequest("Q1", "Sales"),
                          f.principal);

      verify(f.lib).addTableStyleFolder("Sales~Q1");
      verify(f.lib).save();
   }

   @Test
   void create_refusalsMutateNothing() throws Exception {
      Fixture f = new Fixture();
      f.folders(null, "Sales");
      when(f.tableStyleService.containsTableStyle("Taken")).thenReturn(true);

      assertThrows(IllegalArgumentException.class, () -> f.controller.create(
         new WizTableStyleFolderController.CreateFolderRequest("Q1", "NoSuch"), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.create(
         new WizTableStyleFolderController.CreateFolderRequest("Sales", null), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.create(
         new WizTableStyleFolderController.CreateFolderRequest("Taken", null), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.create(
         new WizTableStyleFolderController.CreateFolderRequest("A~B", null), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.create(
         new WizTableStyleFolderController.CreateFolderRequest("A/B", null), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.create(
         new WizTableStyleFolderController.CreateFolderRequest("  ", null), f.principal));
      verify(f.lib, never()).addTableStyleFolder(anyString());
      verify(f.lib, never()).save();
   }

   @Test
   void create_withoutWritePermissionIsDenied() throws Exception {
      Fixture f = new Fixture();
      when(f.securityEngine.checkPermission(any(), eq(ResourceType.TABLE_STYLE_LIBRARY), eq("*"),
                                            eq(ResourceAction.WRITE))).thenReturn(false);

      assertThrows(SecurityException.class, () -> f.controller.create(
         new WizTableStyleFolderController.CreateFolderRequest("New", null), f.principal));
      verify(f.lib, never()).addTableStyleFolder(anyString());
   }

   @Test
   void rename_renamesStylesAndSubfoldersKeepingIdsAndSaves() throws Exception {
      Fixture f = new Fixture();
      f.folders(null, "Sales");
      f.folders("Sales", "Sales~Q1");
      f.styles("Sales", style("1", "Sales~Bands"));
      f.styles("Sales~Q1", style("2", "Sales~Q1~Deep"));

      f.controller.update(
         new WizTableStyleFolderController.UpdateFolderRequest("Sales", "Revenue", null), f.principal);

      verify(f.lib).renameTableStyle("Sales~Bands", "Revenue~Bands", "1");
      verify(f.lib).renameTableStyleFolder("Sales", "Revenue");
      verify(f.lib).renameTableStyle("Sales~Q1~Deep", "Revenue~Q1~Deep", "2");
      verify(f.lib).renameTableStyleFolder("Sales~Q1", "Revenue~Q1");
      verify(f.lib).save();
   }

   @Test
   void move_toAnotherParentAndToRoot() throws Exception {
      Fixture f = new Fixture();
      f.folders(null, "Sales", "Archive");
      f.folders("Sales", "Sales~Q1");

      f.controller.update(
         new WizTableStyleFolderController.UpdateFolderRequest("Sales/Q1", null, "Archive"), f.principal);
      verify(f.lib).renameTableStyleFolder("Sales~Q1", "Archive~Q1");

      f.controller.update(
         new WizTableStyleFolderController.UpdateFolderRequest("Sales/Q1", null, ""), f.principal);
      verify(f.lib).renameTableStyleFolder("Sales~Q1", "Q1");
      verify(f.securityEngine, atLeastOnce()).checkPermission(
         any(), eq(ResourceType.TABLE_STYLE_LIBRARY), eq("*"), eq(ResourceAction.WRITE));
   }

   @Test
   void update_refusalsMutateNothing() throws Exception {
      Fixture f = new Fixture();
      f.folders(null, "Sales", "Taken");
      f.folders("Sales", "Sales~Q1");
      when(f.lib.isAuditStyleFolder("Audit")).thenReturn(true);
      when(f.lib.containsFolder("Audit")).thenReturn(true);

      assertThrows(IllegalArgumentException.class, () -> f.controller.update(
         new WizTableStyleFolderController.UpdateFolderRequest("Sales", null, "Sales/Q1"), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.update(
         new WizTableStyleFolderController.UpdateFolderRequest("Sales", "Taken", null), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.update(
         new WizTableStyleFolderController.UpdateFolderRequest("Audit", "X", null), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.update(
         new WizTableStyleFolderController.UpdateFolderRequest("Sales", null, null), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.update(
         new WizTableStyleFolderController.UpdateFolderRequest("NoSuch", "X", null), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.update(
         new WizTableStyleFolderController.UpdateFolderRequest("Sales", "A~B", null), f.principal));
      verify(f.lib, never()).renameTableStyleFolder(anyString(), anyString());
      verify(f.lib, never()).save();
   }

   @Test
   void rename_deniedDeepInTheSubtreeAbortsBeforeAnyMutation() throws Exception {
      Fixture f = new Fixture();
      f.folders(null, "Sales");
      f.styles("Sales", style("1", "Sales~Locked"));
      when(f.securityEngine.checkPermission(any(), eq(ResourceType.TABLE_STYLE), eq("Sales~Locked"),
                                            eq(ResourceAction.DELETE))).thenReturn(false);

      assertThrows(SecurityException.class, () -> f.controller.update(
         new WizTableStyleFolderController.UpdateFolderRequest("Sales", "Revenue", null), f.principal));
      verify(f.lib, never()).renameTableStyle(anyString(), anyString(), anyString());
      verify(f.lib, never()).renameTableStyleFolder(anyString(), anyString());
      verify(f.lib, never()).save();
   }

   @Test
   void delete_emptyFolderNeedsNoRecursive() throws Exception {
      Fixture f = new Fixture();
      f.folders(null, "Empty");

      f.controller.delete("Empty", null, f.principal);

      verify(f.lib).removeTableStyleFolder("Empty");
      verify(f.lib).save();
      verify(f.securityEngine).removePermission(ResourceType.TABLE_STYLE, "Empty");
   }

   @Test
   void delete_nonEmptyWithoutRecursiveRefusesAndRemovesNothing() {
      Fixture f = new Fixture();
      f.folders(null, "Sales");
      f.styles("Sales", style("1", "Sales~Bands"));

      Exception e = assertThrows(IllegalArgumentException.class,
                                 () -> f.controller.delete("Sales", false, f.principal));

      assertTrue(e.getMessage().contains("1 table style"), e.getMessage());
      verify(f.lib, never()).removeTableStyleFolder(anyString());
      verify(f.lib, never()).removeTableStyle(anyString());
   }

   @Test
   void delete_recursiveRemovesSubtreeAndEveryPermission() throws Exception {
      Fixture f = new Fixture();
      f.folders(null, "Sales");
      f.folders("Sales", "Sales~Q1");
      f.styles("Sales", style("1", "Sales~Bands"));
      f.styles("Sales~Q1", style("2", "Sales~Q1~Deep"));

      f.controller.delete("Sales", true, f.principal);

      verify(f.lib).removeTableStyleFolder("Sales");
      verify(f.lib).removeTableStyleFolder("Sales~Q1");
      verify(f.lib).removeTableStyle("1");
      verify(f.lib).removeTableStyle("2");
      verify(f.lib).save();

      for(String name : List.of("Sales", "Sales~Q1", "Sales~Bands", "Sales~Q1~Deep")) {
         verify(f.securityEngine).removePermission(ResourceType.TABLE_STYLE, name);
      }
   }

   @Test
   void delete_deniedDeepInTheSubtreeAbortsBeforeAnyRemoval() throws Exception {
      Fixture f = new Fixture();
      f.folders(null, "Sales");
      f.styles("Sales", style("1", "Sales~Locked"));
      when(f.securityEngine.checkPermission(any(), eq(ResourceType.TABLE_STYLE), eq("Sales~Locked"),
                                            eq(ResourceAction.DELETE))).thenReturn(false);

      assertThrows(SecurityException.class,
                   () -> f.controller.delete("Sales", true, f.principal));
      verify(f.lib, never()).removeTableStyleFolder(anyString());
      verify(f.lib, never()).removeTableStyle(anyString());
      verify(f.lib, never()).save();
   }

   @Test
   void delete_auditFolderIsRefused() {
      Fixture f = new Fixture();
      when(f.lib.containsFolder("Audit")).thenReturn(true);
      when(f.lib.isAuditStyleFolder("Audit")).thenReturn(true);

      assertThrows(IllegalArgumentException.class,
                   () -> f.controller.delete("Audit", true, f.principal));
      verify(f.lib, never()).removeTableStyleFolder(anyString());
   }

   @Test
   void renameHelper_rewritesStylesFolderAndSubfoldersToTheTargetPath() {
      LibManager lib = mock(LibManager.class);
      when(lib.getTableStyles("A")).thenReturn(new XTableStyle[] { style("1", "A~S") });
      when(lib.getTableStyleFolders("A")).thenReturn(new String[] { "A~B" });
      when(lib.getTableStyles("A~B")).thenReturn(new XTableStyle[] { style("2", "A~B~T") });
      when(lib.getTableStyleFolders("A~B")).thenReturn(new String[0]);

      AssetEventUtil.renameTableStyleFolder("A", "X~A", lib);

      verify(lib).renameTableStyle("A~S", "X~A~S", "1");
      verify(lib).renameTableStyleFolder("A", "X~A");
      verify(lib).renameTableStyle("A~B~T", "X~A~B~T", "2");
      verify(lib).renameTableStyleFolder("A~B", "X~A~B");
   }

   private static XTableStyle style(String id, String name) {
      XTableStyle style = new XTableStyle(new DefaultTableLens());
      style.setID(id);
      style.setName(name);
      return style;
   }

   private static final class Fixture {
      Fixture() {
         try {
            when(securityEngine.checkPermission(any(), any(ResourceType.class),
                                                nullable(String.class),
                                                any(ResourceAction.class))).thenReturn(true);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }

         when(libManagerProvider.getManager(any(Principal.class))).thenReturn(lib);
         when(lib.getTableStyleFolders(nullable(String.class))).thenReturn(new String[0]);
         when(lib.getTableStyleFolders(nullable(String.class), anyBoolean())).thenReturn(new String[0]);
         when(lib.getTableStyles(nullable(String.class))).thenReturn(new XTableStyle[0]);
         when(lib.getTableStyles(nullable(String.class), anyBoolean())).thenReturn(new XTableStyle[0]);
         controller = new WizTableStyleFolderController(
            libManagerProvider, tableStyleService, securityEngine);
      }

      /** Registers {@code children} as the direct subfolders of {@code parent} (null = root). */
      void folders(String parent, String... children) {
         when(lib.getTableStyleFolders(parent)).thenReturn(children);
         when(lib.getTableStyleFolders(parent, true)).thenReturn(children);

         for(String child : children) {
            when(lib.containsFolder(child)).thenReturn(true);
         }
      }

      void styles(String folder, XTableStyle... styles) {
         when(lib.getTableStyles(folder)).thenReturn(styles);
         when(lib.getTableStyles(folder, true)).thenReturn(styles);
      }

      final LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      final TableStyleService tableStyleService = mock(TableStyleService.class);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);
      final LibManager lib = mock(LibManager.class);
      final Principal principal = mock(Principal.class);
      final WizTableStyleFolderController controller;
   }
}
