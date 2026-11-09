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
package inetsoft.web.wiz.viewsheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.controller.ClipboardControllerService;
import inetsoft.web.composer.vs.objects.controller.ComposerObjectService;
import inetsoft.web.composer.vs.objects.controller.ComposerGroupService;
import inetsoft.web.composer.vs.objects.controller.GroupingService;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.controller.VSCalendarService;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.event.ImmutableVSEditBookmarkEvent;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.event.VSEditBookmarkEvent;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.report.composition.WorksheetService;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78406: the services behind the remaining write endpoints that re-run a table now hand
 * back the warning {@link ViewsheetSessionService#mutate} captured. Each test runs the REAL
 * service on the real stack (EmbeddedVS1, a table whose JS pre-condition throws, the real
 * {@code CoreLifecycleService} and property/bookmark/calendar services) through a seam that does
 * what {@code mutate} does around the mutation -- the capturing dispatcher -- and asserts the
 * service returns the script failure. A clean-state control asserts nothing is returned when the
 * table's script is fine.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ScriptFailureRelayRealStackTest {
   @Test
   void gotoBookmarkReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      VSBookmarkService bookmarks = saveBookmark(rvs);

      List<String> warnings = seam(rvs).mutate("tok", principal(), (r, id, d) ->
         bookmarks.gotoBookmark(id, bookmarkEvent(), principal(), d, ""));

      assertFailureRelayed(warnings);
   }

   @Test
   void gotoBookmarkIsSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();
      VSBookmarkService bookmarks = saveBookmark(rvs);

      List<String> warnings = seam(rvs).mutate("tok", principal(), (r, id, d) ->
         bookmarks.gotoBookmark(id, bookmarkEvent(), principal(), d, ""));

      assertEquals(List.of(), warnings);
   }

   /** set_viewsheet_properties and set_viewsheet_data_source share setViewsheetInfo. */
   @Test
   void setViewsheetPropertiesReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      SheetPropertyService service =
         new SheetPropertyService(seam(rvs), viewsheetPropertyDialogService());

      List<String> warnings =
         service.set("tok", principal(), Map.of("desc", "relay probe"), "");

      assertFailureRelayed(warnings);
   }

   @Test
   void setViewsheetPropertiesIsSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();
      SheetPropertyService service =
         new SheetPropertyService(seam(rvs), viewsheetPropertyDialogService());

      assertEquals(List.of(),
                   service.set("tok", principal(), Map.of("desc", "relay probe"), ""));
   }

   @Test
   void setDataSourceReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      SheetPropertyService service =
         new SheetPropertyService(seam(rvs), viewsheetPropertyDialogService());

      // Re-selecting the current base is the same setViewsheetInfo write the endpoint makes.
      assertFailureRelayed(
         service.setDataSource("tok", principal(), rvs.getViewsheet().getBaseEntry(), ""));
   }

   @Test
   void setAssemblyPropertiesOnATableReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);

      List<String> warnings = assemblyPropertyService(rvs)
         .set("tok", principal(), TABLE, Map.of("title", "relay probe"), "");

      assertFailureRelayed(warnings);
   }

   @Test
   void setAssemblyPropertiesOnATableIsSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();

      assertEquals(List.of(), assemblyPropertyService(rvs)
         .set("tok", principal(), TABLE, Map.of("title", "relay probe"), ""));
   }

   @Test
   void calendarDisplayReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      addCalendar(rvs);

      Map<String, Object> result = calendarService(rvs)
         .setDisplay("tok", principal(), CALENDAR, null, true, null, null, null, "");

      assertFailureRelayed(warningsOf(result));
   }

   @Test
   void calendarDisplayIsSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();
      addCalendar(rvs);

      Map<String, Object> result = calendarService(rvs)
         .setDisplay("tok", principal(), CALENDAR, null, true, null, null, null, "");

      assertFalse(result.containsKey("warnings"), result.toString());
   }

   @Test
   void calendarClearReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      addCalendar(rvs);

      assertFailureRelayed(
         warningsOf(calendarService(rvs).clear("tok", principal(), CALENDAR, "")));
   }

   @Test
   void calendarDatesReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      addCalendar(rvs);

      // "y2000" is the encoded whole-year token the calendar's own widget sends.
      Map<String, Object> result = calendarService(rvs).setDates(
         "tok", principal(), CALENDAR, List.of("y2000"), "");

      assertFailureRelayed(warningsOf(result));
   }

   /** Removing the selection list re-runs the table that was filtered by it. */
   @Test
   void editRemoveReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      addSelectionList(rvs);

      List<String> warnings = editService(rvs).apply(
         "tok", principal(), editRequest("remove", SELECTION), "").warnings();

      assertFailureRelayed(warnings);
   }

   @Test
   void editRemoveIsSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();
      addSelectionList(rvs);

      assertEquals(List.of(), editService(rvs).apply(
         "tok", principal(), editRequest("remove", SELECTION), "").warnings());
   }

   @Test
   void editCutReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      addSelectionList(rvs);

      List<String> warnings = editService(rvs).apply(
         "tok", principal(),
         new EditRequest("cut", null, null, null, null, null, null, null, null, null,
                         List.of(SELECTION), null, null, null), "").warnings();

      assertFailureRelayed(warnings);
   }

   /**
    * Bug #78406 review F1: resize warms the table with a bare lens read, which rethrew the
    * ScriptException of a failing table (a masked 500) on the first call and succeeded on the
    * second off the cached null. Neither outcome may fail the resize.
    */
   @Test
   void editResizeOnAFailingTableSucceedsOnTheFirstReadThrowAndTheCachedNull() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      ViewsheetEditService service = editService(rvs);

      for(int attempt = 0; attempt < 2; attempt++) {
         assertDoesNotThrow(() -> service.apply(
            "tok", principal(),
            new EditRequest("resize", TABLE, null, null, 320, 210, null, null, null, null, null,
                            null, null, null), ""));
      }
   }

   /**
    * Bug #80820: an assembly script on a table that depends on the filter being written. An
    * undefined name is wrapped as ScriptException(UndefinedNameException), which the table path
    * used to unwrap past, so the write threw "Error loading data" instead of returning a warning.
    */
   @Test
   void calendarDatesWithAnUndefinedNameScriptReturnsAWarning() throws Exception {
      assertDatesWarn(UNDEFINED_NAME_SCRIPT, "ReferenceError");
   }

   @Test
   void calendarDatesWithATypeErrorScriptReturnsAWarning() throws Exception {
      assertDatesWarn(TYPE_ERROR_SCRIPT, "TypeError");
   }

   @Test
   void calendarClearWithAnUndefinedNameScriptReturnsAWarning() throws Exception {
      assertClearWarns(UNDEFINED_NAME_SCRIPT, "ReferenceError");
   }

   @Test
   void calendarClearWithATypeErrorScriptReturnsAWarning() throws Exception {
      assertClearWarns(TYPE_ERROR_SCRIPT, "TypeError");
   }

   @Test
   void calendarYearViewWithAnUndefinedNameScriptReturnsOneWarning() throws Exception {
      assertYearViewWarns(UNDEFINED_NAME_SCRIPT, "ReferenceError");
   }

   @Test
   void calendarYearViewWithATypeErrorScriptReturnsOneWarning() throws Exception {
      assertYearViewWarns(TYPE_ERROR_SCRIPT, "TypeError");
   }

   @Test
   void calendarYearViewWithAThrowScriptReturnsOneWarning() throws Exception {
      assertYearViewWarns("throw 'boom';", "boom");
   }

   @Test
   void setSelectionWithAnUndefinedNameScriptReturnsAWarning() throws Exception {
      RuntimeViewsheet rvs = open();
      addSelectionList(rvs);
      String value = firstValue(rvs);
      setTableScript(rvs, UNDEFINED_NAME_SCRIPT);
      SelectionRuntimeService service = new SelectionRuntimeService(
         seam(rvs), selections, new VSCalendarService(
            coreLifecycleService, objectPropertyService(), viewsheetService));

      Map<String, Object> result = service.setSelection(
         "tok", principal(), SELECTION, List.of(List.of(value)), null, null, null, null, "");

      assertScriptWarning(warningsOf(result), "ReferenceError");
   }

   /** Not a calendar: any wiz write that drains the sandbox's script failures is downgraded. */
   @Test
   void setAssemblyPropertiesWithAScriptThatThrowsReturnsAWarning() throws Exception {
      RuntimeViewsheet rvs = open();
      setTableScript(rvs, TYPE_ERROR_SCRIPT);

      List<String> warnings = assemblyPropertyService(rvs)
         .set("tok", principal(), TABLE, Map.of("title", "relay probe"), "");

      assertScriptWarning(warnings, "TypeError");
   }

   @Test
   void setAssemblyPropertiesWithAnUndefinedNameScriptReturnsOneWarning() throws Exception {
      RuntimeViewsheet rvs = open();
      setTableScript(rvs, UNDEFINED_NAME_SCRIPT);

      List<String> warnings = assemblyPropertyService(rvs)
         .set("tok", principal(), TABLE, Map.of("title", "relay probe"), "");

      assertScriptWarning(warnings, "ReferenceError");
   }

   private void assertDatesWarn(String script, String error) throws Exception {
      RuntimeViewsheet rvs = open();
      addCalendar(rvs);
      setTableScript(rvs, script);

      Map<String, Object> result =
         calendarService(rvs).setDates("tok", principal(), CALENDAR, List.of("y2000"), "");

      assertScriptWarning(warningsOf(result), error);
   }

   private void assertClearWarns(String script, String error) throws Exception {
      RuntimeViewsheet rvs = open();
      addCalendar(rvs);
      setTableScript(rvs, script);

      assertScriptWarning(
         warningsOf(calendarService(rvs).clear("tok", principal(), CALENDAR, "")), error);
   }

   private void assertYearViewWarns(String script, String error) throws Exception {
      RuntimeViewsheet rvs = open();
      addCalendar(rvs);
      setTableScript(rvs, script);

      Map<String, Object> result = calendarService(rvs)
         .setDisplay("tok", principal(), CALENDAR, null, true, null, null, null, "");

      assertScriptWarning(warningsOf(result), error);
   }

   /** Exactly one warning for the table, carrying the engine's own error text. */
   private static void assertScriptWarning(List<String> warnings, String error) {
      assertEquals(1, warnings.size(), warnings.toString());
      String warning = warnings.get(0);
      assertTrue(warning.startsWith(BaseTableService.SCRIPT_FAILURE_PREFIX + TABLE), warning);
      assertTrue(warning.contains(error), warning);
      assertEquals(warning.indexOf("Script failed:"), warning.lastIndexOf("Script failed:"),
                   "the script failure must not be wrapped inside itself: " + warning);
   }

   /** A value the selection list really holds, read before any script is installed. */
   private static String firstValue(RuntimeViewsheet rvs) throws Exception {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      box.lockWrite();

      try {
         box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      }
      finally {
         box.unlockWrite();
      }

      SelectionListVSAssembly list = (SelectionListVSAssembly) vs.getAssembly(SELECTION);
      return list.getSelectionList().getSelectionValues()[0].getValue();
   }

   /** Installs an assembly script on the table and drops what an earlier call left behind. */
   private static void setTableScript(RuntimeViewsheet rvs, String script) {
      WorksheetService.ASSET_EXCEPTIONS.remove();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      TableVSAssembly table = (TableVSAssembly) box.getViewsheet().getAssembly(TABLE);
      VSAssemblyInfo info = table.getVSAssemblyInfo();
      info.setScriptEnabled(true);
      info.setScript(script);
      box.resetDataMap(TABLE);
   }

   @AfterEach
   void clearAssetExceptions() {
      WorksheetService.ASSET_EXCEPTIONS.remove();
   }

   @SuppressWarnings("unchecked")
   private static List<String> warningsOf(Map<String, Object> result) {
      Object warnings = result.get("warnings");
      return warnings == null ? List.of() : (List<String>) warnings;
   }

   private static void assertFailureRelayed(List<String> warnings) {
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).startsWith(BaseTableService.SCRIPT_FAILURE_PREFIX + TABLE),
                 warnings.get(0));
      assertTrue(warnings.get(0).contains("undefinedFn"), warnings.get(0));
   }

   /**
    * Does around the mutation what {@link ViewsheetSessionService#mutate} does (run it under the
    * capturing dispatcher, return its warnings); the checkpoint and the broadcast are not part of
    * what is under test.
    */
   private static ViewsheetSessionService seam(RuntimeViewsheet rvs) throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
         Principal agent = inv.getArgument(1);
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(agent, dispatcher -> {
            mutation.run(rvs, rvs.getID(), dispatcher);
            return dispatcher.getWarnings();
         });
      });
      return sessions;
   }

   private VSBookmarkService saveBookmark(RuntimeViewsheet rvs) throws Exception {
      rvs.addBookmark(BOOKMARK, VSBookmarkInfo.PRIVATE, owner(), false);
      ScheduleManager schedules = mock(ScheduleManager.class);
      when(schedules.getScheduleTasks()).thenReturn(new Vector<>());
      return new VSBookmarkService(objectService, viewsheetService, securityEngine, schedules,
                                   cluster, xSessionService);
   }

   private VSEditBookmarkEvent bookmarkEvent() {
      return ImmutableVSEditBookmarkEvent.builder()
         .vsBookmarkInfoModel(
            VSBookmarkInfoModel.builder().name(BOOKMARK).owner(owner()).build())
         .confirmed(false)
         .build();
   }

   private IdentityID owner() {
      return IdentityID.getIdentityIDFromKey(principal().getName());
   }

   private ViewsheetPropertyDialogService viewsheetPropertyDialogService() {
      DeviceRegistry devices = mock(DeviceRegistry.class);
      when(devices.getDevices()).thenReturn(new DeviceInfo[0]);

      return new ViewsheetPropertyDialogService(
         coreLifecycleService, viewsheetService, new VSLayoutService(),
         new ViewsheetSettingsService(viewsheetService), mock(VSAssemblyInfoHandler.class),
         securityEngine, mock(MVManager.class), devices,
         mock(QueryManagerService.class));
   }

   private VSObjectPropertyService objectPropertyService() {
      return new VSObjectPropertyService(
         coreLifecycleService, mock(VSColumnHandler.class), treeService,
         mock(VSAssemblyInfoHandler.class), mock(VSWizardTemporaryInfoService.class),
         compositionService, sharedFilterService, dataSourceRegistry,
         mock(QueryManagerService.class));
   }

   private AssemblyPropertyService assemblyPropertyService(RuntimeViewsheet rvs) throws Exception {
      // The property model needs a geometry; the mock dialog service supplies a fixed one.
      VSDialogService dialogs = mock(VSDialogService.class);
      when(dialogs.getAssemblyPosition(any(), any())).thenReturn(new Point(10, 10));
      when(dialogs.getAssemblySize(any(), any())).thenReturn(new Dimension(300, 200));
      TableViewPropertyDialogService tableService = new TableViewPropertyDialogService(
         objectPropertyService(), dialogs, viewsheetService);

      return new AssemblyPropertyService(
         seam(rvs), mock(GaugePropertyDialogService.class),
         mock(ImagePropertyDialogService.class), mock(TextPropertyDialogService.class),
         mock(ChartPropertyDialogService.class), tableService,
         mock(CrosstabPropertyDialogService.class), mock(SelectionListPropertyDialogService.class),
         mock(SelectionTreePropertyDialogService.class), mock(VSInputService.class),
         mock(RangeSliderPropertyDialogService.class), mock(CalendarPropertyDialogService.class),
         mock(TabPropertyDialogService.class), mock(CalcTablePropertyDialogService.class),
         mock(GroupContainerPropertyDialogService.class), mock(LinePropertyDialogService.class),
         mock(OvalPropertyDialogService.class), mock(RectanglePropertyDialogService.class),
         mock(SelectionContainerPropertyDialogService.class),
         mock(SubmitPropertyDialogService.class), mock(VSOutputService.class),
         mock(VSColumnHandler.class), mock(DataOutputService.class),
         mock(ViewsheetObjectPropertyDialogService.class));
   }

   private ViewsheetEditService editService(RuntimeViewsheet rvs) throws Exception {
      ViewsheetReadService reader = mock(ViewsheetReadService.class);
      when(reader.read(any())).thenReturn(new ViewsheetModel("vs", List.of(
         new AssemblyNode(SELECTION, "SelectionList", 0, 0, 100, 100, 0, null, true),
         new AssemblyNode(TABLE, "Table", 0, 0, 300, 200, 0, null, true))));
      VSObjectPropertyService properties = objectPropertyService();
      ComposerObjectService objects = new ComposerObjectService(
         treeService, coreLifecycleService, viewsheetService, mock(VSAssemblyInfoHandler.class),
         objectModelFactoryService, objectService, compositionService);
      ClipboardControllerService clipboard = new ClipboardControllerService(
         coreLifecycleService, treeService, viewsheetService, mock(VSAssemblyInfoHandler.class),
         properties);

      return new ViewsheetEditService(
         seam(rvs), objects, clipboard, properties, reader, mock(ComposerGroupService.class),
         mock(GroupingService.class), mock(VSRefreshService.class), coreLifecycleService);
   }

   private static EditRequest editRequest(String op, String assembly) {
      return new EditRequest(op, assembly, null, null, null, null, null, null, null, null, null,
                             null, null, null);
   }

   /** A selection list on the same worksheet table the failing TableView1 reads. */
   private static void addSelectionList(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, SELECTION);
      list.setTableName(table.getTableName());
      list.setDataRef(table.getColumnSelection().getAttribute("type"));
      vs.addAssembly(list);
   }

   private CalendarDisplayService calendarService(RuntimeViewsheet rvs) throws Exception {
      return new CalendarDisplayService(
         seam(rvs),
         new VSCalendarService(coreLifecycleService, objectPropertyService(), viewsheetService));
   }

   /** A calendar on the same worksheet table the failing TableView1 reads. */
   private static void addCalendar(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);

      // The table has no date column; re-type its double "datetime" column so a calendar can bind.
      ColumnRef date = (ColumnRef) table.getColumnSelection().getAttribute("datetime").clone();
      date.setDataType(XSchema.DATE);

      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, CALENDAR);
      calendar.setTableName(table.getTableName());
      calendar.setDataRef(date);
      vs.addAssembly(calendar);
   }

   private static void addFailingExpressionCondition(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      TableVSAssembly table = (TableVSAssembly) box.getViewsheet().getAssembly(TABLE);
      DataRef type = table.getColumnSelection().getAttribute("type");
      assertNotNull(type, "type column");

      ExpressionValue expression = new ExpressionValue();
      expression.setExpression("undefinedFn(1)");
      expression.setType(ExpressionValue.JAVASCRIPT);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.STRING);
      cond.addValue(expression);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(type, cond, 0));
      table.setPreConditionList(list);
      box.resetDataMap(TABLE);
   }

   /** The runtime plus the principal it was opened with; a different one is "Invalid user". */
   private RuntimeViewsheet open() {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      user = rvs.getUser();
      return rvs;
   }

   private XPrincipal principal() {
      return (XPrincipal) user;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired ViewsheetService viewsheetService;
   @Autowired CoreLifecycleService coreLifecycleService;
   @Autowired VSObjectService objectService;
   @Autowired SecurityEngine securityEngine;
   @Autowired Cluster cluster;
   @Autowired XSessionService xSessionService;
   @Autowired VSObjectTreeService treeService;
   @Autowired VSCompositionService compositionService;
   @Autowired SharedFilterService sharedFilterService;
   @Autowired DataSourceRegistry dataSourceRegistry;
   @Autowired VSObjectModelFactoryService objectModelFactoryService;
   @Autowired VSSelectionService selections;

   private Principal user;
   private static final String TABLE = "TableView1";
   private static final String CALENDAR = "Calendar1";
   private static final String SELECTION = "SelectionList1";
   private static final String UNDEFINED_NAME_SCRIPT = "TableView1.title = undefinedFunctionXYZ('test');";
   private static final String TYPE_ERROR_SCRIPT = "var x = null; x.foo();";
   private static final String BOOKMARK = "relay78406";
}
