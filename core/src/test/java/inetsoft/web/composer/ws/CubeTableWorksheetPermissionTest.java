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
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.xmla.Domain;
import inetsoft.util.Tool;
import inetsoft.util.DataSpace;
import inetsoft.web.binding.VSFormulaService;
import inetsoft.web.binding.VSScriptableService;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.model.vs.ViewsheetPropertyDialogModel;
import inetsoft.web.composer.model.ws.SaveWorksheetDialogModel;
import inetsoft.web.composer.model.ws.SortColumnDialogModel;
import inetsoft.web.composer.model.ws.VariableAssemblyDialogModel;
import inetsoft.web.composer.vs.dialog.SelectionListService;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.ws.dialog.*;
import inetsoft.web.composer.ws.event.*;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.composer.ws.service.SaveWorksheetService;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSInputService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77462: <tt>Worksheet.getAssembly</tt> resolves a cube table name
 * (<tt>___inetsoft_cube_&lt;ds&gt;/&lt;cube&gt;</tt>) straight from the data source, ahead of the
 * worksheet's own assemblies and without a permission check, so every worksheet composer
 * endpoint that looks up or stores a client-supplied table name must check it, and a worksheet
 * that references a cube table name must not be saved. The viewsheet column endpoints check a
 * cube that no assembly is bound to yet. Labels:
 * <ul>
 *    <li>G WorksheetControllerService guard helper</li>
 *    <li>L lookup endpoints (dialogs, show plan, load data, table mode)</li>
 *    <li>S store endpoints (mirror, rename, join, variable table)</li>
 *    <li>B save backstop (mirror laundering, subquery, variable, cube-named table)</li>
 *    <li>E exempt paths (viewsheet runtime synthesis, bound viewsheet cube)</li>
 *    <li>V viewsheet column endpoints (/api/vs/selectionList/columns, /vs/dataInput/columns)</li>
 *    <li>K Bug #77427 viewsheet rename and base change gates</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CubeTableWorksheetPermissionTest {
   private static final String CUBE_SOURCE = "OLAP";
   private static final String CUBE = "Sales";
   private static final String CUBE_TABLE = Assembly.CUBE_VS + CUBE_SOURCE + "/" + CUBE;
   private static final String TABLE = "Query1";
   private static final String RUNTIME_ID = "ws1";

   private SecurityEngine securityEngine;
   private QueryManagerService queryManager;
   private ViewsheetService viewsheetService;
   private RuntimeWorksheet rws;
   private AssetQuerySandbox box;
   private Worksheet worksheet;
   private final CommandDispatcher dispatcher = mock(CommandDispatcher.class);
   private final Principal principal =
      new SRPrincipal(new IdentityID("bob", Organization.getDefaultOrganizationID()));

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      queryManager = new QueryManagerService(
         mock(RuntimeQueryService.class), mock(XRepository.class), mock(DataSourceService.class),
         securityEngine, mock(ColumnCache.class));

      worksheet = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(worksheet, TABLE);
      worksheet.addAssembly(table);

      box = mock(AssetQuerySandbox.class);
      when(box.getColumnInfoMapping(anyString())).thenReturn(new HashMap<>());
      rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(rws.getID()).thenReturn(RUNTIME_ID);
      viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getWorksheet(RUNTIME_ID, principal)).thenReturn(rws);
   }

   private <T extends WorksheetControllerService> T guarded(T service) {
      service.setQueryManagerService(queryManager);
      return service;
   }

   /**
    * Runs the call with the static security engine and repository that the cube listing
    * decision uses, with an OLAP cube (one dimension level and one measure) in a data source
    * that may or may not be readable.
    */
   private void withCube(boolean dataSourceReadable, boolean cubeReadable, Executable call)
      throws Throwable
   {
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), nullable(String.class), eq(ResourceAction.READ)))
         .thenAnswer(inv -> dataSourceReadable && CUBE_SOURCE.equals(inv.getArgument(2)));
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.CUBE), anyString(), eq(ResourceAction.READ)))
         .thenAnswer(inv -> cubeReadable || !(CUBE_SOURCE + "::" + CUBE).equals(inv.getArgument(2)));
      Domain domain = mock(Domain.class);
      XCube cube = cube();
      when(domain.getCube(CUBE)).thenReturn(cube);
      XRepository repository = mock(XRepository.class);
      when(repository.getDomain(CUBE_SOURCE)).thenReturn(domain);

      try(MockedStatic<SecurityEngine> security = mockStatic(SecurityEngine.class);
          MockedStatic<XRepository> repositoryStatic = mockStatic(XRepository.class))
      {
         security.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
         repositoryStatic.when(XRepository::getRepository).thenReturn(repository);
         call.execute();
      }
   }

   private static XCube cube() {
      XCubeMember level = mock(XCubeMember.class);
      when(level.getName()).thenReturn("SecretCountry");
      when(level.getType()).thenReturn(XSchema.STRING);
      XDimension dimension = mock(XDimension.class);
      when(dimension.getName()).thenReturn("SecretRegion");
      when(dimension.getLevelCount()).thenReturn(1);
      when(dimension.getLevelAt(0)).thenReturn(level);
      XCubeMember measure = mock(XCubeMember.class);
      when(measure.getName()).thenReturn("SecretMargin");
      when(measure.getType()).thenReturn(XSchema.DOUBLE);
      XCube cube = mock(XCube.class);
      // the enumerations are consumed, so each call gets a fresh one
      when(cube.getDimensions()).thenAnswer(inv -> Collections.enumeration(List.of(dimension)));
      when(cube.getMeasures()).thenAnswer(inv -> Collections.enumeration(List.of(measure)));
      return cube;
   }

   private void assertDenied(Executable call) throws Throwable {
      withCube(true, false, () -> assertThrows(java.lang.SecurityException.class, call));
      verify(securityEngine, atLeastOnce()).checkPermission(
         principal, ResourceType.CUBE, CUBE_SOURCE + "::" + CUBE, ResourceAction.READ);
   }

   private void verifyNothingChecked() throws Exception {
      verify(securityEngine, never()).checkPermission(
         any(Principal.class), any(ResourceType.class), nullable(String.class), any(ResourceAction.class));
   }

   private static WSAssemblyEvent assemblyEvent(String name) {
      WSAssemblyEvent event = mock(WSAssemblyEvent.class);
      when(event.getAssemblyName()).thenReturn(name);
      return event;
   }

   // ---- G: guard helper ----

   @Test
   void gNonCubeNamesNotChecked() throws Exception {
      WorksheetControllerService service = guarded(new WorksheetControllerService(viewsheetService, null));
      service.checkCubeTableReadPermission(principal, TABLE, null, Assembly.CUBE_VS + "noslash");
      service.checkCubeTableReadPermission(principal, (String[]) null);
      verifyNothingChecked();
   }

   @Test
   void gCubeDeniedWithoutDataSourceRead() throws Throwable {
      WorksheetControllerService service = guarded(new WorksheetControllerService(viewsheetService, null));
      withCube(false, true, () -> assertThrows(java.lang.SecurityException.class,
         () -> service.checkCubeTableReadPermission(principal, TABLE, CUBE_TABLE)));
   }

   @Test
   void gCubeDeniedWithoutCubeRead() throws Throwable {
      WorksheetControllerService service = guarded(new WorksheetControllerService(viewsheetService, null));
      assertDenied(() -> service.checkCubeTableReadPermission(principal, CUBE_TABLE));
   }

   @Test
   void gReadableCubeAllowed() throws Throwable {
      WorksheetControllerService service = guarded(new WorksheetControllerService(viewsheetService, null));
      withCube(true, true, () -> service.checkCubeTableReadPermission(principal, CUBE_TABLE));
   }

   @Test
   void gCubeRefusedWithoutQueryManager() {
      WorksheetControllerService service = new WorksheetControllerService(viewsheetService, null);
      assertDoesNotThrow(() -> service.checkCubeTableReadPermission(principal, TABLE));
      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkCubeTableReadPermission(principal, CUBE_TABLE));
   }

   // ---- L: lookup endpoints ----

   @Test
   void lSortDialogDeniedCubeRefused() throws Throwable {
      SortColumnDialogService service = guarded(new SortColumnDialogService(viewsheetService, null));
      assertDenied(() -> service.getModel(RUNTIME_ID, CUBE_TABLE, principal));
   }

   @Test
   void lSortDialogReadableCubeListsColumns() throws Throwable {
      SortColumnDialogService service = guarded(new SortColumnDialogService(viewsheetService, null));
      withCube(true, true, () -> {
         SortColumnDialogModel model = service.getModel(RUNTIME_ID, CUBE_TABLE, principal);
         assertEquals(2, model.getSortColumnEditorModel().getAvailableColumns().length);
      });
   }

   @Test
   void lSortDialogWorksheetTableNotChecked() throws Throwable {
      SortColumnDialogService service = guarded(new SortColumnDialogService(viewsheetService, null));
      withCube(false, false, () -> {
         SortColumnDialogModel model = service.getModel(RUNTIME_ID, TABLE, principal);
         assertEquals(TABLE, model.getName());
      });
      verifyNothingChecked();
   }

   @Test
   void lAggregateDialogDeniedCubeRefused() throws Throwable {
      AggregateDialogService service =
         guarded(new AggregateDialogService(viewsheetService, null, null, null));
      assertDenied(() -> service.getModel(RUNTIME_ID, CUBE_TABLE, principal));
   }

   @Test
   void lShowHideColumnsDialogDeniedCubeRefused() throws Throwable {
      SetColumnVisibleService service =
         guarded(new SetColumnVisibleService(viewsheetService, null, null));
      assertDenied(() -> service.getModel(RUNTIME_ID, CUBE_TABLE, principal));
   }

   @Test
   void lReorderColumnsDialogDeniedCubeRefused() throws Throwable {
      ReorderColumnsDialogService service =
         guarded(new ReorderColumnsDialogService(viewsheetService, null, null));
      assertDenied(() -> service.getModel(RUNTIME_ID, CUBE_TABLE, principal));
   }

   @Test
   void lExpressionDialogDeniedCubeRefused() throws Throwable {
      ExpressionDialogService service =
         guarded(new ExpressionDialogService(viewsheetService, null, null));
      assertDenied(() -> service.getExpressionModel(RUNTIME_ID, CUBE_TABLE, "0", false, principal));
      verify(box, never()).getTableLens(anyString(), anyInt());
   }

   @Test
   void lTablePropertyDialogDeniedCubeRefused() throws Throwable {
      TablePropertyDialogService service =
         guarded(new TablePropertyDialogService(viewsheetService, null));
      assertDenied(() -> service.getModel(RUNTIME_ID, CUBE_TABLE, principal));
   }

   @Test
   void lValueRangeDialogDeniedCubeRefused() throws Throwable {
      ValueRangeService service = guarded(new ValueRangeService(viewsheetService, null, null));
      assertDenied(() -> service.valueRangeModel(
         RUNTIME_ID, CUBE_TABLE, "Range", "SecretMargin", true, principal));
   }

   @Test
   void lShowPlanDeniedCubeRefused() throws Throwable {
      ShowPlanService service = new ShowPlanService(viewsheetService, queryManager);
      assertDenied(() -> service.showPlan(RUNTIME_ID, CUBE_TABLE, principal));
   }

   @Test
   void lLoadDataDeniedCubeRefused() throws Throwable {
      WSLoadTableDataService service = guarded(new WSLoadTableDataService(viewsheetService, null));
      WSLoadTableDataEvent event = mock(WSLoadTableDataEvent.class);
      assertDenied(() -> service.loadWSTableData(RUNTIME_ID, CUBE_TABLE, event, principal, dispatcher));
      verify(box, never()).getTableLens(anyString(), anyInt());
      verifyNoInteractions(dispatcher);
   }

   @Test
   void lLiveModeDeniedCubeRefused() throws Throwable {
      TableModeService service = guarded(new TableModeService(viewsheetService, null, null));
      assertDenied(() -> service.setLiveMode(RUNTIME_ID, assemblyEvent(CUBE_TABLE), principal, dispatcher));
      verifyNoInteractions(dispatcher);
   }

   // ---- S: store endpoints ----

   @Test
   void sMirrorOfDeniedCubeRefused() throws Throwable {
      WSMirrorService service = guarded(new WSMirrorService(viewsheetService, null));
      assertDenied(() -> service.addMirrorAssembly(
         RUNTIME_ID, assemblyEvent(CUBE_TABLE), principal, dispatcher));
      assertEquals(1, worksheet.getAssemblies().length);
   }

   @Test
   void sMirrorOfWorksheetTableNotChecked() throws Throwable {
      WSMirrorService service = guarded(new WSMirrorService(viewsheetService, null));
      withCube(false, false, () -> service.addMirrorAssembly(
         RUNTIME_ID, assemblyEvent(TABLE), principal, dispatcher));
      assertEquals(2, worksheet.getAssemblies().length);
      verifyNothingChecked();
   }

   @Test
   void sRenameToCubeNameRefusedEvenIfReadable() throws Throwable {
      // the worksheet would resolve the new name to the cube instead of the table, and the
      // save would refuse it, so the rename is refused whatever the permission
      WSRenameAssemblyService service = guarded(new WSRenameAssemblyService(viewsheetService, null));
      WSRenameAssemblyEvent event = mock(WSRenameAssemblyEvent.class);
      when(event.oldName()).thenReturn(TABLE);
      when(event.newName()).thenReturn(CUBE_TABLE);
      withCube(true, true, () -> assertThrows(java.lang.SecurityException.class,
         () -> service.renameAssembly(RUNTIME_ID, event, principal, dispatcher)));
      assertNotNull(worksheet.getAssembly(TABLE));
   }

   @Test
   void sConcatCompatibilityWithDeniedCubeRefused() throws Throwable {
      ConcatenateTablesService service =
         guarded(new ConcatenateTablesService(viewsheetService, null));
      ConcatCompatibilityEvent event = mock(ConcatCompatibilityEvent.class);
      when(event.getSourceTable()).thenReturn(TABLE);
      when(event.getOtherTables()).thenReturn(new String[] { CUBE_TABLE });
      assertDenied(() -> service.checkCompatibility(RUNTIME_ID, event, principal, dispatcher));
      verifyNoInteractions(dispatcher);
   }

   @Test
   void sJoinWithDeniedCubeRefused() throws Throwable {
      InnerJoinService service = guarded(new InnerJoinService(viewsheetService, null));
      WSJoinTablePairEvent event = mock(WSJoinTablePairEvent.class);
      when(event.getLeftTable()).thenReturn(TABLE);
      when(event.getRightTable()).thenReturn(CUBE_TABLE);
      assertDenied(() -> service.joinTablePair(RUNTIME_ID, event, principal, dispatcher));
      assertEquals(1, worksheet.getAssemblies().length);
   }

   @Test
   void sVariableTableDeniedCubeRefused() throws Throwable {
      VariableAssemblyDialogService service =
         guarded(new VariableAssemblyDialogService(viewsheetService, null));
      VariableAssemblyDialogModel model = new VariableAssemblyDialogModel();
      model.setNewName("Var1");
      model.setSelectionList("query");
      model.getVariableTableListDialogModel().setTableName(CUBE_TABLE);
      assertDenied(() -> service.setVariableAssemblyProperties(RUNTIME_ID, model, principal, dispatcher));
      assertEquals(1, worksheet.getAssemblies().length);
   }

   // ---- B: save backstop ----

   private MirrorTableAssembly mirrorOfCube(Worksheet ws) {
      MirrorTableAssembly mirror = new MirrorTableAssembly(
         ws, "Query2", null, false, (TableAssembly) ws.getAssembly(CUBE_TABLE));
      ws.addAssembly(mirror);
      return mirror;
   }

   @Test
   void bSaveRefusesLaunderedMirror() throws Throwable {
      SaveWorksheetService service = guarded(new SaveWorksheetService(
         null, viewsheetService, null, null, null));
      withCube(true, true, () -> mirrorOfCube(worksheet));
      when(rws.getEntry()).thenReturn(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "WS1", null));
      SaveSheetEvent event = new SaveSheetEvent();

      assertThrows(java.lang.SecurityException.class,
                   () -> service.saveWorksheet(RUNTIME_ID, event, principal, dispatcher));
      verify(viewsheetService, never()).setWorksheet(
         any(Worksheet.class), any(AssetEntry.class), any(Principal.class), anyBoolean(), anyBoolean());
   }

   @Test
   void bSaveAsRefusesLaunderedMirror() throws Throwable {
      SaveWorksheetDialogService service = guarded(new SaveWorksheetDialogService(
         viewsheetService, null, mock(DataSpace.class)));
      withCube(true, true, () -> mirrorOfCube(worksheet));
      when(rws.getEntry()).thenReturn(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "WS1", null));
      SaveWorksheetDialogModel model = mock(SaveWorksheetDialogModel.class, RETURNS_DEEP_STUBS);

      assertThrows(java.lang.SecurityException.class,
                   () -> service.process(rws, model, principal, true));
      verify(viewsheetService, never()).setWorksheet(
         any(Worksheet.class), any(AssetEntry.class), any(Principal.class), anyBoolean(), anyBoolean());
   }

   @Test
   void bMirrorLaunderingSurvivesXmlAndIsRefused() throws Throwable {
      Worksheet[] parsed = new Worksheet[1];

      withCube(true, true, () -> {
         mirrorOfCube(worksheet);
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         PrintWriter writer =
            new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
         worksheet.writeXML(writer);
         writer.flush();
         assertTrue(out.toString(StandardCharsets.UTF_8).contains(CUBE_TABLE));

         Document doc = Tool.parseXML(new ByteArrayInputStream(out.toByteArray()));
         parsed[0] = new Worksheet();
         parsed[0].parseXML(doc.getDocumentElement());
      });

      assertThrows(java.lang.SecurityException.class,
                   () -> WorksheetControllerService.checkNoCubeTableReference(parsed[0]));
   }

   @Test
   void bSubqueryOnCubeRefused() {
      AssetCondition condition = new AssetCondition(XSchema.STRING);
      condition.setOperation(XCondition.EQUAL_TO);
      SubQueryValue subquery = new SubQueryValue();
      subquery.setQuery(CUBE_TABLE);
      condition.addValue(subquery);
      ConditionList conditions = new ConditionList();
      conditions.append(new ConditionItem(new AttributeRef("A"), condition, 0));
      ((TableAssembly) worksheet.getAssembly(TABLE)).setPreConditionList(conditions);

      assertThrows(java.lang.SecurityException.class,
                   () -> WorksheetControllerService.checkNoCubeTableReference(worksheet));
   }

   @Test
   void bVariableOnCubeRefused() {
      DefaultVariableAssembly variable = new DefaultVariableAssembly(worksheet, "Var1");
      AssetVariable var = new AssetVariable("Var1");
      var.setTableName(CUBE_TABLE);
      variable.setVariable(var);
      worksheet.addAssembly(variable);

      assertThrows(java.lang.SecurityException.class,
                   () -> WorksheetControllerService.checkNoCubeTableReference(worksheet));
   }

   @Test
   void bOrdinaryWorksheetSaved() {
      MirrorTableAssembly mirror = new MirrorTableAssembly(
         worksheet, "Query2", null, false, (TableAssembly) worksheet.getAssembly(TABLE));
      worksheet.addAssembly(mirror);
      DefaultVariableAssembly variable = new DefaultVariableAssembly(worksheet, "Var1");
      AssetVariable var = new AssetVariable("Var1");
      var.setTableName(TABLE);
      variable.setVariable(var);
      worksheet.addAssembly(variable);

      assertDoesNotThrow(() -> WorksheetControllerService.checkNoCubeTableReference(worksheet));
   }

   // ---- E: exempt paths ----

   @Test
   void eViewsheetRuntimeSynthesisUnchanged() throws Throwable {
      // a viewsheet that binds a cube resolves it through its base worksheet, which is not
      // checked here, so an existing viewsheet keeps working
      withCube(false, false, () -> assertInstanceOf(
         CubeTableAssembly.class, new Worksheet().getAssembly(CUBE_TABLE)));
   }

   @Test
   void eWorksheetCubeTableFromAssetTreeMirroredAndSaved() throws Throwable {
      // a cube dropped from the worksheet asset tree is named and sourced without the cube
      // table prefix (WorksheetOpenAssetService), so using it is not checked and a worksheet
      // built on it is still saved
      CubeTableAssembly cube = new CubeTableAssembly(worksheet, "OLAP_Sales");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef("SecretRegion", "SecretCountry")));
      cube.setColumnSelection(columns);
      cube.setSourceInfo(new SourceInfo(SourceInfo.CUBE, CUBE_SOURCE, CUBE));
      worksheet.addAssembly(cube);
      DefaultVariableAssembly variable = new DefaultVariableAssembly(worksheet, "Var1");
      AssetVariable var = new AssetVariable("Var1");
      var.setTableName(cube.getName());
      variable.setVariable(var);
      worksheet.addAssembly(variable);
      WSMirrorService service = guarded(new WSMirrorService(viewsheetService, null));

      withCube(false, false, () -> service.addMirrorAssembly(
         RUNTIME_ID, assemblyEvent(cube.getName()), principal, dispatcher));

      assertEquals(4, worksheet.getAssemblies().length);
      assertDoesNotThrow(() -> WorksheetControllerService.checkNoCubeTableReference(worksheet));
      verifyNothingChecked();
   }

   // ---- V: viewsheet column endpoints ----

   private RuntimeViewsheet viewsheet(String boundTable) {
      Viewsheet vs = new Viewsheet();
      vs.setBaseEntry(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "Base", null));

      if(boundTable != null) {
         ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
         chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, boundTable));
         vs.addAssembly(chart);
      }

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private SelectionListService selectionListService(RuntimeViewsheet rvs,
                                                     VSColumnHandler columnHandler)
      throws Exception
   {
      ViewsheetService service = mock(ViewsheetService.class);
      when(service.getViewsheet("vs1", principal)).thenReturn(rvs);
      when(columnHandler.getTableColumns(any(), anyString(), any())).thenReturn(new ColumnSelection());
      return new SelectionListService(
         columnHandler, service, mock(VSAssemblyInfoHandler.class), queryManager);
   }

   @Test
   void vSelectionListColumnsOfUnboundCubeRefused() throws Throwable {
      VSColumnHandler columnHandler = mock(VSColumnHandler.class);
      SelectionListService service = selectionListService(viewsheet(TABLE), columnHandler);
      assertDenied(() -> service.getTableColumns("vs1", CUBE_TABLE, principal));
      verifyNoInteractions(columnHandler);
   }

   @Test
   void vSelectionListColumnsOfBoundCubeNotChecked() throws Throwable {
      VSColumnHandler columnHandler = mock(VSColumnHandler.class);
      SelectionListService service = selectionListService(viewsheet(CUBE_TABLE), columnHandler);
      withCube(false, false, () -> service.getTableColumns("vs1", CUBE_TABLE, principal));
      verifyNothingChecked();
   }

   @Test
   void vDataInputColumnsOfUnboundCubeRefused() throws Throwable {
      ViewsheetService service = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = viewsheet(TABLE);
      when(service.getViewsheet("vs1", principal)).thenReturn(rvs);
      VSColumnHandler columnHandler = mock(VSColumnHandler.class);
      VSInputService input = new VSInputService(
         null, null, service, null, null, null, null, null, columnHandler, queryManager);
      assertDenied(() -> input.getTableColumns("vs1", CUBE_TABLE, principal));
      verifyNoInteractions(columnHandler);
   }

   @Test
   void vDataInputColumnsOfReadableCubeListed() throws Throwable {
      ViewsheetService service = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = viewsheet(TABLE);
      when(service.getViewsheet("vs1", principal)).thenReturn(rvs);
      VSColumnHandler columnHandler = mock(VSColumnHandler.class);
      when(columnHandler.getTableColumns(rvs, CUBE_TABLE, true, principal))
         .thenReturn(new ColumnSelection());
      VSInputService input = new VSInputService(
         null, null, service, null, null, null, null, null, columnHandler, queryManager);
      withCube(true, true, () -> input.getTableColumns("vs1", CUBE_TABLE, principal));
      verify(columnHandler).getTableColumns(rvs, CUBE_TABLE, true, principal);
   }

   private VSFormulaService formulaService(RuntimeViewsheet rvs, VSColumnHandler columnHandler)
      throws Exception
   {
      ViewsheetService service = mock(ViewsheetService.class);
      when(service.getViewsheet("vs1", principal)).thenReturn(rvs);
      return new VSFormulaService(service, null, columnHandler, null, queryManager);
   }

   @Test
   void vFormulaFieldsOfUnboundCubeRefused() throws Throwable {
      VSColumnHandler columnHandler = mock(VSColumnHandler.class);
      VSFormulaService service = formulaService(viewsheet(TABLE), columnHandler);
      assertDenied(() -> service.getFields("vs1", "Chart1", CUBE_TABLE, principal));
      verifyNoInteractions(columnHandler);
   }

   @Test
   void vFormulaFieldsOfBoundCubeNotChecked() throws Throwable {
      VSFormulaService service = formulaService(viewsheet(CUBE_TABLE), mock(VSColumnHandler.class));
      withCube(false, false, () -> service.getFields("vs1", "Chart1", CUBE_TABLE, principal));
      withCube(false, false, () -> service.getFields("vs1", "Chart1", null, principal));
      verifyNothingChecked();
   }

   @Test
   void vScriptFieldsOfUnboundCubeRefused() throws Throwable {
      ViewsheetService service = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = viewsheet(TABLE);
      when(service.getViewsheet("vs1", principal)).thenReturn(rvs);
      VSScriptableService scriptable = new VSScriptableService(service, null, null, queryManager);
      assertDenied(() -> scriptable.getScriptDefinition("vs1", "Chart1", CUBE_TABLE, false, principal));
      verify(rvs, never()).getViewsheetSandbox();
   }

   // ---- K: Bug #77427 viewsheet rename and base change gates ----

   @Test
   void kRenameToDeniedCubeNameRefusedBeforeAnyChange() throws Throwable {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(text);
      vs.getViewsheetInfo().setFilterID("Text1", "F1");
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      VSObjectPropertyService service = new VSObjectPropertyService(
         null, null, null, null, null, null, null, null, queryManager);
      VSAssemblyInfo info = (VSAssemblyInfo) text.getVSAssemblyInfo().clone();

      assertDenied(() -> service.editObjectProperty(
         rvs, info, "Text1", CUBE_TABLE, null, principal, dispatcher));
      assertNotNull(vs.getAssembly("Text1"));
      assertEquals("F1", vs.getViewsheetInfo().getFilterID("Text1"));
      assertNull(vs.getViewsheetInfo().getFilterID(CUBE_TABLE));
   }

   @Test
   void kBaseChangeToDeniedCubeRefused() throws Throwable {
      Viewsheet vs = new Viewsheet();
      AssetEntry base = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "Base", null);
      vs.setBaseEntry(base);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetService service = mock(ViewsheetService.class);
      when(service.getViewsheet("vs1", principal)).thenReturn(rvs);
      when(service.getAssetRepository()).thenReturn(mock(AssetRepository.class));
      ViewsheetPropertyDialogService dialog = new ViewsheetPropertyDialogService(
         null, service, null, null, null, null, null, null, queryManager);
      ViewsheetPropertyDialogModel model = ViewsheetPropertyDialogModel.builder().build();
      // "^_^" stands for "/" in the name of an entry below the root
      AssetEntry cubeEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
         "F/" + Assembly.CUBE_VS + CUBE_SOURCE + "^_^" + CUBE, null);
      assertEquals(CUBE_TABLE, cubeEntry.getName());
      model.vsOptionsPane().getSelectDataSourceDialogModel().setDataSource(cubeEntry);

      assertDenied(() -> dialog.setViewsheetInfo("vs1", model, principal, dispatcher, null, null));
      assertEquals(base, vs.getBaseEntry());
   }
}
