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
         .setDisplay("tok", principal(), CALENDAR, null, true, null, "");

      assertFailureRelayed(warningsOf(result));
   }

   @Test
   void calendarDisplayIsSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();
      addCalendar(rvs);

      Map<String, Object> result = calendarService(rvs)
         .setDisplay("tok", principal(), CALENDAR, null, true, null, "");

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
         securityEngine, mock(MVManager.class), devices);
   }

   private VSObjectPropertyService objectPropertyService() {
      return new VSObjectPropertyService(
         coreLifecycleService, mock(VSColumnHandler.class), treeService,
         mock(VSAssemblyInfoHandler.class), mock(VSWizardTemporaryInfoService.class),
         compositionService, sharedFilterService, dataSourceRegistry);
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
         mock(SubmitPropertyDialogService.class), mock(VSOutputService.class));
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
      DataRef date = null;

      // The table has no date column; re-type one numeric column so the calendar can bind to it.
      ColumnRef column = (ColumnRef) table.getColumnSelection().getAttribute("datetime").clone();
      column.setDataType(XSchema.DATE);
      date = column;

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

   private Principal user;
   private static final String TABLE = "TableView1";
   private static final String CALENDAR = "Calendar1";
   private static final String BOOKMARK = "relay78406";
}
