/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer.script;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.script.*;
import inetsoft.web.composer.script.service.ScriptService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76933: saveScriptAs must enforce WRITE permission itself instead of relying on the
 * advisory save-script-dialog validation being called first by the client.
 * Bug #76934: saveScript must check WRITE permission against the trusted target name rather
 * than the client-supplied id, which can be crafted to decode to REPORT_SCOPE and bypass the
 * check entirely.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OpenScriptControllerTest {
   @Mock AssetRepository assetRepository;
   @Mock ScriptService scriptService;
   @Mock LibManagerProvider libManagerProvider;
   @Mock LibManager libManager;

   private OpenScriptController controller;
   private SRPrincipal principal;

   @BeforeEach
   void setUp() {
      when(libManagerProvider.getManager(any(java.security.Principal.class))).thenReturn(libManager);
      controller = new OpenScriptController(assetRepository, scriptService, libManagerProvider);
      principal = new SRPrincipal(new IdentityID("user1", Organization.getDefaultOrganizationID()),
         new IdentityID[0], new String[0], Organization.getDefaultOrganizationID(),
         Tool.getSecureRandom().nextLong());
   }

   @Test
   void saveScriptAsDeniedDoesNotWriteLibrary() throws Exception {
      doThrow(new MessageException("denied")).when(assetRepository)
         .checkAssetPermission(eq(principal), any(AssetEntry.class), eq(ResourceAction.WRITE));

      assertThrows(MessageException.class,
         () -> controller.saveScriptAs(createRequest("sharedFn", AssetRepository.COMPONENT_SCOPE), principal));

      verify(libManager, never()).setScript(anyString(), anyString());
      verify(libManager, never()).setScriptComment(anyString(), anyString());
      verify(libManager, never()).save();
   }

   @Test
   void saveScriptAsAllowedWritesLibrary() throws Exception {
      controller.saveScriptAs(createRequest("sharedFn", AssetRepository.COMPONENT_SCOPE), principal);

      verify(libManager).setScript("sharedFn", "function sharedFn() {}");
      verify(libManager).save();
   }

   @Test
   void saveScriptAsChecksTargetScriptIgnoringClientScope() throws Exception {
      // REPORT_SCOPE entries pass checkAssetPermission unconditionally, so the checked entry
      // must not inherit the scope from the client-supplied identifier
      controller.saveScriptAs(createRequest("sharedFn", AssetRepository.REPORT_SCOPE), principal);

      ArgumentCaptor<AssetEntry> captor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(assetRepository).checkAssetPermission(eq(principal), captor.capture(),
         eq(ResourceAction.WRITE));
      AssetEntry checked = captor.getValue();
      assertEquals(AssetRepository.COMPONENT_SCOPE, checked.getScope());
      assertEquals(AssetEntry.Type.SCRIPT, checked.getType());
      assertEquals("sharedFn", checked.getName());
   }

   @Test
   void saveScriptChecksTargetScriptIgnoringClientScope() throws Exception {
      // REPORT_SCOPE entries pass checkAssetPermission unconditionally, so the checked entry
      // must be built from the trusted target name (the label), not the client-supplied id
      when(libManager.getScript("sharedFn")).thenReturn("old text");

      ScriptModel scriptModel = new ScriptModel();
      scriptModel.setId(new AssetEntry(AssetRepository.REPORT_SCOPE, AssetEntry.Type.SCRIPT,
         "sharedFn", null).toIdentifier());
      scriptModel.setLabel("sharedFn");
      scriptModel.setText("new text");

      controller.saveScript(scriptModel, principal);

      ArgumentCaptor<AssetEntry> captor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(assetRepository).checkAssetPermission(eq(principal), captor.capture(),
         eq(ResourceAction.WRITE));
      AssetEntry checked = captor.getValue();
      assertEquals(AssetRepository.COMPONENT_SCOPE, checked.getScope());
      assertEquals(AssetEntry.Type.SCRIPT, checked.getType());
      assertEquals("sharedFn", checked.getName());
   }

   @Test
   void saveScriptDeniedWithReportScopeIdDoesNotWriteLibrary() throws Exception {
      // Regression test for the REPORT_SCOPE bypass: a crafted id that decodes to REPORT_SCOPE
      // must not let the write through even though checkAssetPermission0 would otherwise pass it
      when(libManager.getScript("sharedFn")).thenReturn("old text");
      doThrow(new MessageException("denied")).when(assetRepository)
         .checkAssetPermission(eq(principal), any(AssetEntry.class), eq(ResourceAction.WRITE));

      ScriptModel scriptModel = new ScriptModel();
      scriptModel.setId(new AssetEntry(AssetRepository.REPORT_SCOPE, AssetEntry.Type.SCRIPT,
         "sharedFn", null).toIdentifier());
      scriptModel.setLabel("sharedFn");
      scriptModel.setText("new text");

      String result = controller.saveScript(scriptModel, principal);

      assertFalse(result.isEmpty());
      verify(libManager, never()).setScript(anyString(), anyString());
      verify(libManager, never()).save();
   }

   @Test
   void saveScriptDeniedCommentOnlyEditDoesNotTouchLibrary() throws Exception {
      when(libManager.getScript("sharedFn")).thenReturn("function sharedFn() {}");
      when(libManager.getScriptComment("sharedFn")).thenReturn("old comment");
      doThrow(new MessageException("denied")).when(assetRepository)
         .checkAssetPermission(eq(principal), any(AssetEntry.class), eq(ResourceAction.WRITE));

      ScriptModel scriptModel = new ScriptModel();
      scriptModel.setId(new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT,
         "sharedFn", null).toIdentifier());
      scriptModel.setLabel("sharedFn");
      scriptModel.setText("function sharedFn() {}");
      scriptModel.setComment("new comment");

      String result = controller.saveScript(scriptModel, principal);

      assertFalse(result.isEmpty());
      verify(libManager, never()).setScriptComment(anyString(), anyString());
      verify(libManager, never()).setScript(anyString(), anyString());
      verify(libManager, never()).save();
   }

   private ScriptRequestModel createRequest(String name, int parentScope) {
      AssetEntry parent = new AssetEntry(parentScope, AssetEntry.Type.SCRIPT_FOLDER, "/Script", null);
      SaveScriptDialogModel saveModel = new SaveScriptDialogModel();
      saveModel.setName(name);
      saveModel.setIdentifier(parent.toIdentifier());

      ScriptModel scriptModel = new ScriptModel();
      scriptModel.setText("function " + name + "() {}");

      ScriptRequestModel request = new ScriptRequestModel();
      request.setScriptModel(scriptModel);
      request.setSaveModel(saveModel);
      return request;
   }
}
