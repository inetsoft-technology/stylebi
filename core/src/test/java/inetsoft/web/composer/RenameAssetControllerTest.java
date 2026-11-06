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
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.composer.model.RenameAssetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80303, the Composer asset-tree "Rename" action's script branch applied no server-side
 * name check, the same class of defect as the EM rename editor: a hyphenated or reserved-word
 * name reaches LibManager.renameScript, which rewrites the stored function's own declaration
 * with no syntax check.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RenameAssetControllerTest {
   private MockedStatic<Audit> auditStatic;
   private LibManager manager;
   private RenameAssetController controller;
   private Principal principal;

   @BeforeEach
   void setUp() {
      Audit audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      AssetRepository assetRepository = mock(AssetRepository.class);
      RepletRepository repletRepository = mock(RepletRepository.class);
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      manager = mock(LibManager.class);
      LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      when(libManagerProvider.getManager(any(Principal.class))).thenReturn(manager);

      controller = new RenameAssetController(
         assetRepository, repletRepository, viewsheetService, libManagerProvider);
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
   }

   @Test
   void renameToNonIdentifierNameIsRejectedAndNotApplied() throws Exception {
      MessageCommand result = renameScript("oldName", "a-b");

      assertNotNull(result);
      assertEquals(MessageCommand.Type.ERROR, result.getType());
      verify(manager, never()).renameScript(anyString(), anyString());
      verify(manager, never()).save();
   }

   @Test
   void renameToReservedWordIsRejectedAndNotApplied() throws Exception {
      MessageCommand result = renameScript("oldName", "class");

      assertNotNull(result);
      assertEquals(MessageCommand.Type.ERROR, result.getType());
      verify(manager, never()).renameScript(anyString(), anyString());
      verify(manager, never()).save();
   }

   /** Bug #80189: the SCRIPT grant moves with the rename. */
   @Test
   void renameMovesTheScriptsPermissionGrant() throws Exception {
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.getSecurityProvider()).thenReturn(mock(inetsoft.sree.security.SecurityProvider.class));
      Permission grant = new Permission();
      when(security.getPermission(eq(ResourceType.SCRIPT), anyString())).thenReturn(grant);

      try(MockedStatic<SecurityEngine> engine = mockStatic(SecurityEngine.class)) {
         engine.when(SecurityEngine::getSecurity).thenReturn(security);
         renameScript("oldName", "newName");
      }

      verify(manager).renameScript(anyString(), anyString());
      verify(security).setPermission(eq(ResourceType.SCRIPT), anyString(), eq(grant));
      verify(security).removePermission(eq(ResourceType.SCRIPT), anyString());
   }

   /** Bug #80189: a grant-less source must not inherit a stale grant at the new name. */
   @Test
   void renameOfAGrantlessScriptClearsAStaleGrantAtTheNewName() throws Exception {
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.getSecurityProvider()).thenReturn(mock(inetsoft.sree.security.SecurityProvider.class));

      try(MockedStatic<SecurityEngine> engine = mockStatic(SecurityEngine.class)) {
         engine.when(SecurityEngine::getSecurity).thenReturn(security);
         renameScript("oldName", "newName");
      }

      verify(security).removePermission(eq(ResourceType.SCRIPT), anyString());
      verify(security, never()).setPermission(any(), anyString(), any());
   }

   private MessageCommand renameScript(String oldName, String newName) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT,
         "Script Function/" + oldName, null);
      RenameAssetEvent event = new RenameAssetEvent.Builder()
         .entry(entry).newName(newName).confirmed(true).build();

      return controller.renameAsset(event, principal);
   }
}
