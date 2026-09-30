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
package inetsoft.web.composer.vs;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.util.MessageException;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.controller.*;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.composer.vs.event.NewViewsheetEvent;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.controller.VSWizardDialogController;
import inetsoft.web.vswizard.controller.VSWizardDialogServiceProxy;
import inetsoft.web.vswizard.event.OpenVsWizardEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77400: the endpoints that newly bind a client-supplied entry as the base source of a
 * viewsheet must check that source, because {@link Viewsheet#update} builds the base worksheet
 * from the entry without a permission check. An unchanged base source is not checked, so an
 * existing viewsheet keeps working. Labels:
 * <ul>
 *    <li>H QueryManagerService.checkViewsheetBaseEntryPermission</li>
 *    <li>N STOMP composer/viewsheet/new</li>
 *    <li>W STOMP /vswizard/dialog/open</li>
 *    <li>P STOMP /composer/vs/viewsheet-property-dialog-model</li>
 *    <li>S STOMP /composer/vs/save-viewsheet-dialog-model (Save-As)</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetBaseSourcePermissionTest {
   private static final String ALLOWED = "DS_OK";
   private static final String DENIED = "DS1";
   private static final String MODEL = "LM";
   private static final String MODEL_FOLDER = "F";
   private static final String RUNTIME_ID = "vs1";

   private SecurityEngine securityEngine;
   private AssetRepository assetRepository;
   private ViewsheetService viewsheetService;
   private QueryManagerService queryManager;
   private RuntimeViewsheet rvs;
   private Viewsheet viewsheet;
   private final Principal principal =
      new SRPrincipal(new IdentityID("bob", Organization.getDefaultOrganizationID()));

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      XRepository repository = mock(XRepository.class);
      assetRepository = mock(AssetRepository.class);
      queryManager = new QueryManagerService(
         mock(RuntimeQueryService.class), repository, mock(DataSourceService.class),
         securityEngine, mock(ColumnCache.class));
      viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getAssetRepository()).thenReturn(assetRepository);
      rvs = mock(RuntimeViewsheet.class);
      viewsheet = mock(Viewsheet.class);
      when(viewsheetService.getViewsheet(RUNTIME_ID, principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getViewsheetInfo()).thenReturn(new ViewsheetInfo());

      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      grantRead(ALLOWED);
      grantPhysicalAccess(true);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_MODEL_FOLDER), anyString(), eq(ResourceAction.READ)))
         .thenReturn(true);
      grantModelRead(true);

      XDataModel dataModel = mock(XDataModel.class);
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(logicalModel.getFolder()).thenReturn(MODEL_FOLDER);
      when(dataModel.getLogicalModel(MODEL)).thenReturn(logicalModel);
      when(repository.getDataModel(ALLOWED)).thenReturn(dataModel);
   }

   private void grantRead(String... names) throws Exception {
      Set<String> readable = Set.of(names);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), nullable(String.class), eq(ResourceAction.READ)))
         .thenAnswer(inv -> readable.contains(inv.<String>getArgument(2)));
   }

   private void grantPhysicalAccess(boolean allowed) throws Exception {
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.PHYSICAL_TABLE), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(allowed);
   }

   private void grantModelRead(boolean allowed) throws Exception {
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.QUERY), anyString(), eq(ResourceAction.READ)))
         .thenReturn(allowed);
   }

   private void verifyNoSourceChecked() throws Exception {
      verify(securityEngine, never()).checkPermission(
         any(), eq(ResourceType.DATA_SOURCE), nullable(String.class), any(ResourceAction.class));
      verify(securityEngine, never()).checkPermission(
         any(), eq(ResourceType.QUERY), nullable(String.class), any(ResourceAction.class));
      verify(assetRepository, never()).checkAssetPermission(any(), any(), any());
   }

   private static AssetEntry modelEntry(String prefix) {
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL, ALLOWED + "/" + MODEL, null);
      entry.setProperty("prefix", prefix);
      entry.setProperty("source", MODEL);
      entry.setProperty("type", SourceInfo.MODEL + "");
      return entry;
   }

   private static AssetEntry worksheetEntry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "WS1", null);
   }

   private void denyWorksheet(AssetEntry entry) throws Exception {
      doThrow(new MessageException("denied")).when(assetRepository)
         .checkAssetPermission(principal, entry, ResourceAction.READ);
   }

   // ---- H: shared base entry check ----

   @Test
   void hNullEntryClearsWithoutCheck() throws Exception {
      queryManager.checkViewsheetBaseEntryPermission(null, assetRepository, principal);
      verifyNoSourceChecked();
   }

   @Test
   void hModelDeniedWithoutDataSourceRead() throws Exception {
      assertThrows(java.lang.SecurityException.class, () -> queryManager
         .checkViewsheetBaseEntryPermission(modelEntry(DENIED), assetRepository, principal));
   }

   @Test
   void hModelDeniedWithoutModelReadOnStoredResource() throws Exception {
      grantModelRead(false);

      assertThrows(java.lang.SecurityException.class, () -> queryManager
         .checkViewsheetBaseEntryPermission(modelEntry(ALLOWED), assetRepository, principal));
      verify(securityEngine).checkPermission(
         principal, ResourceType.QUERY, MODEL + "::" + ALLOWED + "^__^" + MODEL_FOLDER,
         ResourceAction.READ);
   }

   @Test
   void hSourceTypeTakenFromPropertiesNotEntryType() throws Exception {
      // a logical model entry whose type property builds a physical table source
      grantPhysicalAccess(false);
      AssetEntry entry = modelEntry(ALLOWED);
      entry.setProperty("type", SourceInfo.PHYSICAL_TABLE + "");

      assertThrows(java.lang.SecurityException.class, () -> queryManager
         .checkViewsheetBaseEntryPermission(entry, assetRepository, principal));
   }

   @Test
   void hMissingSourceTypeDenied() throws Exception {
      AssetEntry entry = modelEntry(ALLOWED);
      entry.setProperty("type", null);

      assertThrows(java.lang.SecurityException.class, () -> queryManager
         .checkViewsheetBaseEntryPermission(entry, assetRepository, principal));
   }

   @Test
   void hWorksheetRequiresRead() throws Exception {
      AssetEntry ws = worksheetEntry();
      denyWorksheet(ws);

      assertThrows(MessageException.class, () -> queryManager
         .checkViewsheetBaseEntryPermission(ws, assetRepository, principal));
   }

   @Test
   void hReadableSourcesAllowed() throws Exception {
      queryManager.checkViewsheetBaseEntryPermission(
         modelEntry(ALLOWED), assetRepository, principal);
      queryManager.checkViewsheetBaseEntryPermission(
         worksheetEntry(), assetRepository, principal);
      verify(assetRepository).checkAssetPermission(
         same(principal), any(AssetEntry.class), eq(ResourceAction.READ));
   }

   // ---- N: new viewsheet ----

   private ComposerViewsheetController newController() {
      return new ComposerViewsheetController(
         mock(RuntimeViewsheetRef.class), viewsheetService,
         mock(ComposerViewsheetServiceProxy.class), securityEngine, queryManager);
   }

   private static NewViewsheetEvent newEvent(AssetEntry source) {
      NewViewsheetEvent event = new NewViewsheetEvent();
      event.setDataSource(source);
      return event;
   }

   @Test
   void nDeniedSourceOpensNothing() throws Exception {
      assertThrows(java.lang.SecurityException.class, () -> newController().newViewsheet(
         newEvent(modelEntry(DENIED)), principal, mock(CommandDispatcher.class), null));
      verify(viewsheetService, never()).openTemporaryViewsheet(any(), any());
   }

   @Test
   void nDeniedWorksheetOpensNothing() throws Exception {
      AssetEntry ws = worksheetEntry();
      denyWorksheet(ws);

      assertThrows(MessageException.class, () -> newController().newViewsheet(
         newEvent(ws), principal, mock(CommandDispatcher.class), null));
      verify(viewsheetService, never()).openTemporaryViewsheet(any(), any());
   }

   @Test
   void nReadableSourceOpens() throws Exception {
      AssetEntry source = modelEntry(ALLOWED);

      newController().newViewsheet(newEvent(source), principal, mock(CommandDispatcher.class), null);
      verify(viewsheetService).openTemporaryViewsheet(same(source), same(principal));
   }

   // ---- W: VS wizard open for a new viewsheet ----

   private VSWizardDialogController wizardController() {
      return new VSWizardDialogController(
         viewsheetService, null, null, mock(VSWizardDialogServiceProxy.class), securityEngine,
         queryManager);
   }

   private static OpenVsWizardEvent wizardEvent(AssetEntry source) {
      OpenVsWizardEvent event = new OpenVsWizardEvent();
      event.setEntry(source);
      return event;
   }

   @Test
   void wDeniedSourceOpensNothing() throws Exception {
      assertThrows(java.lang.SecurityException.class, () -> wizardController().createRuntimeSheet(
         wizardEvent(modelEntry(DENIED)), null, mock(CommandDispatcher.class), principal));
      verify(viewsheetService, never()).openTemporaryViewsheet(any(), any());
   }

   @Test
   void wReadableSourceOpens() throws Exception {
      AssetEntry source = modelEntry(ALLOWED);

      wizardController().createRuntimeSheet(
         wizardEvent(source), null, mock(CommandDispatcher.class), principal);
      verify(viewsheetService).openTemporaryViewsheet(same(source), same(principal));
   }

   // ---- P: viewsheet property dialog ----

   private ViewsheetPropertyDialogService propertyService() {
      return new ViewsheetPropertyDialogService(
         mock(CoreLifecycleService.class), viewsheetService, mock(VSLayoutService.class),
         mock(ViewsheetSettingsService.class), mock(VSAssemblyInfoHandler.class), null, null,
         null, queryManager);
   }

   private static ViewsheetPropertyDialogModel propertyModel(AssetEntry source) {
      ViewsheetPropertyDialogModel model = ViewsheetPropertyDialogModel.builder().build();
      model.vsOptionsPane().getSelectDataSourceDialogModel().setDataSource(source);
      model.vsOptionsPane().setDesc("changed");
      model.vsOptionsPane().getViewsheetParametersDialogModel()
         .setDisabledParameters(new String[0]);
      model.filtersPane().setSharedFilters(new ArrayList<>());
      model.filtersPane().setFilters(new ArrayList<>());
      model.screensPane().setDevices(new ArrayList<>());

      if(model.localizationPane() != null) {
         model.localizationPane().setLocalized(new ArrayList<>());
      }

      return model;
   }

   private void setViewsheetInfo(ViewsheetPropertyDialogModel model) throws Exception {
      when(viewsheet.getLayoutInfo()).thenReturn(new LayoutInfo());
      propertyService().setViewsheetInfo(
         RUNTIME_ID, model, principal, mock(CommandDispatcher.class), null, null);
   }

   @Test
   void pDeniedSourceChangesNothing() throws Exception {
      assertThrows(java.lang.SecurityException.class,
                   () -> setViewsheetInfo(propertyModel(modelEntry(DENIED))));
      verify(viewsheet, never()).setBaseEntry(any());
      verify(viewsheet, never()).update(any(), any(), any());
      assertNull(viewsheet.getViewsheetInfo().getDescription());
   }

   @Test
   void pReadableSourceIsBound() throws Exception {
      AssetEntry source = modelEntry(ALLOWED);

      setViewsheetInfo(propertyModel(source));
      verify(viewsheet).setBaseEntry(same(source));
   }

   @Test
   void pUnchangedSourceIsNotChecked() throws Exception {
      when(viewsheet.getBaseEntry()).thenReturn(modelEntry(ALLOWED));
      // equal by path, type and scope, whatever its properties
      AssetEntry client = modelEntry(DENIED);

      setViewsheetInfo(propertyModel(client));
      verifyNoSourceChecked();
      verify(viewsheet, never()).setBaseEntry(any());
   }

   // ---- S: Save-As ----

   private SaveViewsheetDialogService saveService() {
      return new SaveViewsheetDialogService(
         mock(CoreLifecycleService.class), assetRepository, viewsheetService,
         mock(ViewsheetSettingsService.class), queryManager);
   }

   private static SaveViewsheetDialogModel saveModel(AssetEntry source) {
      SaveViewsheetDialogModel model = new SaveViewsheetDialogModel();
      model.setName("Saved");
      model.getViewsheetOptionsPaneModel().getSelectDataSourceDialogModel().setDataSource(source);
      return model;
   }

   private void save(SaveViewsheetDialogModel model) throws Exception {
      when(rvs.getEntry()).thenReturn(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "Old", null));
      saveService().saveViewsheet(RUNTIME_ID, model, null, principal,
                                  mock(CommandDispatcher.class));
   }

   @Test
   void sDeniedSourceSavesNothing() throws Exception {
      assertThrows(java.lang.SecurityException.class, () -> save(saveModel(modelEntry(DENIED))));
      verify(viewsheet, never()).setBaseEntry(any());
      verify(rvs, never()).setEditable(anyBoolean());
      verify(viewsheetService, never()).setViewsheet(any(), any(), any(), anyBoolean(), anyBoolean());
   }

   @Test
   void sDeniedWorksheetSavesNothing() throws Exception {
      AssetEntry ws = worksheetEntry();
      denyWorksheet(ws);

      assertThrows(MessageException.class, () -> save(saveModel(ws)));
      verify(viewsheet, never()).setBaseEntry(any());
      verify(viewsheetService, never()).setViewsheet(any(), any(), any(), anyBoolean(), anyBoolean());
   }

   @Test
   void sReadableSourceIsSaved() throws Exception {
      AssetEntry source = modelEntry(ALLOWED);

      save(saveModel(source));
      verify(viewsheet).setBaseEntry(same(source));
      verify(viewsheetService).setViewsheet(same(viewsheet), any(), same(principal),
                                            anyBoolean(), anyBoolean());
   }

   @Test
   void sUnchangedSourceKeepsTheServerEntryWithoutCheck() throws Exception {
      AssetEntry server = modelEntry(ALLOWED);
      when(viewsheet.getBaseEntry()).thenReturn(server);
      // equal by path, type and scope, but carrying another data source in its properties
      AssetEntry client = modelEntry(DENIED);

      save(saveModel(client));
      verifyNoSourceChecked();
      verify(viewsheet).setBaseEntry(same(server));
      verify(viewsheet, never()).setBaseEntry(same(client));
   }

   @Test
   void sClearedSourceSavedWithoutCheck() throws Exception {
      when(viewsheet.getBaseEntry()).thenReturn(modelEntry(ALLOWED));

      save(saveModel(null));
      verifyNoSourceChecked();
      verify(viewsheet).setBaseEntry(null);
   }

   // ---- A1 with the entry the real client sends ----

   /**
    * The dialogs send back the base entry the server put in their model, after a stored
    * viewsheet parsed it from XML and the client round-tripped it through the AssetEntry
    * JSON serializer and deserializer. That entry must still be recognized as unchanged, so an
    * author who cannot read the base source of an existing viewsheet can re-save it.
    */
   private static AssetEntry storedAndClientRoundTripped(AssetEntry entry) throws Exception {
      java.io.StringWriter xml = new java.io.StringWriter();
      java.io.PrintWriter writer = new java.io.PrintWriter(xml);
      entry.writeXML(writer);
      writer.flush();
      AssetEntry stored = new AssetEntry();
      stored.parseXML(inetsoft.util.Tool.parseXML(new java.io.StringReader(xml.toString()))
                         .getDocumentElement());

      com.fasterxml.jackson.databind.ObjectMapper mapper =
         new com.fasterxml.jackson.databind.ObjectMapper();
      String json = mapper.writeValueAsString(stored);
      AssetEntry client = mapper.readValue(json, AssetEntry.class);
      // the options pane rewrites the description before the dialog is sent
      client.setProperty("_description_", "rewritten by the client");
      return client;
   }

   private static AssetEntry folderedModelEntry() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
         DENIED + "/" + MODEL_FOLDER + "/" + MODEL, null);
      entry.setProperty("prefix", DENIED);
      entry.setProperty("source", MODEL);
      entry.setProperty("type", SourceInfo.MODEL + "");
      entry.setProperty("folder_description", MODEL_FOLDER);
      return entry;
   }

   private void denyEverySource() throws Exception {
      grantRead();
      grantModelRead(false);
      grantPhysicalAccess(false);
      doThrow(new MessageException("denied")).when(assetRepository)
         .checkAssetPermission(any(), any(), any());
   }

   @Test
   void aRoundTrippedUnchangedModelSavesWithoutCheck() throws Exception {
      denyEverySource();
      AssetEntry server = folderedModelEntry();
      when(viewsheet.getBaseEntry()).thenReturn(server);
      AssetEntry client = storedAndClientRoundTripped(server);
      assertNotSame(server, client);
      assertEquals(server, client);

      save(saveModel(client));
      verifyNoSourceChecked();
      verify(viewsheet).setBaseEntry(same(server));
      verify(viewsheetService).setViewsheet(same(viewsheet), any(), same(principal),
                                            anyBoolean(), anyBoolean());
   }

   @Test
   void aRoundTrippedUnchangedUserWorksheetSavesWithoutCheck() throws Exception {
      denyEverySource();
      AssetEntry server = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET, "My Folder/WS1",
         IdentityID.getIdentityIDFromKey(principal.getName()));
      when(viewsheet.getBaseEntry()).thenReturn(server);
      AssetEntry client = storedAndClientRoundTripped(server);
      assertEquals(server, client);

      save(saveModel(client));
      verifyNoSourceChecked();
      verify(viewsheet).setBaseEntry(same(server));
   }

   @Test
   void aRoundTrippedUnchangedModelAppliesPropertiesWithoutCheck() throws Exception {
      denyEverySource();
      AssetEntry server = folderedModelEntry();
      when(viewsheet.getBaseEntry()).thenReturn(server);

      setViewsheetInfo(propertyModel(storedAndClientRoundTripped(server)));
      verifyNoSourceChecked();
      verify(viewsheet, never()).setBaseEntry(any());
      assertEquals("changed", viewsheet.getViewsheetInfo().getDescription());
   }
}
