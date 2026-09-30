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
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XCube;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.xmla.Domain;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingFactory;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.composer.model.ws.GroupingAssemblyDialogModel;
import inetsoft.web.composer.vs.dialog.DataOutputService;
import inetsoft.web.composer.vs.dialog.VSConditionDialogService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.dialog.GroupingAssemblyDialogService;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSOutputService;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77427: a viewsheet assembly bound to a cube table name
 * (<tt>___inetsoft_cube_&lt;ds&gt;/&lt;cube&gt;</tt>) is resolved by the worksheet straight
 * from the data source, outside the base worksheet and without a permission check, so the
 * authoring and data browsing endpoints must check a newly bound cube with the asset tree's
 * cube listing decision (data source READ and cube READ). A cube that is already bound is
 * not checked, so an existing viewsheet keeps working. Labels:
 * <ul>
 *    <li>H QueryManagerService cube table helpers</li>
 *    <li>A VSAssemblyInfoHandler.changeSource (the binding pane drop endpoints)</li>
 *    <li>B VSBindingService.updateAssembly (/vs/binding/setbinding and the chart editors)</li>
 *    <li>O /vs/dataOutput/table/columns</li>
 *    <li>C VS condition dialog browse-data</li>
 *    <li>G worksheet grouping dialog onlyFor</li>
 *    <li>W VSWizardBindingHandler.changeSource (the VS wizard refresh-fields endpoint)</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CubeTableBindingPermissionTest {
   private static final String CUBE_SOURCE = "OLAP";
   private static final String CUBE = "Sales";
   private static final String CUBE_TABLE = Assembly.CUBE_VS + CUBE_SOURCE + "/" + CUBE;
   private static final String OTHER_TABLE = "Query1";
   private static final String RUNTIME_ID = "vs1";

   private SecurityEngine securityEngine;
   private QueryManagerService queryManager;
   private final Principal principal =
      new SRPrincipal(new IdentityID("bob", Organization.getDefaultOrganizationID()));

   @BeforeEach
   void setUp() {
      securityEngine = mock(SecurityEngine.class);
      queryManager = new QueryManagerService(
         mock(RuntimeQueryService.class), mock(XRepository.class), mock(DataSourceService.class),
         securityEngine, mock(ColumnCache.class));
   }

   /**
    * Runs the call with the static security engine and repository that the cube listing
    * decision uses, with an OLAP cube in a data source that may or may not be readable.
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
      when(domain.getCube(CUBE)).thenReturn(mock(XCube.class));
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

   private void verifyCubeChecked() throws Exception {
      verify(securityEngine, atLeastOnce()).checkPermission(
         principal, ResourceType.CUBE, CUBE_SOURCE + "::" + CUBE, ResourceAction.READ);
   }

   private void verifyNothingChecked() throws Exception {
      verify(securityEngine, never()).checkPermission(
         any(Principal.class), any(ResourceType.class), nullable(String.class), any(ResourceAction.class));
   }

   // ---- H: helpers ----

   @Test
   void hNonCubeTableNotChecked() throws Exception {
      queryManager.checkCubeTableReadPermission(OTHER_TABLE, principal);
      queryManager.checkCubeTableReadPermission(null, principal);
      queryManager.checkCubeTableReadPermission(Assembly.CUBE_VS + "noslash", principal);
      verifyNothingChecked();
   }

   @Test
   void hCubeDeniedWithoutDataSourceRead() throws Throwable {
      withCube(false, true, () -> assertThrows(java.lang.SecurityException.class,
         () -> queryManager.checkCubeTableReadPermission(CUBE_TABLE, principal)));
      verify(securityEngine, atLeastOnce()).checkPermission(
         principal, ResourceType.DATA_SOURCE, CUBE_SOURCE, ResourceAction.READ);
   }

   @Test
   void hCubeDeniedWithoutCubeRead() throws Throwable {
      withCube(true, false, () -> assertThrows(java.lang.SecurityException.class,
         () -> queryManager.checkCubeTableReadPermission(CUBE_TABLE, principal)));
      verifyCubeChecked();
   }

   @Test
   void hUnresolvableCubeDenied() throws Throwable {
      withCube(true, true, () -> assertThrows(java.lang.SecurityException.class,
         () -> queryManager.checkCubeTableReadPermission(
            Assembly.CUBE_VS + CUBE_SOURCE + "/Missing", principal)));
   }

   @Test
   void hReadableCubeAllowed() throws Throwable {
      withCube(true, true, () -> queryManager.checkCubeTableReadPermission(CUBE_TABLE, principal));
      verifyCubeChecked();
   }

   @Test
   void hOnlyNewlyBoundTablesChecked() throws Throwable {
      withCube(false, false, () -> {
         queryManager.checkNewCubeTableReadPermission(CUBE_TABLE, CUBE_TABLE, principal);
         queryManager.checkNewCubeTablesReadPermission(
            List.of(CUBE_TABLE, OTHER_TABLE), List.of(CUBE_TABLE), principal);
         queryManager.checkNewCubeTablesReadPermission(
            OTHER_TABLE, List.of(CUBE_TABLE), List.of(CUBE_TABLE), principal);
         // an additional table of a selection is checked like the first one
         assertThrows(java.lang.SecurityException.class,
                      () -> queryManager.checkNewCubeTablesReadPermission(
                         OTHER_TABLE, List.of(CUBE_TABLE), List.of(OTHER_TABLE), principal));
      });
   }

   // ---- A: VSAssemblyInfoHandler.changeSource ----

   private VSAssemblyInfoHandler infoHandler() {
      return new VSAssemblyInfoHandler(null, null, null, null, queryManager);
   }

   private static ChartVSAssembly chart(String table) {
      ChartVSAssembly chart = new ChartVSAssembly(new Viewsheet(), "Chart1");

      if(table != null) {
         chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, table));
      }

      return chart;
   }

   @Test
   void aDeniedCubeLeavesSourceUnchanged() throws Throwable {
      ChartVSAssembly chart = chart(OTHER_TABLE);
      withCube(true, false, () -> assertThrows(java.lang.SecurityException.class,
         () -> infoHandler().changeSource(chart, CUBE_TABLE, SourceInfo.ASSET, principal)));
      assertEquals(OTHER_TABLE, chart.getSourceInfo().getSource());
   }

   @Test
   void aFirstBoundCubeDeniedWithoutDataSourceRead() throws Throwable {
      ChartVSAssembly chart = chart(null);
      withCube(false, true, () -> assertThrows(java.lang.SecurityException.class,
         () -> infoHandler().changeSource(chart, CUBE_TABLE, SourceInfo.ASSET, principal)));
      assertNull(chart.getSourceInfo());
   }

   @Test
   void aUnchangedCubeSourceNotChecked() throws Throwable {
      ChartVSAssembly chart = chart(CUBE_TABLE);
      withCube(false, false,
               () -> infoHandler().changeSource(chart, CUBE_TABLE, SourceInfo.ASSET, principal));
      assertEquals(CUBE_TABLE, chart.getSourceInfo().getSource());
      verifyNothingChecked();
   }

   @Test
   void aReadableCubeIsBound() throws Throwable {
      ChartVSAssembly chart = chart(OTHER_TABLE);
      withCube(true, true,
               () -> infoHandler().changeSource(chart, CUBE_TABLE, SourceInfo.ASSET, principal));
      assertEquals(CUBE_TABLE, chart.getSourceInfo().getSource());
   }

   // ---- B: VSBindingService.updateAssembly ----

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private VSBindingFactory chartFactory() {
      VSBindingFactory factory = mock(VSBindingFactory.class);
      doReturn(ChartVSAssembly.class).when(factory).getAssemblyClass();
      when(factory.updateAssembly(any(), any())).thenAnswer(inv -> inv.getArgument(1));
      return factory;
   }

   private static VSBindingService bindingService(VSBindingFactory<?, ?> factory,
                                                  QueryManagerService queryManager)
   {
      return new VSBindingService(null, null, null, null, List.of(factory), null, null, null,
                                  null, null, null, null, null, queryManager);
   }

   private static BindingModel bindingModel(String table) {
      inetsoft.web.binding.model.SourceInfo source = new inetsoft.web.binding.model.SourceInfo();
      source.setType(SourceInfo.ASSET);
      source.setSource(table);
      BindingModel model = mock(BindingModel.class);
      when(model.getSource()).thenReturn(source);
      return model;
   }

   @SuppressWarnings("unchecked")
   @Test
   void bDeniedNewCubeSourceChangesNothing() throws Throwable {
      VSBindingFactory<?, ?> factory = chartFactory();
      ChartVSAssembly chart = chart(OTHER_TABLE);
      withCube(true, false, () -> assertThrows(java.lang.SecurityException.class,
         () -> bindingService(factory, queryManager)
            .updateAssembly(bindingModel(CUBE_TABLE), chart, principal)));
      verify((VSBindingFactory<ChartVSAssembly, BindingModel>) factory, never())
         .updateAssembly(any(), any());
      assertEquals(OTHER_TABLE, chart.getSourceInfo().getSource());
   }

   @SuppressWarnings("unchecked")
   @Test
   void bUnchangedCubeSourceNotChecked() throws Throwable {
      VSBindingFactory<?, ?> factory = chartFactory();
      ChartVSAssembly chart = chart(CUBE_TABLE);
      withCube(false, false, () -> bindingService(factory, queryManager)
         .updateAssembly(bindingModel(CUBE_TABLE), chart, principal));
      verify((VSBindingFactory<ChartVSAssembly, BindingModel>) factory)
         .updateAssembly(any(), same(chart));
      verifyNothingChecked();
   }

   // ---- O: /vs/dataOutput/table/columns ----

   private RuntimeViewsheet runtimeViewsheet(Viewsheet vs, ViewsheetService viewsheetService)
      throws Exception
   {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(viewsheetService.getViewsheet(RUNTIME_ID, principal)).thenReturn(rvs);
      return rvs;
   }

   @Test
   void oUnboundCubeColumnsDenied() throws Throwable {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      runtimeViewsheet(new Viewsheet(), viewsheetService);
      VSOutputService outputService = mock(VSOutputService.class);
      DataOutputService service =
         new DataOutputService(viewsheetService, outputService, queryManager);

      withCube(true, false, () -> assertThrows(java.lang.SecurityException.class,
         () -> service.getOutputTableColumns(RUNTIME_ID, CUBE_TABLE, principal)));
      verifyNoInteractions(outputService);
   }

   @Test
   void oBoundCubeColumnsNotChecked() throws Throwable {
      Viewsheet vs = new Viewsheet();
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      ScalarBindingInfo binding = new ScalarBindingInfo();
      binding.setTableName(CUBE_TABLE);
      gauge.setScalarBindingInfo(binding);
      vs.addAssembly(gauge);
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      runtimeViewsheet(vs, viewsheetService);
      VSOutputService outputService = mock(VSOutputService.class);
      when(outputService.getOutputTableColumns(any(), eq(CUBE_TABLE), anyBoolean(), any()))
         .thenReturn(new ColumnSelection());
      DataOutputService service =
         new DataOutputService(viewsheetService, outputService, queryManager);

      withCube(false, false, () -> service.getOutputTableColumns(RUNTIME_ID, CUBE_TABLE, principal));
      verify(outputService).getOutputTableColumns(any(), eq(CUBE_TABLE), eq(true), eq(principal));
      verifyNothingChecked();
   }

   // ---- C: VS condition browse-data ----

   @Test
   void cBrowseOtherCubeDenied() throws Throwable {
      Viewsheet vs = mock(Viewsheet.class);
      ChartVSAssembly chart = chart(OTHER_TABLE);
      when(vs.getAssembly("Chart1")).thenReturn(chart);
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = runtimeViewsheet(vs, viewsheetService);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      VSConditionDialogService service =
         new VSConditionDialogService(null, null, viewsheetService, null, queryManager);
      DataRefModel ref = mock(DataRefModel.class);

      withCube(true, false, () -> assertThrows(java.lang.SecurityException.class,
         () -> service.browseData(RUNTIME_ID, CUBE_TABLE, "Chart1", false, ref, principal)));
      verifyNoInteractions(ref);
      verify(rvs, never()).getRuntimeWorksheet();
   }

   // ---- G: worksheet grouping dialog onlyFor ----

   private static final String DENIED = "DS1";
   private static final String MODEL = "LM";

   private static AssetEntry modelEntry() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL, DENIED + "/" + MODEL, null);
      entry.setProperty("type", SourceInfo.MODEL + "");
      entry.setProperty("prefix", DENIED);
      entry.setProperty("source", MODEL);
      return entry;
   }

   private GroupingAssemblyDialogService groupingService(Worksheet ws) throws Exception {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getWorksheet(RUNTIME_ID, principal)).thenReturn(rws);
      return new GroupingAssemblyDialogService(viewsheetService, null, null, queryManager);
   }

   private static GroupingAssemblyDialogModel groupingModel() {
      GroupingAssemblyDialogModel model = mock(GroupingAssemblyDialogModel.class);
      when(model.getOldName()).thenReturn("Grouping1");
      when(model.getNewName()).thenReturn("Grouping1");
      when(model.getOnlyFor()).thenReturn(modelEntry());
      when(model.getAttribute()).thenReturn(mock(DataRefModel.class));
      when(model.getConditionExpressions()).thenReturn(new ConditionExpression[0]);
      return model;
   }

   @Test
   void gNewOnlyForCheckedBeforeConditions() throws Exception {
      Worksheet ws = new Worksheet();
      GroupingAssemblyDialogModel model = groupingModel();

      assertThrows(java.lang.SecurityException.class,
                   () -> groupingService(ws).setGroupingAssemblyDialogProperties(
                      RUNTIME_ID, model, principal, mock(CommandDispatcher.class)));
      verify(securityEngine).checkPermission(
         principal, ResourceType.DATA_SOURCE, DENIED, ResourceAction.READ);
      // the group conditions and the attribute are resolved from onlyFor, after the check
      verify(model, never()).getConditionExpressions();
      verify(model, never()).getAttribute();
      assertEquals(0, ws.getAssemblies().length);
   }

   @Test
   void gUnchangedOnlyForNotChecked() throws Exception {
      Worksheet ws = new Worksheet();
      DefaultNamedGroupAssembly grouping = new DefaultNamedGroupAssembly(ws, "Grouping1");
      grouping.setAttachedSource(new SourceInfo(SourceInfo.MODEL, DENIED, MODEL));
      ws.addAssembly(grouping);
      GroupingAssemblyDialogModel model = groupingModel();

      try(MockedStatic<ConditionUtil> conditionUtil = mockStatic(ConditionUtil.class);
          MockedStatic<WorksheetEventUtil> util = mockStatic(WorksheetEventUtil.class))
      {
         conditionUtil.when(() -> ConditionUtil.getOriginalDataRef(any(), any(), any(), any()))
            .thenReturn(new ColumnRef(new AttributeRef("E", "A")));
         groupingService(ws).setGroupingAssemblyDialogProperties(
            RUNTIME_ID, model, principal, mock(CommandDispatcher.class));
         conditionUtil.verify(() -> ConditionUtil.getOriginalDataRef(
            any(), eq(new SourceInfo(SourceInfo.MODEL, DENIED, MODEL)), any(), eq(principal)));
      }

      verifyNothingChecked();
   }

   // ---- W: VSWizardBindingHandler.changeSource ----

   private VSWizardBindingHandler wizardHandler() {
      return new VSWizardBindingHandler(null, null, null, null, null, null, null, null, null,
                                        null, null, null, null, queryManager);
   }

   private static VSTemporaryInfo temporaryInfo(ChartVSAssembly tempChart) {
      VSTemporaryInfo info = mock(VSTemporaryInfo.class);
      when(info.getTempChart()).thenReturn(tempChart);
      return info;
   }

   @Test
   void wDeniedNewCubeLeavesTempChartUnchanged() throws Throwable {
      ChartVSAssembly tempChart = chart(OTHER_TABLE);
      SourceInfo oldSource = tempChart.getSourceInfo();
      withCube(true, false, () -> assertThrows(java.lang.SecurityException.class,
         () -> wizardHandler().changeSource(new SourceInfo(SourceInfo.ASSET, null, CUBE_TABLE),
                                            oldSource, temporaryInfo(tempChart), null, principal)));
      verifyCubeChecked();
      assertEquals(OTHER_TABLE, tempChart.getSourceInfo().getSource());
   }

   @Test
   void wFirstBoundCubeDeniedWithoutDataSourceRead() throws Throwable {
      ChartVSAssembly tempChart = chart(null);
      withCube(false, true, () -> assertThrows(java.lang.SecurityException.class,
         () -> wizardHandler().changeSource(new SourceInfo(SourceInfo.ASSET, null, CUBE_TABLE),
                                            null, temporaryInfo(tempChart), null, principal)));
      assertNull(tempChart.getSourceInfo());
   }

   @Test
   void wUnchangedCubeSourceNotChecked() throws Throwable {
      ChartVSAssembly tempChart = chart(CUBE_TABLE);
      SourceInfo oldSource = tempChart.getSourceInfo();
      withCube(false, false, () -> assertFalse(wizardHandler().changeSource(
         new SourceInfo(SourceInfo.ASSET, null, CUBE_TABLE), oldSource,
         temporaryInfo(tempChart), null, principal)));
      assertSame(oldSource, tempChart.getSourceInfo());
      verifyNothingChecked();
   }
}
