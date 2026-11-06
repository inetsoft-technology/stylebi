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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import inetsoft.util.AbstractIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The original asset's stored XML stays byte-identical through Create modern copy, with the real
 * Save As, composer save and asset repository doing every write.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernCopyOriginalUnchangedTest {
   private static final String LINK = "http://localhost:8080/sree/";

   @Autowired
   ViewsheetService viewsheetService;

   @BeforeEach
   void setUp() throws Exception {
      // opening the sheet sets the thread's principal, restored in tearDown()
      savedPrincipal = ThreadContext.getPrincipal();
      principal = SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false);
      ThreadContext.setPrincipal(principal);
      repository = AssetUtil.getAssetRepository(false);

      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet legacy = new Viewsheet();
      legacy.getVSAssemblyInfo().setVizMark(null);
      TextVSAssembly text = new TextVSAssembly(legacy, "Text1");
      text.getVSAssemblyInfo().initDefaultFormat();
      legacy.addAssembly(text);
      text.getVSAssemblyInfo().setVizMark(null);
      objectFormat(text.getVSAssemblyInfo()).setForegroundValue("0xff0000");

      name = "ModernCopyOriginal" + System.nanoTime();
      original = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, name,
                                null);
      repository.setSheet(original, legacy, principal, true);
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      before = stored(original);
      assertNotNull(before.bytes(), "the original is stored");
      runtimeId = viewsheetService.openViewsheet(original, principal, false);

      lifecycle = mock(CoreLifecycleService.class);
      saveService = spy(new SaveViewsheetDialogService(
         lifecycle, repository, viewsheetService, new ViewsheetSettingsService(viewsheetService),
         new QueryManagerService(null, null, null, null, null)));
      // a mock MVManager reports no MV, so the save raises no MV confirmation
      composerService = spy(new ComposerViewsheetService(
         null, lifecycle, viewsheetService, null, null, null, null, null, mock(MVManager.class),
         mock(XSessionService.class)));
      service = new ModernCopyService(viewsheetService, lifecycle, repository,
                                      new ModernizeDialogService(viewsheetService, saveService),
                                      saveService, composerService);
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         if(runtimeId != null) {
            viewsheetService.closeViewsheet(runtimeId, principal);
         }

         for(AssetEntry entry : created) {
            if(repository.containsEntry(entry)) {
               repository.removeSheet(entry, principal, true);
            }
         }

         if(repository.containsEntry(original)) {
            repository.removeSheet(original, principal, true);
         }
      }
      finally {
         runtimeId = null;
         SreeEnv.setProperty("viewsheet.modernVisualization", null);
         SreeEnv.setProperty("viewsheet.darkMode", null);
         ThreadContext.setPrincipal(savedPrincipal);
      }
   }

   @Test
   void aCreatedCopyLeavesTheOriginalByteIdentical() throws Exception {
      service.createModernCopy(runtimeId, copyName(), principal, dispatcher(), LINK);

      AssetEntry copy = runtime().getEntry();
      created.add(copy);
      assertNotEquals(original, copy, "the runtime moved to the copy");
      Viewsheet saved = storedSheet(copy);
      assertEquals(original.toIdentifier(), saved.getViewsheetInfo().getModernCopyOf());
      assertFalse(objectFormat(info(saved, "Text1")).isForegroundValueDefined(),
                  "the copy was reset");
      assertOriginalUnchanged();
   }

   @Test
   void unsavedChangesGoIntoTheCopyAndNotTheOriginal() throws Exception {
      addUnsavedChange();

      service.createModernCopy(runtimeId, copyName(), principal, dispatcher(), LINK);

      AssetEntry copy = runtime().getEntry();
      created.add(copy);
      assertNotNull(storedSheet(copy).getAssembly("Unsaved1"), "the copy has the change");
      assertOriginalUnchanged();
   }

   @Test
   void aSaveAsFailureAfterItWroteTheCopyLeavesTheOriginalByteIdentical() throws Exception {
      addUnsavedChange();
      doAnswer(i -> {
         i.callRealMethod();
         created.add(runtime().getEntry());
         throw new IllegalStateException("forced");
      }).when(saveService).saveViewsheet(anyString(), any(), any(), any(), any());

      assertThrows(IllegalStateException.class, () ->
         service.createModernCopy(runtimeId, copyName(), principal, dispatcher(), LINK));

      assertRolledBack();
   }

   @Test
   void aResetFailureLeavesTheOriginalByteIdentical() throws Exception {
      addUnsavedChange();

      try(MockedStatic<VizModernizeUtil> util =
             mockStatic(VizModernizeUtil.class, CALLS_REAL_METHODS))
      {
         util.when(() -> VizModernizeUtil.resetToModern(any()))
            .thenAnswer(i -> {
               created.add(runtime().getEntry());
               throw new IllegalStateException("forced");
            });

         assertThrows(IllegalStateException.class, () ->
            service.createModernCopy(runtimeId, copyName(), principal, dispatcher(), LINK));
      }

      assertRolledBack();
   }

   @Test
   void aSecondSaveFailureAfterItWroteLeavesTheOriginalByteIdentical() throws Exception {
      addUnsavedChange();
      doAnswer(i -> {
         i.callRealMethod();
         created.add(runtime().getEntry());
         throw new IllegalStateException("forced");
      }).when(composerService).saveViewsheet(anyString(), any(), any(), any(), any());

      assertThrows(IllegalStateException.class, () ->
         service.createModernCopy(runtimeId, copyName(), principal, dispatcher(), LINK));

      assertRolledBack();
   }

   @Test
   void aRefreshFailureKeepsTheCopyAndLeavesTheOriginalByteIdentical() throws Exception {
      addUnsavedChange();
      doThrow(new IllegalStateException("forced")).when(lifecycle)
         .refreshViewsheet(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                           any());

      assertThrows(IllegalStateException.class, () ->
         service.createModernCopy(runtimeId, copyName(), principal, dispatcher(), LINK));

      AssetEntry copy = runtime().getEntry();
      created.add(copy);
      assertTrue(repository.containsEntry(copy), "the copy is complete and kept");
      assertOriginalUnchanged();
   }

   private void assertRolledBack() throws Exception {
      assertFalse(created.isEmpty(), "the copy was written before the failure");

      for(AssetEntry copy : created) {
         assertFalse(repository.containsEntry(copy), "the copy is deleted");
      }

      assertEquals(original, runtime().getEntry(), "the runtime is back on the original");
      assertOriginalUnchanged();
   }

   private void assertOriginalUnchanged() throws Exception {
      Stored after = stored(original);
      assertArrayEquals(before.bytes(), after.bytes(), "the original's stored XML");
      assertEquals(before.lastModified(), after.lastModified(), "the original is never rewritten");
   }

   private void addUnsavedChange() throws Exception {
      Viewsheet vs = runtime().getViewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Unsaved1");
      text.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(text);
   }

   private String copyName() {
      return name + " (modern)";
   }

   private RuntimeViewsheet runtime() throws Exception {
      return viewsheetService.getViewsheet(runtimeId, principal);
   }

   private Stored stored(AssetEntry entry) throws Exception {
      IndexedStorage storage = repository.getStorage(entry);
      assertInstanceOf(AbstractIndexedStorage.class, storage);
      String key = entry.toIdentifier();
      return new Stored(((AbstractIndexedStorage) storage).get(key, entry.getOrgID()),
                        storage.lastModified(key, entry.getOrgID()));
   }

   private Viewsheet storedSheet(AssetEntry entry) throws Exception {
      return (Viewsheet) repository.getSheet(entry, principal, false, AssetContent.ALL);
   }

   private static VSAssemblyInfo info(Viewsheet vs, String name) {
      return ((VSAssembly) vs.getAssembly(name)).getVSAssemblyInfo();
   }

   private static VSFormat objectFormat(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH).getUserDefinedFormat();
   }

   private static CommandDispatcher dispatcher() {
      return mock(CommandDispatcher.class);
   }

   private record Stored(byte[] bytes, long lastModified) {
   }

   private Principal principal;
   private Principal savedPrincipal;
   private AssetRepository repository;
   private String name;
   private AssetEntry original;
   private Stored before;
   private String runtimeId;
   private CoreLifecycleService lifecycle;
   private SaveViewsheetDialogService saveService;
   private ComposerViewsheetService composerService;
   private ModernCopyService service;
   private final List<AssetEntry> created = new ArrayList<>();
}
