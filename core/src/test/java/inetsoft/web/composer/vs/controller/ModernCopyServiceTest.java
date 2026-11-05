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

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.MessageException;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.ModernizeDialogService;
import inetsoft.web.composer.vs.dialog.SaveViewsheetDialogService;
import inetsoft.web.composer.ws.event.SaveSheetEvent;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class ModernCopyServiceTest {
   @BeforeEach
   void setup() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      autoSave = mockStatic(AutoSaveUtils.class);
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setVizMark(null);
      currentSheet = viewsheet;
      addText("Text1");
      addText("Text2");

      original = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                "Folder/Sales", null);
      original.setAlias("Sales Dashboard");
      currentEntry = original;
      current = 3;
      savePoint = 2;
      editable = true;
      startEditable = true;
      composerSavedOriginal = false;

      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getID()).thenReturn("rid");
      when(rvs.getEntry()).thenAnswer(i -> currentEntry);
      doAnswer(i -> currentEntry = i.getArgument(0)).when(rvs).setEntry(any());
      when(rvs.getViewsheet()).thenAnswer(i -> currentSheet);
      when(rvs.getSheet()).thenAnswer(i -> currentSheet);
      doAnswer(i -> currentSheet = i.getArgument(0)).when(rvs).setViewsheet(any());
      when(rvs.getSavePoint()).thenAnswer(i -> savePoint);
      doAnswer(i -> savePoint = i.<Integer>getArgument(0)).when(rvs).setSavePoint(anyInt());
      when(rvs.getCurrent()).thenAnswer(i -> current);
      when(rvs.size()).thenAnswer(i -> current + 1);
      when(rvs.addCheckpoint(any())).thenAnswer(i -> ++current);
      when(rvs.isEditable()).thenAnswer(i -> editable);
      doAnswer(i -> editable = i.<Boolean>getArgument(0)).when(rvs).setEditable(anyBoolean());

      dialogService = spy(new ModernizeDialogService(viewsheetEngine, saveDialogService));
      when(saveDialogService.validateSaveViewSheet(anyString(), any(), nullable(Principal.class)))
         .thenReturn(SaveViewsheetDialogModelValidator.builder().permissionDenied("").build());
      when(saveDialogService.getSaveViewsheetInfo(anyString(), nullable(Principal.class)))
         .thenAnswer(i -> {
            SaveViewsheetDialogModel model = new SaveViewsheetDialogModel();
            model.setName("Sales");
            model.setParentId("stale");
            model.setUpdateDepend(true);
            model.getViewsheetOptionsPaneModel().setAlias(original.getAlias());
            return model;
         });
      // Save As moves the runtime to the new entry, saves it and tells the client
      doAnswer(i -> {
         SaveViewsheetDialogModel model = i.getArgument(1);
         editable = true;
         AssetEntry parent = AssetEntry.createAssetEntry(model.getParentId());
         currentEntry = new AssetEntry(parent.getScope(), AssetEntry.Type.VIEWSHEET,
                                       parent.getPath() + "/" + model.getName(), null);
         currentEntry.setAlias(model.getViewsheetOptionsPaneModel().getAlias());
         savePoint = current;
         CommandDispatcher saveDispatcher = i.getArgument(4);
         saveDispatcher.sendCommand(new SetViewsheetInfoCommand());
         saveDispatcher.sendCommand(SaveSheetCommand.builder()
                                       .savePoint(savePoint).id(currentEntry.toIdentifier()).build());
         return null;
      }).when(saveDialogService).saveViewsheet(anyString(), any(), any(), any(), any());
      when(composerService.saveViewsheet(anyString(), any(), any(), any(), any()))
         .thenAnswer(i -> {
            composerSavedOriginal |= original.equals(currentEntry);
            savedMark = currentSheet.getVSAssemblyInfo().getVizMark();
            savedModernCopyOf = currentSheet.getViewsheetInfo().getModernCopyOf();
            savePoint = current;
            CommandDispatcher saveDispatcher = i.getArgument(3);
            saveDispatcher.sendCommand("", new SetViewsheetInfoCommand());
            saveDispatcher.sendCommand("", SaveSheetCommand.builder()
               .savePoint(savePoint).id(currentEntry.toIdentifier()).build());
            return true;
         });

      service = new ModernCopyService(viewsheetEngine, coreLifecycleService, assetRepository,
                                      dialogService, saveDialogService, composerService);
   }

   @AfterEach
   void reset() {
      autoSave.close();
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void createsTheCopyInOrder() throws Exception {
      service.createModernCopy("rid", "  Sales (modern) ", principal, dispatcher, "uri");

      InOrder order = inOrder(dialogService, saveDialogService, assetRepository, composerService,
                              coreLifecycleService);
      order.verify(dialogService).checkName(
         eq("rid"), argThat(r -> "Sales (modern)".equals(r.getName())), eq(principal));
      ArgumentCaptor<SaveViewsheetDialogModel> model =
         ArgumentCaptor.forClass(SaveViewsheetDialogModel.class);
      order.verify(saveDialogService).saveViewsheet(eq("rid"), model.capture(), eq("uri"),
                                                    eq(principal), any(CommandDispatcher.class));
      order.verify(composerService).saveViewsheet(eq("rid"), any(SaveSheetEvent.class),
                                                  eq(principal), any(CommandDispatcher.class),
                                                  eq("uri"));
      order.verify(coreLifecycleService).refreshViewsheet(
         eq(rvs), eq("rid"), eq("uri"), eq(dispatcher), eq(false), eq(false), eq(true), any());

      assertEquals("Sales (modern)", model.getValue().getName());
      assertEquals(original.getParent().toIdentifier(), model.getValue().getParentId());
      assertFalse(model.getValue().isUpdateDepend());

      AssetEntry copy = currentEntry;
      assertEquals("Folder/Sales (modern)", copy.getPath());
      assertSame(viewsheet, currentSheet);
      assertEquals(VizMark.MODERN_LIGHT, viewsheet.getVSAssemblyInfo().getVizMark());
      assertEquals(VizMark.MODERN_LIGHT, info("Text1").getVizMark());
      assertEquals(original.toIdentifier(), viewsheet.getViewsheetInfo().getModernCopyOf());
      assertEquals(VizMark.MODERN_LIGHT, savedMark, "the second save writes the reset");
      assertEquals(original.toIdentifier(), savedModernCopyOf);

      MessageCommand message = lastMessage();
      assertEquals(MessageCommand.Type.OK, message.getType());
      assertEquals("Created Sales (modern).", message.getMessage());
      autoSave.verify(() -> AutoSaveUtils.deleteAutoSaveFile(original, principal));
      verify(assetRepository, never()).removeSheet(any(), any(), anyBoolean());
      assertOriginalNeverWritten();
   }

   @Test
   void onlyOneSaveCommandReachesTheClientAfterTheCopyIsDone() throws Exception {
      service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri");

      List<SaveSheetCommand> saves = commands(SaveSheetCommand.class);
      assertEquals(1, saves.size(), "the saves' own save commands are withheld");
      assertEquals(currentEntry.toIdentifier(), saves.getFirst().id());
      assertEquals(4, saves.getFirst().savePoint());
      assertEquals(2, commands(SetViewsheetInfoCommand.class).size(),
                   "the saves' other commands pass through");

      InOrder order = inOrder(dispatcher);
      order.verify(dispatcher).sendCommand(isA(UpdateUndoStateCommand.class));
      order.verify(dispatcher).sendCommand(isA(SaveSheetCommand.class));
      order.verify(dispatcher).sendCommand(isA(MessageCommand.class));
   }

   @Test
   void theCopyListsUnderItsTypedName() throws Exception {
      service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri");

      ArgumentCaptor<SaveViewsheetDialogModel> model =
         ArgumentCaptor.forClass(SaveViewsheetDialogModel.class);
      verify(saveDialogService).saveViewsheet(anyString(), model.capture(), any(), any(), any());
      assertNull(model.getValue().getViewsheetOptionsPaneModel().getAlias());
      assertNotEquals(original.getAlias(), currentEntry.getAlias());
      assertEquals("Sales Dashboard", original.getAlias());
   }

   @Test
   void aRefreshFailureKeepsTheCopyAndTellsTheClient() throws Exception {
      IllegalStateException failure = new IllegalStateException("refresh");
      doThrow(failure).when(coreLifecycleService).refreshViewsheet(
         any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());

      assertSame(failure, assertThrows(IllegalStateException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri")));

      verify(assetRepository, never()).removeSheet(any(), any(), anyBoolean());
      assertEquals("Folder/Sales (modern)", currentEntry.getPath());
      List<SaveSheetCommand> saves = commands(SaveSheetCommand.class);
      assertEquals(1, saves.size());
      assertEquals(currentEntry.toIdentifier(), saves.getFirst().id());
      assertTrue(commands(MessageCommand.class).isEmpty());
      assertOriginalNeverWritten();
   }

   @Test
   void theCopyKeepsNoBookmarksOfTheOriginal() throws Exception {
      service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri");

      InOrder order = inOrder(saveDialogService, assetRepository, composerService);
      order.verify(saveDialogService).saveViewsheet(anyString(), any(), any(), any(), any());
      order.verify(assetRepository).clearVSBookmark(currentEntry);
      order.verify(composerService).saveViewsheet(anyString(), any(), any(), any(), any());
      assertOriginalNeverWritten();
   }

   @Test
   void theResetIsOneUndoStepAndTheCopysSavePoint() throws Exception {
      service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri");

      verify(rvs, times(1)).addCheckpoint(any(AbstractSheet.class));
      assertEquals(4, current);
      assertEquals(current, savePoint, "the copy is clean right after it is created");

      UpdateUndoStateCommand undo = commands(UpdateUndoStateCommand.class).getLast();
      assertEquals(4, undo.getCurrent());
      assertEquals(4, undo.getSavePoint());
      assertEquals(5, undo.getPoints());
   }

   @Test
   void aScriptWarningTurnsTheMessageIntoInfo() throws Exception {
      VSAssemblyInfo text2 = info("Text2");
      text2.setScript("Text1.foreground = [255,0,0];");
      text2.setScriptEnabled(true);

      service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri");

      MessageCommand message = lastMessage();
      assertEquals(MessageCommand.Type.INFO, message.getType());
      assertEquals("Created Sales (modern). Scripts set formatting on: Text1. The reset does " +
                      "not change scripts, so they can still override the new defaults.",
                   message.getMessage());
   }

   @Test
   void aDashboardScriptWithoutATargetStillWarns() throws Exception {
      viewsheet.getViewsheetInfo().setOnInit("Missing1.foreground = [255,0,0];");

      service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri");

      MessageCommand message = lastMessage();
      assertEquals(MessageCommand.Type.INFO, message.getType());
      assertEquals("Created Sales (modern). The dashboard script sets formatting. The reset " +
                      "does not change scripts, so a script that sets formatting can still " +
                      "override the new defaults.", message.getMessage());
   }

   @Test
   void aTakenNameIsRefusedBeforeAnySave() throws Exception {
      when(saveDialogService.validateSaveViewSheet(anyString(), any(), nullable(Principal.class)))
         .thenReturn(SaveViewsheetDialogModelValidator.builder()
                        .alreadyExists("exists").permissionDenied("").build());

      MessageException ex = assertThrows(MessageException.class, () ->
         service.createModernCopy("rid", "Sales", principal, dispatcher, "uri"));

      assertEquals("A dashboard with this name already exists.", ex.getMessage());
      assertNothingSaved();
   }

   @Test
   void aFolderWithoutWritePermissionIsRefused() throws Exception {
      when(saveDialogService.validateSaveViewSheet(anyString(), any(), nullable(Principal.class)))
         .thenReturn(SaveViewsheetDialogModelValidator.builder()
                        .permissionDenied("denied").build());

      MessageException ex = assertThrows(MessageException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri"));

      assertEquals("denied", ex.getMessage());
      assertNothingSaved();
   }

   @Test
   void aNameHeldByAnotherRuntimeChangesNothing() throws Exception {
      startEditable = editable = false;
      // Save As marks the runtime editable before its duplicate check
      doAnswer(i -> {
         editable = true;
         throw new MessageException("overwriteForbidden");
      }).when(saveDialogService).saveViewsheet(anyString(), any(), any(), any(), any());

      assertThrows(MessageException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri"));

      verify(assetRepository, never()).removeSheet(any(), any(), anyBoolean());
      verify(composerService, never()).saveViewsheet(anyString(), any(), any(), any(), any());
      assertFalse(editable, "the runtime stays read-only on the original");
      assertSame(original, currentEntry);
      assertSame(viewsheet, currentSheet);
      assertNull(info("Text1").getVizMark(), "the reset never ran");
      assertTrue(commands(MessageCommand.class).isEmpty());
      autoSave.verifyNoInteractions();
      assertOriginalNeverWritten();
   }

   @Test
   void aFailureAfterTheFirstSaveDeletesTheCopy() throws Exception {
      RuntimeException failure = new RuntimeException("disk full");
      doThrow(failure).when(composerService)
         .saveViewsheet(anyString(), any(), any(), any(), any());

      RuntimeException thrown = assertThrows(RuntimeException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri"));

      assertSame(failure, thrown);
      AssetEntry copy = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                       "Folder/Sales (modern)", null);
      verify(assetRepository).removeSheet(copy, principal, true);
      assertRestored();
   }

   @Test
   void aFailedDeleteStillRestoresTheRuntime() throws Exception {
      RuntimeException failure = new RuntimeException("disk full");
      doThrow(failure).when(composerService)
         .saveViewsheet(anyString(), any(), any(), any(), any());
      doThrow(new SecurityException("no delete"))
         .when(assetRepository).removeSheet(any(), any(), anyBoolean());

      RuntimeException thrown = assertThrows(RuntimeException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri"));

      assertSame(failure, thrown);
      assertRestored();
   }

   @Test
   void aFailureBeforeTheSecondSaveDeletesTheCopy() throws Exception {
      doThrow(new IllegalStateException("bookmarks"))
         .when(assetRepository).clearVSBookmark(any());

      assertThrows(IllegalStateException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri"));

      verify(assetRepository).removeSheet(argThat(e -> !original.equals(e)), eq(principal),
                                          eq(true));
      verify(composerService, never()).saveViewsheet(anyString(), any(), any(), any(), any());
      assertRestored();
   }

   @Test
   void aFailureOnAnotherUsersLockedOriginalLeavesTheRuntimeReadOnly() throws Exception {
      startEditable = editable = false;
      doThrow(new RuntimeException("disk full")).when(composerService)
         .saveViewsheet(anyString(), any(), any(), any(), any());

      assertThrows(RuntimeException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri"));

      verify(assetRepository).removeSheet(argThat(e -> !original.equals(e)), eq(principal),
                                          eq(true));
      assertRestored();
   }

   @Test
   void aSaveAsFailureAfterItSavedTheCopyDeletesIt() throws Exception {
      IllegalStateException failure = new IllegalStateException("info");
      // Save As moves the runtime after writing the copy, then sends the info
      doAnswer(i -> {
         editable = true;
         currentEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                       "Folder/Sales (modern)", null);
         savePoint = current;
         throw failure;
      }).when(saveDialogService).saveViewsheet(anyString(), any(), any(), any(), any());

      assertSame(failure, assertThrows(IllegalStateException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri")));

      AssetEntry copy = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                       "Folder/Sales (modern)", null);
      verify(assetRepository).removeSheet(copy, principal, true);
      verify(composerService, never()).saveViewsheet(anyString(), any(), any(), any(), any());
      assertRestored();
   }

   @Test
   void aSheetThatCannotBeClonedIsRefused() throws Exception {
      Viewsheet uncloneable = spy(viewsheet);
      doReturn(null).when(uncloneable).clone();
      currentSheet = uncloneable;

      MessageException ex = assertThrows(MessageException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri"));

      assertEquals("An unexpected error has occurred.", ex.getMessage());
      verify(saveDialogService, never()).getSaveViewsheetInfo(anyString(), any());
      verify(saveDialogService, never()).saveViewsheet(anyString(), any(), any(), any(), any());
      verify(composerService, never()).saveViewsheet(anyString(), any(), any(), any(), any());
      verify(assetRepository, never()).removeSheet(any(), any(), anyBoolean());
      assertSame(original, currentEntry);
      assertSame(uncloneable, currentSheet);
      assertNull(info("Text1").getVizMark(), "the reset never ran");
      autoSave.verifyNoInteractions();
   }

   @Test
   void aSaveAsThatLeavesTheOriginalStopsBeforeAnyWrite() throws Exception {
      doNothing().when(saveDialogService).saveViewsheet(anyString(), any(), any(), any(), any());

      assertThrows(IllegalStateException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri"));

      verify(composerService, never()).saveViewsheet(anyString(), any(), any(), any(), any());
      verify(assetRepository, never()).removeSheet(any(), any(), anyBoolean());
      assertNull(info("Text1").getVizMark(), "the reset never ran");
      assertOriginalNeverWritten();
   }

   @Test
   void bannedCharactersAreRefusedServerSide() throws Exception {
      MessageException ex = assertThrows(MessageException.class, () ->
         service.createModernCopy("rid", "a/b", principal, dispatcher, "uri"));
      assertNotNull(ex.getMessage());
      assertNothingSaved();

      assertThrows(MessageException.class, () ->
         service.createModernCopy("rid", " \u0007 ", principal, dispatcher, "uri"));
      assertThrows(MessageException.class, () ->
         service.createModernCopy("rid", null, principal, dispatcher, "uri"));
      assertNothingSaved();
   }

   @Test
   void controlCharactersAreDroppedBeforeTheNameIsChecked() throws Exception {
      service.createModernCopy("rid", "Sales\u0007 (modern)", principal, dispatcher, "uri");

      verify(dialogService).checkName(
         eq("rid"), argThat(r -> "Sales (modern)".equals(r.getName())), eq(principal));
      assertEquals("Folder/Sales (modern)", currentEntry.getPath());
   }

   @Test
   void refusedWithTheGateOffOrANeverSavedSheet() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertThrows(MessageException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri"));
      assertNothingSaved();

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      currentEntry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE, AssetEntry.Type.VIEWSHEET,
                                    "Untitled", null);
      MessageException ex = assertThrows(MessageException.class, () ->
         service.createModernCopy("rid", "Sales (modern)", principal, dispatcher, "uri"));
      assertEquals("Save the dashboard first.", ex.getMessage());
      assertNothingSaved();
   }

   private void addText(String name) {
      viewsheet.addAssembly(new TextVSAssembly(viewsheet, name));
      info(name).setVizMark(null);
   }

   private VSAssemblyInfo info(String name) {
      return ((VSAssembly) currentSheet.getAssembly(name)).getVSAssemblyInfo();
   }

   private <T> List<T> commands(Class<T> type) {
      ArgumentCaptor<ViewsheetCommand> captor = ArgumentCaptor.forClass(ViewsheetCommand.class);
      verify(dispatcher, atLeast(0)).sendCommand(captor.capture());
      ArgumentCaptor<ViewsheetCommand> named = ArgumentCaptor.forClass(ViewsheetCommand.class);
      verify(dispatcher, atLeast(0)).sendCommand(anyString(), named.capture());
      List<ViewsheetCommand> all = new ArrayList<>(captor.getAllValues());
      all.addAll(named.getAllValues());
      return all.stream().filter(type::isInstance).map(type::cast).toList();
   }

   private MessageCommand lastMessage() {
      List<MessageCommand> messages = commands(MessageCommand.class);
      assertFalse(messages.isEmpty(), "a notification is sent");
      return messages.getLast();
   }

   private void assertNothingSaved() throws Exception {
      verify(saveDialogService, never()).saveViewsheet(anyString(), any(), any(), any(), any());
      verify(composerService, never()).saveViewsheet(anyString(), any(), any(), any(), any());
      verify(assetRepository, never()).removeSheet(any(), any(), anyBoolean());
      assertSame(viewsheet, currentSheet);
      assertNull(info("Text1").getVizMark(), "the reset never ran");
      autoSave.verifyNoInteractions();
   }

   private void assertRestored() throws Exception {
      assertSame(original, currentEntry, "the runtime points at the original again");
      assertNotSame(viewsheet, currentSheet, "the pre-reset sheet is back");
      assertNull(currentSheet.getVSAssemblyInfo().getVizMark());
      assertNull(info("Text1").getVizMark());
      assertNull(currentSheet.getViewsheetInfo().getModernCopyOf());
      assertEquals(2, savePoint);
      assertEquals(startEditable, editable, "Save As's editable flag is undone");
      assertEquals(3, current, "no undo step is added");

      InOrder order = inOrder(rvs, coreLifecycleService);
      order.verify(rvs).setEntry(original);
      order.verify(rvs).setViewsheet(any(Viewsheet.class));
      order.verify(coreLifecycleService).setViewsheetInfo(eq(rvs), eq("uri"), eq(dispatcher));

      assertTrue(commands(SaveSheetCommand.class).isEmpty(),
                 "the client never learned of the copy");
      assertTrue(commands(MessageCommand.class).isEmpty());
      verify(rvs, never()).addCheckpoint(any());
      autoSave.verify(() -> AutoSaveUtils.deleteAutoSaveFile(any(AssetEntry.class), any()),
                      never());
      assertOriginalNeverWritten();
   }

   private void assertOriginalNeverWritten() throws Exception {
      assertFalse(composerSavedOriginal, "the composer save never targets the original");
      verify(assetRepository, never()).removeSheet(eq(original), any(), anyBoolean());
      verify(assetRepository, never()).clearVSBookmark(eq(original));
   }

   private ModernCopyService service;
   private ModernizeDialogService dialogService;
   private MockedStatic<AutoSaveUtils> autoSave;
   private Viewsheet viewsheet;
   private AssetEntry original;
   private AssetEntry currentEntry;
   private Viewsheet currentSheet;
   private int current;
   private int savePoint;
   private boolean editable;
   private boolean startEditable;
   private boolean composerSavedOriginal;
   private VizMark savedMark;
   private String savedModernCopyOf;
   private final Principal principal = mock(Principal.class);
   @Mock private ViewsheetEngine viewsheetEngine;
   @Mock private CoreLifecycleService coreLifecycleService;
   @Mock private AssetRepository assetRepository;
   @Mock private SaveViewsheetDialogService saveDialogService;
   @Mock private ComposerViewsheetService composerService;
   @Mock private RuntimeViewsheet rvs;
   @Mock private CommandDispatcher dispatcher;
}
