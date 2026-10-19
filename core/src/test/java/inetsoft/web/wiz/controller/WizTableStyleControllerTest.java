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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.composer.tablestyle.TableStyleFormatModel;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import inetsoft.web.wiz.service.WizTableStylePreviewRenderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WizTableStyleControllerTest {
   @Test
   void create_refusesANameCollisionInsteadOfSilentlyOverwriting() {
      Fixture fixture = new Fixture();
      when(fixture.tableStyleService.contains(null, "BorderedBands")).thenReturn(true);

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("BorderedBands", null, null, null),
         fixture.principal));

      assertTrue(thrown.getMessage().contains("already exists"),
                 "must say why, got: " + thrown.getMessage());
      verify(fixture.lib, never()).setTableStyle(anyString(), any());
   }

   /**
    * The "/"-joined folder a caller passes must reach {@code TableStyleService} as
    * {@code LibManager}'s own "~"-joined internal separator -- the wire detail this
    * controller exists to keep out of the plugin/composer client.
    */
   @Test
   void create_convertsADisplayFolderToLibManagersInternalSeparatorBeforeCheckingCollision() {
      Fixture fixture = new Fixture();
      when(fixture.tableStyleService.contains("User Defined~Sales", "BorderedBands")).thenReturn(true);

      assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest(
            "BorderedBands", "User Defined/Sales", null, null),
         fixture.principal));

      verify(fixture.tableStyleService).contains("User Defined~Sales", "BorderedBands");
   }

   @Test
   void create_requiresANonBlankName() {
      Fixture fixture = new Fixture();

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("   ", null, null, null), fixture.principal));

      assertTrue(thrown.getMessage().contains("requires 'name'"));
      verifyNoInteractions(fixture.lib);
   }

   /**
    * Left unchecked, a "~" embedded in 'name' synthesizes a full label one nesting level deeper
    * than the caller's own 'folder' -- {@code TableStyleService.contains()} only compares against
    * DIRECT children of that folder, so its collision guard silently never fires for the
    * synthesized deeper name, and the style can land as an orphaned leaf {@code list()} may not
    * even discover.
    */
   @Test
   void create_refusesANameContainingLibManagersInternalSeparator() {
      Fixture fixture = new Fixture();

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("Foo~Bar", null, null, null), fixture.principal));

      assertTrue(thrown.getMessage().contains("'name' must not contain"));
      verifyNoInteractions(fixture.lib);
   }

   @Test
   void create_refusesAFolderContainingLibManagersInternalSeparator() {
      Fixture fixture = new Fixture();

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("BorderedBands", "User Defined~Sales", null, null),
         fixture.principal));

      assertTrue(thrown.getMessage().contains("'folder' must not contain"));
      verifyNoInteractions(fixture.lib);
   }

   /**
    * {@code TableStyleFormatModel.updateTableStyle()} calls into all nine region fields with no
    * null guard -- an incomplete format must be refused loudly here, by name, rather than
    * reaching that method and failing as a bare NullPointerException.
    */
   @Test
   void update_refusesAnIncompleteFormatNamingWhatsMissing() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);
      TableStyleFormatModel incomplete = new TableStyleFormatModel();

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.update(
         "1001", new WizTableStyleController.UpdateTableStyleRequest(null, null, incomplete, null),
         fixture.principal));

      assertTrue(thrown.getMessage().contains("topBorderFormat"));
      assertTrue(thrown.getMessage().contains("bodyRegionFormat"));
      verify(fixture.lib, never()).setTableStyle(anyString(), any());
   }

   /**
    * {@code lib.getTableStyle(styleId)} returns the shared, {@code LibManager}-cached instance --
    * mutating it directly (as {@code read()} does, matching the internal controller's own
    * {@code openTableStyle}) is fine for a display-only read, but a WRITE must clone first so a
    * failed/in-flight update never corrupts what every other reader sees in the meantime.
    */
   @Test
   void update_clonesRatherThanMutatingTheSharedCachedInstance() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);
      long originalModified = fixture.existingStyle.getLastModified();

      fixture.controller.update(
         "1001", new WizTableStyleController.UpdateTableStyleRequest(null, null, null, null), fixture.principal);

      assertEquals(originalModified, fixture.existingStyle.getLastModified(),
                   "the shared cached instance must be untouched");
      verify(fixture.lib).setTableStyle(eq("1001"), argThat(style -> style != fixture.existingStyle));
   }

   /** {@code fixture.existingStyle}'s full name is "User Defined~BorderedBands" -- leaf-only rename. */
   @Test
   void update_renamesTheLeafWhenOnlyNameIsProvided() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);

      fixture.controller.update("1001",
         new WizTableStyleController.UpdateTableStyleRequest("Renamed", null, null, null), fixture.principal);

      verify(fixture.lib).renameTableStyle("User Defined~BorderedBands", "User Defined~Renamed", "1001");
      verify(fixture.lib).setTableStyle(eq("1001"),
         argThat(style -> "User Defined~Renamed".equals(style.getName())));
   }

   @Test
   void update_movesToANewFolderWhenOnlyFolderIsProvided() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);

      fixture.controller.update("1001",
         new WizTableStyleController.UpdateTableStyleRequest(null, "Sales", null, null), fixture.principal);

      verify(fixture.lib).renameTableStyle("User Defined~BorderedBands", "Sales~BorderedBands", "1001");
      verify(fixture.lib).setTableStyle(eq("1001"),
         argThat(style -> "Sales~BorderedBands".equals(style.getName())));
   }

   @Test
   void update_renamesAndMovesTogetherInOneCall() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);

      fixture.controller.update("1001",
         new WizTableStyleController.UpdateTableStyleRequest("Renamed", "Sales", null, null), fixture.principal);

      verify(fixture.lib).renameTableStyle("User Defined~BorderedBands", "Sales~Renamed", "1001");
      verify(fixture.lib).setTableStyle(eq("1001"),
         argThat(style -> "Sales~Renamed".equals(style.getName())));
   }

   /**
    * {@code TableStyleLogicalLibrary.rename}/{@code LibManager.renameTableStyle} do no duplicate-
    * name check themselves -- that's a controller-layer responsibility in the existing composer
    * flows (see {@code AssetEventUtil.isRenameDuplicate}/{@code isChangeDuplicate}), so this
    * controller must do it explicitly or a rename-to-an-existing-name silently collides.
    */
   @Test
   void update_refusesARenameThatCollidesWithAnExistingStyleAtTheDestination() {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);
      when(fixture.tableStyleService.contains("User Defined", "Taken")).thenReturn(true);

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.update(
         "1001", new WizTableStyleController.UpdateTableStyleRequest("Taken", null, null, null),
         fixture.principal));

      assertTrue(thrown.getMessage().contains("already exists"),
                 "must say why, got: " + thrown.getMessage());
      verify(fixture.lib, never()).renameTableStyle(any(), any(), any());
      verify(fixture.lib, never()).setTableStyle(anyString(), any());
   }

   @Test
   void update_refusesANameContainingLibManagersInternalSeparator() {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.update(
         "1001", new WizTableStyleController.UpdateTableStyleRequest("Foo~Bar", null, null, null),
         fixture.principal));

      assertTrue(thrown.getMessage().contains("'name' must not contain"));
      verify(fixture.lib, never()).renameTableStyle(any(), any(), any());
      verify(fixture.lib, never()).setTableStyle(anyString(), any());
   }

   @Test
   void update_refusesAFolderContainingLibManagersInternalSeparator() {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.update(
         "1001", new WizTableStyleController.UpdateTableStyleRequest(null, "User Defined~Sales", null, null),
         fixture.principal));

      assertTrue(thrown.getMessage().contains("'folder' must not contain"));
      verify(fixture.lib, never()).renameTableStyle(any(), any(), any());
      verify(fixture.lib, never()).setTableStyle(anyString(), any());
   }

   /**
    * Regression for the refuter's finding on bug-76742's VTS-004: {@code update()}'s tail,
    * {@code lib.setTableStyle(style.getID(), style)}, runs unconditionally on every call and
    * persists the LOCAL clone -- if that clone's name isn't also updated alongside the
    * {@code lib.renameTableStyle(...)} call that renames the STORED entry, this same call
    * silently undoes its own rename the instant it reaches the tail. A format payload in the
    * same request must not resurrect the pre-rename name either.
    */
   @Test
   void update_aRenameSurvivesTheSameCallsUnconditionalFormatMergeAndPersistTail() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);
      TableStyleFormatModel completeFormat = new TableStyleFormatModel(fixture.existingStyle);

      fixture.controller.update("1001",
         new WizTableStyleController.UpdateTableStyleRequest("Renamed", null, completeFormat, null),
         fixture.principal);

      verify(fixture.lib).setTableStyle(eq("1001"),
         argThat(style -> "User Defined~Renamed".equals(style.getName())));
   }

   /**
    * Same regression, across two separate requests: a rename, then an unrelated format-only
    * update. The second call must not revert the name the first call just set.
    */
   @Test
   void update_aSubsequentFormatOnlyUpdateDoesNotRevertAPriorRename() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);

      fixture.controller.update("1001",
         new WizTableStyleController.UpdateTableStyleRequest("Renamed", null, null, null), fixture.principal);

      // Simulate the real LibManager after the first call: renameTableStyle mutates the stored
      // entry in place, keyed by the same oid, so a re-fetch by styleId now returns the new name.
      XTableStyle renamedStoredEntry = newStyle("1001", "User Defined~Renamed");
      when(fixture.lib.getTableStyle("1001")).thenReturn(renamedStoredEntry);
      TableStyleFormatModel completeFormat = new TableStyleFormatModel(renamedStoredEntry);

      fixture.controller.update("1001",
         new WizTableStyleController.UpdateTableStyleRequest(null, null, completeFormat, null),
         fixture.principal);

      verify(fixture.lib, times(2)).setTableStyle(eq("1001"),
         argThat(style -> "User Defined~Renamed".equals(style.getName())));
   }

   /**
    * Unlike {@code ScriptLibraryController.delete()}, there is deliberately no dependency-conflict
    * check here -- {@code RemoveAssetController}'s own {@code entry.isTableStyle()} branch has
    * none either, so this matches the reference behavior rather than inventing a stricter one.
    */
   @Test
   void delete_hasNoDependencyCheck() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);

      fixture.controller.delete("1001", fixture.principal);

      verify(fixture.lib).removeTableStyle("1001");
      verify(fixture.lib).save();
   }

   @Test
   void read_throwsANamedErrorForAnUnknownStyleId() {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("missing")).thenReturn(null);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> fixture.controller.read("missing", fixture.principal));

      assertTrue(thrown.getMessage().contains("missing"));
   }

   @Test
   void read_deniesAccessWhenSecurityEngineRefusesReadPermission() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);
      when(fixture.securityEngine.checkPermission(any(), eq(ResourceType.TABLE_STYLE),
         eq(fixture.existingStyle.getName()), eq(ResourceAction.READ))).thenReturn(false);

      assertThrows(SecurityException.class, () -> fixture.controller.read("1001", fixture.principal));
   }

   /** {@code getTableStyles}/{@code getTableStyleFolders} only report DIRECT children of the
    * folder passed in -- the controller must walk the tree itself to list everything. */
   @Test
   void list_walksFoldersRecursively() {
      Fixture fixture = new Fixture();
      XTableStyle rootStyle = newStyle("1001", "Simple");
      XTableStyle nestedStyle = newStyle("1002", "User Defined~BorderedBands");
      when(fixture.lib.getTableStyles(isNull(), eq(true))).thenReturn(new XTableStyle[]{ rootStyle });
      when(fixture.lib.getTableStyleFolders(isNull(), eq(true)))
         .thenReturn(new String[]{ "User Defined" });
      when(fixture.lib.getTableStyles(eq("User Defined"), eq(true)))
         .thenReturn(new XTableStyle[]{ nestedStyle });
      when(fixture.lib.getTableStyleFolders(eq("User Defined"), eq(true))).thenReturn(new String[0]);

      List<WizTableStyleController.TableStyleSummary> result = fixture.controller.list(fixture.principal);

      // Sorted by folder first ("" root sorts before "User Defined"), then by name.
      assertEquals(List.of(
         new WizTableStyleController.TableStyleSummary("1001", "Simple", ""),
         new WizTableStyleController.TableStyleSummary("1002", "BorderedBands", "User Defined")
      ), result);
   }

   /**
    * {@code getTableStyles}/{@code getTableStyleFolders} are raw data-access calls with no
    * permission awareness of their own -- unlike {@code ScriptLibraryController.list()}'s
    * equivalent {@code hasPermission} filter, an earlier version of this method returned every
    * style unconditionally, leaking folder/style naming to a caller without READ permission.
    */
   @Test
   void list_excludesAStyleTheCallerLacksReadPermissionOn() throws Exception {
      Fixture fixture = new Fixture();
      XTableStyle visibleStyle = newStyle("1001", "Simple");
      XTableStyle hiddenStyle = newStyle("1002", "Secret");
      when(fixture.lib.getTableStyles(isNull(), eq(true)))
         .thenReturn(new XTableStyle[]{ visibleStyle, hiddenStyle });
      when(fixture.lib.getTableStyleFolders(isNull(), eq(true))).thenReturn(new String[0]);
      when(fixture.securityEngine.checkPermission(any(), eq(ResourceType.TABLE_STYLE),
         eq("Secret"), eq(ResourceAction.READ))).thenReturn(false);

      List<WizTableStyleController.TableStyleSummary> result = fixture.controller.list(fixture.principal);

      assertEquals(List.of(new WizTableStyleController.TableStyleSummary("1001", "Simple", "")), result);
   }

   // ---- Bug #78289: a style stored under an unregistered folder is an invisible orphan ----

   private static void registerFolders(Fixture fixture, String... folders) {
      when(fixture.lib.containsFolder(anyString())).thenReturn(false);

      for(String folder : folders) {
         when(fixture.lib.containsFolder(folder)).thenReturn(true);
      }

      when(fixture.lib.getTableStyleFolders(isNull(), eq(true))).thenReturn(folders.length == 0 ?
         new String[0] : new String[]{ folders[0] });
   }

   @Test
   void create_acceptsAnExistingFolderWithoutTheFlag() throws Exception {
      Fixture fixture = new Fixture();
      registerFolders(fixture, "User Defined", "User Defined~Sales");

      fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("Bands", "User Defined/Sales", null, null),
         fixture.principal);

      verify(fixture.lib, never()).addTableStyleFolder(anyString());
      verify(fixture.lib).setTableStyle(any(), any());
   }

   @Test
   void create_refusesANonexistentFolderNamingItListingExistingOnesAndTheFlag() {
      Fixture fixture = new Fixture();
      registerFolders(fixture, "User Defined");
      when(fixture.lib.getTableStyleFolders(eq("User Defined"), eq(true))).thenReturn(new String[0]);

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("Bands", "NoSuch", null, null),
         fixture.principal));

      String message = thrown.getMessage();
      assertTrue(message.contains("'folder' 'NoSuch' does not exist"), message);
      assertTrue(message.contains("case-sensitive"), message);
      assertTrue(message.contains("User Defined"), message);
      assertTrue(message.contains("createFolders"), message);
      verify(fixture.lib, never()).setTableStyle(any(), any());
      verify(fixture.lib, never()).addTableStyleFolder(anyString());
   }

   @Test
   void create_refusesAFolderWhoseCaseDiffers() {
      Fixture fixture = new Fixture();
      registerFolders(fixture, "Sales");
      when(fixture.lib.getTableStyleFolders(eq("Sales"), eq(true))).thenReturn(new String[0]);

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("Bands", "sales", null, null),
         fixture.principal));

      assertTrue(thrown.getMessage().contains("'sales'"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Sales"), thrown.getMessage());
      verify(fixture.lib, never()).setTableStyle(any(), any());
   }

   @Test
   void create_refusesANestedFolderWhoseParentIsMissingNamingTheMissingSegment() {
      Fixture fixture = new Fixture();
      registerFolders(fixture, "User Defined");
      when(fixture.lib.getTableStyleFolders(eq("User Defined"), eq(true))).thenReturn(new String[0]);

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest(
            "Bands", "User Defined/Missing/Deep", null, null), fixture.principal));

      assertTrue(thrown.getMessage().contains("'User Defined/Missing' is not a registered"),
                 thrown.getMessage());
      verify(fixture.lib, never()).setTableStyle(any(), any());
   }

   @Test
   void create_withCreateFoldersRegistersEveryMissingAncestorOutermostFirstThenStoresTheStyle()
      throws Exception
   {
      Fixture fixture = new Fixture();
      registerFolders(fixture, "User Defined");

      fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest(
            "Bands", "User Defined/New/Deep", null, true), fixture.principal);

      InOrder order = inOrder(fixture.lib);
      order.verify(fixture.lib).addTableStyleFolder("User Defined~New");
      order.verify(fixture.lib).addTableStyleFolder("User Defined~New~Deep");
      order.verify(fixture.lib).setTableStyle(any(), any());
      order.verify(fixture.lib).save();
      verify(fixture.lib, never()).addTableStyleFolder("User Defined");
   }

   @Test
   void create_withCreateFoldersReRegistersAnAuditFolder() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.isAuditStyleFolder("Audit")).thenReturn(true);

      fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("Bands", "Audit", null, true),
         fixture.principal);

      verify(fixture.lib).addTableStyleFolder("Audit");
   }

   @Test
   void create_refusesAnAuditFolderWithoutTheFlag() {
      Fixture fixture = new Fixture();
      when(fixture.lib.isAuditStyleFolder("Audit")).thenReturn(true);

      assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("Bands", "Audit", null, null),
         fixture.principal));

      verify(fixture.lib, never()).setTableStyle(any(), any());
   }

   @Test
   void create_withCreateFoldersButNoWritePermissionOnTheParentGivesADistinctError() throws Exception {
      Fixture fixture = new Fixture();
      registerFolders(fixture, "User Defined");
      when(fixture.securityEngine.checkPermission(any(), eq(ResourceType.TABLE_STYLE),
         eq("User Defined"), eq(ResourceAction.WRITE))).thenReturn(false);

      Exception thrown = assertThrows(SecurityException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest(
            "Bands", "User Defined/New", null, true), fixture.principal));

      assertTrue(thrown.getMessage().contains("No WRITE permission on table style folder"),
                 thrown.getMessage());
      verify(fixture.lib, never()).addTableStyleFolder(anyString());
      verify(fixture.lib, never()).setTableStyle(any(), any());
   }

   @Test
   void update_refusesMovingToANonexistentFolder() {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);
      registerFolders(fixture, "User Defined");
      when(fixture.lib.getTableStyleFolders(eq("User Defined"), eq(true))).thenReturn(new String[0]);

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.update(
         "1001", new WizTableStyleController.UpdateTableStyleRequest(null, "NoSuch", null, null),
         fixture.principal));

      assertTrue(thrown.getMessage().contains("'folder' 'NoSuch' does not exist"), thrown.getMessage());
      verify(fixture.lib, never()).renameTableStyle(any(), any(), any());
      verify(fixture.lib, never()).setTableStyle(any(), any());
   }

   @Test
   void update_withCreateFoldersRegistersTheMissingFolderBeforeTheMove() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);
      registerFolders(fixture, "User Defined");

      fixture.controller.update("1001",
         new WizTableStyleController.UpdateTableStyleRequest(null, "User Defined/New", null, true),
         fixture.principal);

      InOrder order = inOrder(fixture.lib);
      order.verify(fixture.lib).addTableStyleFolder("User Defined~New");
      order.verify(fixture.lib).renameTableStyle(
         "User Defined~BorderedBands", "User Defined~New~BorderedBands", "1001");
   }

   @Test
   void update_aRenameInTheSameFolderNeverChecksFolders() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);
      when(fixture.lib.containsFolder(anyString())).thenReturn(false);

      fixture.controller.update("1001",
         new WizTableStyleController.UpdateTableStyleRequest("Renamed", null, null, null),
         fixture.principal);

      verify(fixture.lib).renameTableStyle("User Defined~BorderedBands", "User Defined~Renamed", "1001");
   }

   // ---- Bug #78541: a trailing-slash folder was stored under an unlistable "Bands~" parent ----

   private WizTableStyleController.TableStyleDetail createIn(Fixture fixture, String folder,
                                                             Boolean createFolders)
      throws Exception
   {
      return fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("X", folder, null, createFolders),
         fixture.principal);
   }

   private static void assertFolderRefused(Executable call, Fixture fixture) {
      Exception thrown = assertThrows(IllegalArgumentException.class, call);
      assertTrue(thrown.getMessage().contains("'folder'"), thrown.getMessage());
      verify(fixture.lib, never()).setTableStyle(any(), any());
      verify(fixture.lib, never()).renameTableStyle(any(), any(), any());
      verify(fixture.lib, never()).addTableStyleFolder(anyString());
   }

   @Test
   void create_stripsATrailingSlashAndStoresUnderTheRealFolder() throws Exception {
      Fixture fixture = new Fixture();
      registerFolders(fixture, "Bands");

      WizTableStyleController.TableStyleDetail detail = createIn(fixture, "Bands/", null);

      assertEquals("Bands", detail.folder());
      verify(fixture.tableStyleService).getTableStyleLabel("Bands", "X");
      verify(fixture.lib).setTableStyle(any(), any());
      verify(fixture.lib, never()).addTableStyleFolder(anyString());
   }

   @Test
   void create_stripsATrailingSlashFromANestedFolder() throws Exception {
      Fixture fixture = new Fixture();
      registerFolders(fixture, "Bands", "Bands~Sub");

      assertEquals("Bands/Sub", createIn(fixture, "Bands/Sub/", null).folder());
      verify(fixture.tableStyleService).getTableStyleLabel("Bands~Sub", "X");
   }

   @Test
   void create_withCreateFoldersRegistersTheNormalizedFolderNotAnOrphan() throws Exception {
      Fixture fixture = new Fixture();
      registerFolders(fixture);

      assertEquals("New", createIn(fixture, "New/", true).folder());

      verify(fixture.lib).addTableStyleFolder("New");
      verify(fixture.lib, never()).addTableStyleFolder("");
      verify(fixture.tableStyleService).getTableStyleLabel("New", "X");
   }

   @Test
   void create_aLoneSlashIsTheLibraryRoot() throws Exception {
      Fixture fixture = new Fixture();

      assertEquals("", createIn(fixture, "/", null).folder());

      verify(fixture.tableStyleService).getTableStyleLabel(null, "X");
      verify(fixture.lib, never()).addTableStyleFolder(anyString());
   }

   @Test
   void create_refusesEmptyInteriorOrLeadingSegmentsWithAndWithoutCreateFolders() {
      for(String folder : new String[] { "/X", "Bands//X", "Bands//", "//" }) {
         for(Boolean createFolders : new Boolean[] { null, true }) {
            Fixture fixture = new Fixture();
            assertFolderRefused(() -> createIn(fixture, folder, createFolders), fixture);
         }
      }
   }

   @Test
   void create_nestedAndBlankFoldersAreUnchanged() throws Exception {
      Fixture fixture = new Fixture();
      registerFolders(fixture, "Bands", "Bands~Sub");

      assertEquals("Bands", createIn(fixture, "Bands", null).folder());
      assertEquals("Bands/Sub", createIn(fixture, "Bands/Sub", null).folder());
      assertEquals("", createIn(fixture, null, null).folder());
      assertEquals("", createIn(fixture, "", null).folder());
   }

   @Test
   void update_stripsATrailingSlashAndMovesIntoTheRealFolder() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);
      registerFolders(fixture, "Bands");

      fixture.controller.update("1001",
         new WizTableStyleController.UpdateTableStyleRequest(null, "Bands/", null, null),
         fixture.principal);

      verify(fixture.lib).renameTableStyle("User Defined~BorderedBands", "Bands~BorderedBands", "1001");
   }

   @Test
   void update_aLoneSlashMovesToTheLibraryRoot() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);

      fixture.controller.update("1001",
         new WizTableStyleController.UpdateTableStyleRequest(null, "/", null, null),
         fixture.principal);

      verify(fixture.lib).renameTableStyle("User Defined~BorderedBands", "BorderedBands", "1001");
   }

   @Test
   void update_refusesEmptySegmentsWithAndWithoutCreateFolders() {
      for(String folder : new String[] { "/X", "Bands//X", "//" }) {
         for(Boolean createFolders : new Boolean[] { null, true }) {
            Fixture fixture = new Fixture();
            when(fixture.lib.getTableStyle("1001")).thenReturn(fixture.existingStyle);
            assertFolderRefused(() -> fixture.controller.update("1001",
               new WizTableStyleController.UpdateTableStyleRequest(null, folder, null, createFolders),
               fixture.principal), fixture);
         }
      }
   }

   private static XTableStyle newStyle(String id, String name) {
      XTableStyle style = new XTableStyle(new DefaultTableLens());
      style.setID(id);
      style.setName(name);
      return style;
   }

   /** The controller with every collaborator mocked, and every permission granted by default. */
   private static final class Fixture {
      Fixture() {
         try {
            // any(), not anyString(), for the style-name argument -- Mockito's anyString()
            // excludes null, and the collision-check tests below never stub
            // TableStyleService.getTableStyleLabel(), so create()'s now-earlier permission check
            // runs against a null styleName before those tests' own stubbed collision is reached.
            // any(ResourceType.class), not eq(TABLE_STYLE) -- update()'s destination-folder check
            // uses TABLE_STYLE_LIBRARY for the library root, a second resource type this default
            // must also cover.
            when(securityEngine.checkPermission(any(), any(ResourceType.class),
                                                nullable(String.class),
                                                any(ResourceAction.class))).thenReturn(true);
         }
         catch(Exception e) {
            // checkPermission's checked SecurityException is a compile-time formality on a mock --
            // stubbing it never actually invokes real logic, so this never really throws.
            throw new RuntimeException(e);
         }

         when(libManagerProvider.getManager(any(Principal.class))).thenReturn(lib);
         // Every folder is registered unless a test says otherwise.
         when(lib.containsFolder(anyString())).thenReturn(true);
         when(lib.getTableStyleFolders(nullable(String.class), anyBoolean())).thenReturn(new String[0]);
         when(tableStyleService.getTableStyleLabel(nullable(String.class), anyString()))
            .thenAnswer(inv -> inv.getArgument(0) == null ? inv.getArgument(1) :
               inv.getArgument(0) + LibManager.SEPARATOR + inv.getArgument(1));
         // IdentityID.getIdentityIDFromKey(null) returns null, and setLastModifiedBy(pId.getName())
         // would NPE on that -- every write path needs a real name here, not Mockito's default null.
         when(principal.getName()).thenReturn("admin");
         controller = new WizTableStyleController(
            libManagerProvider, tableStyleService, securityEngine, previewRenderer);
      }

      final LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      final TableStyleService tableStyleService = mock(TableStyleService.class);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);
      final WizTableStylePreviewRenderer previewRenderer =
         mock(WizTableStylePreviewRenderer.class);
      final LibManager lib = mock(LibManager.class);
      final Principal principal = mock(Principal.class);
      final WizTableStyleController controller;
      final XTableStyle existingStyle = newStyle("1001", "User Defined~BorderedBands");
   }
}
