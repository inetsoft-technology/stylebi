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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
         new WizTableStyleController.CreateTableStyleRequest("BorderedBands", null, null),
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
            "BorderedBands", "User Defined/Sales", null),
         fixture.principal));

      verify(fixture.tableStyleService).contains("User Defined~Sales", "BorderedBands");
   }

   @Test
   void create_requiresANonBlankName() {
      Fixture fixture = new Fixture();

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("   ", null, null), fixture.principal));

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
         new WizTableStyleController.CreateTableStyleRequest("Foo~Bar", null, null), fixture.principal));

      assertTrue(thrown.getMessage().contains("'name' must not contain"));
      verifyNoInteractions(fixture.lib);
   }

   @Test
   void create_refusesAFolderContainingLibManagersInternalSeparator() {
      Fixture fixture = new Fixture();

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> fixture.controller.create(
         new WizTableStyleController.CreateTableStyleRequest("BorderedBands", "User Defined~Sales", null),
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
         "1001", new WizTableStyleController.UpdateTableStyleRequest(incomplete), fixture.principal));

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
         "1001", new WizTableStyleController.UpdateTableStyleRequest(null), fixture.principal);

      assertEquals(originalModified, fixture.existingStyle.getLastModified(),
                   "the shared cached instance must be untouched");
      verify(fixture.lib).setTableStyle(eq("1001"), argThat(style -> style != fixture.existingStyle));
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
            when(securityEngine.checkPermission(any(), eq(ResourceType.TABLE_STYLE),
                                                nullable(String.class),
                                                any(ResourceAction.class))).thenReturn(true);
         }
         catch(Exception e) {
            // checkPermission's checked SecurityException is a compile-time formality on a mock --
            // stubbing it never actually invokes real logic, so this never really throws.
            throw new RuntimeException(e);
         }

         when(libManagerProvider.getManager(any(Principal.class))).thenReturn(lib);
         // IdentityID.getIdentityIDFromKey(null) returns null, and setLastModifiedBy(pId.getName())
         // would NPE on that -- every write path needs a real name here, not Mockito's default null.
         when(principal.getName()).thenReturn("admin");
         controller = new WizTableStyleController(libManagerProvider, tableStyleService, securityEngine);
      }

      final LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      final TableStyleService tableStyleService = mock(TableStyleService.class);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);
      final LibManager lib = mock(LibManager.class);
      final Principal principal = mock(Principal.class);
      final WizTableStyleController controller;
      final XTableStyle existingStyle = newStyle("1001", "User Defined~BorderedBands");
   }
}
