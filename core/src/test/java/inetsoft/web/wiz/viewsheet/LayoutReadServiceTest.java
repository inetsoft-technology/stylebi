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

import inetsoft.graph.internal.DimensionD;
import inetsoft.report.ReportSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.model.vs.ScreensPaneModel;
import inetsoft.web.composer.model.vs.VSPrintLayoutDialogModel;
import inetsoft.web.composer.model.vs.ViewsheetPropertyDialogModel;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.viewsheet.model.DeviceCatalogEntry;
import inetsoft.web.wiz.viewsheet.model.LayoutModel;
import inetsoft.web.wiz.viewsheet.model.LayoutObjectModel;
import inetsoft.web.wiz.viewsheet.model.PrintLayoutSettingsModel;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 1 of the layout implementation plan (2026-08-20-layout-implementation.md), Task 2:
 * {@code list_layouts}/{@code get_layout}. A pure projection -- this service never mints or
 * mutates anything, so Hazard 1 does not apply here the way it does to the mutation tasks; the
 * test that matters instead is that {@code get_layout} reports a layout object's two coordinate
 * spaces (layout position/size vs. the same object's live viewsheet position/size) as genuinely
 * independent readings, and that the device catalogue/{@code editDevicesAllowed} pass through
 * {@link DeviceRegistry} unmodified rather than being re-derived.
 */
@WizAgentTestSupport
class LayoutReadServiceTest {
   private static final Principal AGENT = TestPrincipals.user("alice", "host-org");
   private static final String PRINT_LAYOUT = Catalog.getCatalog().getString("Print Layout");

   @Test
   void listReportsEveryLayoutAndThePassThroughDeviceCatalogue() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();
      fx.installDeviceLayout("Phone", true, "wiz-mobile");

      try(MockedStatic<DeviceRegistry> registryStatic = mockStatic(DeviceRegistry.class)) {
         registryStatic.when(() -> DeviceRegistry.isOrgAllowedToEditDevices(AGENT))
            .thenReturn(true);

         Map<String, Object> result = fx.service.list("tok1", AGENT);

         @SuppressWarnings("unchecked")
         List<Map<String, Object>> layouts = (List<Map<String, Object>>) result.get("layouts");
         assertEquals(2, layouts.size());

         Map<String, Object> print = layouts.stream()
            .filter(l -> PRINT_LAYOUT.equals(l.get("name"))).findFirst().orElseThrow();
         assertEquals("print", print.get("type"));

         Map<String, Object> device = layouts.stream()
            .filter(l -> "Phone".equals(l.get("name"))).findFirst().orElseThrow();
         assertEquals("device", device.get("type"));
         assertEquals(true, device.get("mobileOnly"));
         assertEquals(List.of("wiz-mobile"), device.get("selectedDevices"));

         // devices/editDevicesAllowed come through DeviceRegistry unmodified, not re-derived.
         @SuppressWarnings("unchecked")
         List<DeviceCatalogEntry> devices = (List<DeviceCatalogEntry>) result.get("devices");
         assertEquals(List.of(new DeviceCatalogEntry("wiz-mobile", "Wiz Mobile", 0, 767)),
                      devices);
         assertEquals(true, result.get("editDevicesAllowed"));
      }
   }

   @Test
   void listReportsNoLayoutsOnAFreshViewsheetRatherThanFailing() throws Exception {
      Fixture fx = new Fixture();
      // No installPrintLayout()/installDeviceLayout() call -- a brand-new Viewsheet's LayoutInfo
      // has no print layout configured yet and an empty device-layout list.

      try(MockedStatic<DeviceRegistry> registryStatic = mockStatic(DeviceRegistry.class)) {
         registryStatic.when(() -> DeviceRegistry.isOrgAllowedToEditDevices(AGENT))
            .thenReturn(false);

         Map<String, Object> result = fx.service.list("tok1", AGENT);

         assertTrue(((List<?>) result.get("layouts")).isEmpty());
         assertEquals(false, result.get("editDevicesAllowed"));
      }
   }

   @Test
   void getReportsBothCoordinateSpacesAndSupportsTableLayoutOnlyForTheTableObject()
      throws Exception
   {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      LayoutModel model = fx.service.get("tok1", AGENT, PRINT_LAYOUT);

      assertEquals(PRINT_LAYOUT, model.name());
      assertEquals("print", model.type());
      assertNull(model.mobileOnly(), "a print layout has no mobileOnly flag");
      assertNull(model.selectedDevices(), "a print layout has no device selection");
      assertNull(model.printSettings(),
                 "installPrintLayout's LayoutInfo has no screensPane print settings configured");
      assertEquals(2, model.objects().size());

      LayoutObjectModel text = model.objects().stream()
         .filter(o -> "Text1".equals(o.name())).findFirst().orElseThrow();
      // Text1 was moved in the LAYOUT only (installPrintLayout places it at (100, 200)) -- the
      // viewsheet-space reading must still be its untouched master position (10, 20). If this
      // service ever conflated the two, or read viewsheet-space off a layout-applied clone
      // instead of the master, this would silently start reporting (100, 200) here too.
      assertEquals(100, text.layoutX());
      assertEquals(200, text.layoutY());
      assertEquals(30, text.layoutWidth());
      assertEquals(40, text.layoutHeight());
      assertEquals(10, text.viewsheetX());
      assertEquals(20, text.viewsheetY());
      assertFalse(text.supportsTableLayout(), "a Text object does not support table layout");
      assertEquals(ReportSheet.TABLE_FIT_PAGE, text.tableLayout(),
                   "installPrintLayout never sets Text1's tableLayout -- the class's own default");
      // installPrintLayout's real Letter/0.5in PrintInfo gives a 720-unit page height -- both
      // y=200 and y=400 land on page 1 at that height (ceil(200/720) == ceil(400/720) == 1).
      assertEquals(1, text.pageIndex());

      LayoutObjectModel table = model.objects().stream()
         .filter(o -> "Table1".equals(o.name())).findFirst().orElseThrow();
      assertEquals(300, table.layoutX());
      assertEquals(400, table.layoutY());
      assertEquals(50, table.viewsheetX());
      assertEquals(60, table.viewsheetY());
      assertTrue(table.supportsTableLayout(),
                 "a TableDataVSAssembly-backed object supports table layout");
      assertEquals(ReportSheet.TABLE_EQUAL_WIDTH, table.tableLayout(),
                   "installPrintLayout explicitly sets Table1's tableLayout to a non-default value");
      assertEquals(1, table.pageIndex());
   }

   @Test
   void getReportsDifferentPageIndicesForObjectsOnDifferentPages() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayoutSpanningTwoPages();

      LayoutModel model = fx.service.get("tok1", AGENT, PRINT_LAYOUT);

      LayoutObjectModel text = model.objects().stream()
         .filter(o -> "Text1".equals(o.name())).findFirst().orElseThrow();
      assertEquals(1, text.pageIndex(), "y=200 is within the first 720-unit page");

      LayoutObjectModel table = model.objects().stream()
         .filter(o -> "Table1".equals(o.name())).findFirst().orElseThrow();
      assertEquals(2, table.pageIndex(), "y=800 is past the first page's 720-unit height");
   }

   @Test
   void getReportsNullPageIndexWhenThePrintLayoutHasNeverHadPrintInfoConfigured()
      throws Exception
   {
      Fixture fx = new Fixture();
      fx.installPrintLayoutWithoutPrintInfo();

      LayoutModel model = fx.service.get("tok1", AGENT, PRINT_LAYOUT);

      LayoutObjectModel text = model.objects().stream()
         .filter(o -> "Text1".equals(o.name())).findFirst().orElseThrow();
      assertNull(text.pageIndex(),
                 "no PrintInfo means page size can't be computed -- null, not a sentinel int");
   }

   @Test
   void getReportsNullPageIndexForEveryObjectInADeviceLayout() throws Exception {
      Fixture fx = new Fixture();
      fx.installDeviceLayoutWithObject("Phone", "Text1");

      LayoutModel model = fx.service.get("tok1", AGENT, "Phone");

      assertEquals(1, model.objects().size());
      assertNull(model.objects().get(0).pageIndex(), "a device layout has no pages");
   }

   @Test
   void getPopulatesPrintSettingsWhenTheViewsheetsPrintLayoutIsConfigured() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      VSPrintLayoutDialogModel configured = new VSPrintLayoutDialogModel();
      configured.setPaperSize("Letter");
      configured.setScaleFont(0.8f);
      configured.setMarginTop(1.0);
      configured.setLandscape(true);
      ScreensPaneModel screensPane = new ScreensPaneModel();
      screensPane.setPrintLayout(configured);
      fx.withScreensPane(screensPane);

      LayoutModel model = fx.service.get("tok1", AGENT, PRINT_LAYOUT);

      PrintLayoutSettingsModel printSettings = model.printSettings();
      assertNotNull(printSettings);
      assertEquals("Letter", printSettings.paperSize());
      assertEquals(0.8f, printSettings.scaleFont(), 0.0001f);
      assertEquals(1.0, printSettings.marginTop(), 0.0001);
      assertTrue(printSettings.landscape());
   }

   @Test
   void getReturnsNullPrintSettingsRatherThanAZeroedOutRecordWhenNeverConfigured()
      throws Exception
   {
      Fixture fx = new Fixture();
      fx.installPrintLayout();
      // Fixture's default screensPane has no print layout configured -- see its constructor.

      LayoutModel model = fx.service.get("tok1", AGENT, PRINT_LAYOUT);

      assertNull(model.printSettings());
   }

   @Test
   void getRoundTripsWhatSetPrintLayoutActuallyWrote() throws Exception {
      // The exact scenario live-caught during L11 testing: set_print_layout claims success but
      // get_layout has no field to verify it against. Wires PrintDeviceLayoutPropertyService and
      // LayoutReadService against the SAME ViewsheetPropertyDialogService mock, letting a
      // setViewsheetInfo call feed back into the next getViewsheetInfo call, so this is a genuine
      // write-then-read round trip rather than two independently-stubbed halves.
      ViewsheetSessionService writeSessions = mock(ViewsheetSessionService.class);
      ViewsheetPropertyDialogService dialogService = mock(ViewsheetPropertyDialogService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      PrintDeviceLayoutPropertyService writeService = new PrintDeviceLayoutPropertyService(
         writeSessions, dialogService, mock(DeviceRegistry.class));

      ScreensPaneModel[] screensPaneHolder = { new ScreensPaneModel() };
      when(dialogService.getViewsheetInfo(eq("rt1"), eq(AGENT))).thenAnswer(
         invocation -> ViewsheetPropertyDialogModel.builder()
            .screensPane(screensPaneHolder[0]).build());
      doAnswer(invocation -> {
         ViewsheetPropertyDialogModel written = invocation.getArgument(1);
         screensPaneHolder[0] = written.screensPane();
         return null;
      }).when(dialogService).setViewsheetInfo(eq("rt1"), any(), eq(AGENT), any(), anyString(),
                                              any());
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(writeSessions).mutate(anyString(), eq(AGENT), any());

      writeService.setPrintLayout("tok1", AGENT, Map.of("paperSize", "Letter", "scaleFont", 0.8f),
                                  "");

      Fixture fx = new Fixture();
      fx.installPrintLayout();
      when(fx.viewsheetSessions.runtimeId(eq("tok1"), eq(AGENT))).thenReturn("rt1");
      LayoutReadService readService = new LayoutReadService(fx.viewsheetSessions,
         fx.layoutSessions, fx.vsLayoutService, fx.deviceRegistry, dialogService);

      LayoutModel model = readService.get("tok1", AGENT, PRINT_LAYOUT);

      PrintLayoutSettingsModel printSettings = model.printSettings();
      assertNotNull(printSettings);
      assertEquals("Letter", printSettings.paperSize());
      assertEquals(0.8f, printSettings.scaleFont(), 0.0001f);
   }

   @Test
   void getValidatesTheLayoutNameThroughLayoutSessionService() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();
      when(fx.layoutSessions.resolveForRead(eq("tok1"), eq(AGENT), eq("Does Not Exist")))
         .thenThrow(new IllegalArgumentException(
            "Unknown layout \"Does Not Exist\" -- call list_layouts to see the print and " +
            "device layouts defined on this viewsheet."));

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> fx.service.get("tok1", AGENT, "Does Not Exist"));

      assertTrue(thrown.getMessage().contains("Does Not Exist"), thrown.getMessage());
   }

   // ── fixture ───────────────────────────────────────────────────────────────

   /**
    * Wires one {@code LayoutReadService} against a mocked {@code ViewsheetSessionService}/
    * {@code LayoutSessionService}/{@code DeviceRegistry} and a real {@code VSLayoutService},
    * backed by a real master {@code Viewsheet}.
    */
   private static final class Fixture {
      final ViewsheetSessionService viewsheetSessions = mock(ViewsheetSessionService.class);
      final LayoutSessionService layoutSessions = mock(LayoutSessionService.class);
      final VSLayoutService vsLayoutService = new VSLayoutService();
      final DeviceRegistry deviceRegistry = mock(DeviceRegistry.class);
      final ViewsheetPropertyDialogService dialogService =
         mock(ViewsheetPropertyDialogService.class);
      final LayoutReadService service = new LayoutReadService(
         viewsheetSessions, layoutSessions, vsLayoutService, deviceRegistry, dialogService);

      final Viewsheet masterVs = new Viewsheet();
      final RuntimeViewsheet masterRvs = mock(RuntimeViewsheet.class);

      Fixture() throws Exception {
         when(masterRvs.getViewsheet()).thenReturn(masterVs);
         when(viewsheetSessions.resolve(eq("tok1"), eq(AGENT))).thenReturn(masterRvs);
         when(viewsheetSessions.runtimeId(eq("tok1"), eq(AGENT))).thenReturn("rt1");
         // resolveForRead's own clone content is never used by LayoutReadService -- both
         // coordinate-space readings come off the master directly (see the class doc) -- so a
         // bare mock standing in for "the layout name is known" is enough here.
         when(layoutSessions.resolveForRead(anyString(), eq(AGENT), anyString()))
            .thenReturn(mock(RuntimeViewsheet.class));
         // No print layout configured by default -- individual tests override this via
         // withPrintSettings below.
         withScreensPane(new ScreensPaneModel());

         DeviceInfo mobile = new DeviceInfo();
         mobile.setId("wiz-mobile");
         mobile.setName("Wiz Mobile");
         mobile.setMinWidth(0);
         mobile.setMaxWidth(767);
         when(deviceRegistry.getDevices()).thenReturn(new DeviceInfo[] { mobile });
      }

      /** Stubs {@code dialogService.getViewsheetInfo} to read back {@code screensPane}. */
      void withScreensPane(ScreensPaneModel screensPane) throws Exception {
         ViewsheetPropertyDialogModel model =
            ViewsheetPropertyDialogModel.builder().screensPane(screensPane).build();
         when(dialogService.getViewsheetInfo(eq("rt1"), eq(AGENT))).thenReturn(model);
      }

      /**
       * A real print layout with a Text object (does not support table layout) and a Table
       * object (does), each placed at a distinct layout position from its own master/viewsheet
       * position -- the fixture data the coordinate-space-independence test depends on.
       */
      void installPrintLayout() {
         TextVSAssembly text = new TextVSAssembly(masterVs, "Text1");
         text.setPixelOffset(new Point(10, 20));
         text.setPixelSize(new Dimension(15, 10));
         masterVs.addAssembly(text);

         TableVSAssembly table = new TableVSAssembly(masterVs, "Table1");
         table.setPixelOffset(new Point(50, 60));
         table.setPixelSize(new Dimension(80, 40));
         masterVs.addAssembly(table);

         PrintLayout layout = new PrintLayout();
         layout.setPrintInfo(new PrintInfo("Letter", new DimensionD(8.5, 11),
                                            0.5f, 0.5f, 0.5f, 0.5f, "inches"));
         List<VSAssemblyLayout> objects = new ArrayList<>();
         objects.add(new VSAssemblyLayout("Text1", new Point(100, 200), new Dimension(30, 40)));
         VSAssemblyLayout tableLayout =
            new VSAssemblyLayout("Table1", new Point(300, 400), new Dimension(50, 60));
         // Explicit, non-default value -- TABLE_FIT_PAGE (1) is VSAssemblyLayout's own default,
         // so leaving this unset would let the read-back assertion pass even if the service read
         // nothing at all.
         tableLayout.setTableLayout(ReportSheet.TABLE_EQUAL_WIDTH);
         objects.add(tableLayout);
         layout.setVSAssemblyLayouts(objects);

         masterVs.getLayoutInfo().setPrintLayout(layout);
      }

      /**
       * A print layout using the same real Letter/0.5in PrintInfo as {@link #installPrintLayout},
       * but with {@code Table1} moved past the first page boundary -- {@code Text1} stays at
       * {@code y=200} (page 1), {@code Table1} moves to {@code y=800} (page 2, since the fixed
       * 720-unit page height means {@code ceil(800/720) == 2}) -- so {@code pageIndex} actually
       * differs between the two objects, unlike {@link #installPrintLayout}'s own {@code
       * y=200}/{@code y=400} (both page 1 at this page height).
       */
      void installPrintLayoutSpanningTwoPages() {
         TextVSAssembly text = new TextVSAssembly(masterVs, "Text1");
         text.setPixelOffset(new Point(10, 20));
         text.setPixelSize(new Dimension(15, 10));
         masterVs.addAssembly(text);

         TableVSAssembly table = new TableVSAssembly(masterVs, "Table1");
         table.setPixelOffset(new Point(50, 60));
         table.setPixelSize(new Dimension(80, 40));
         masterVs.addAssembly(table);

         PrintLayout layout = new PrintLayout();
         layout.setPrintInfo(new PrintInfo("Letter", new DimensionD(8.5, 11),
                                            0.5f, 0.5f, 0.5f, 0.5f, "inches"));
         List<VSAssemblyLayout> objects = new ArrayList<>();
         objects.add(new VSAssemblyLayout("Text1", new Point(100, 200), new Dimension(30, 40)));
         objects.add(new VSAssemblyLayout("Table1", new Point(300, 800), new Dimension(50, 60)));
         layout.setVSAssemblyLayouts(objects);

         masterVs.getLayoutInfo().setPrintLayout(layout);
      }

      /**
       * A print layout that has never had its {@code PrintInfo} configured -- {@code pageIndex}
       * must read back {@code null} for every object here, the same "unconfigured, not zeroed"
       * convention {@code printSettings} already follows.
       */
      void installPrintLayoutWithoutPrintInfo() {
         TextVSAssembly text = new TextVSAssembly(masterVs, "Text1");
         text.setPixelOffset(new Point(10, 20));
         text.setPixelSize(new Dimension(15, 10));
         masterVs.addAssembly(text);

         PrintLayout layout = new PrintLayout();
         // No setPrintInfo call -- this is the "never configured" case.
         List<VSAssemblyLayout> objects = new ArrayList<>();
         objects.add(new VSAssemblyLayout("Text1", new Point(100, 200), new Dimension(30, 40)));
         layout.setVSAssemblyLayouts(objects);

         masterVs.getLayoutInfo().setPrintLayout(layout);
      }

      /** Adds a real device (viewsheet) layout named {@code name} to the master's LayoutInfo. */
      void installDeviceLayout(String name, boolean mobileOnly, String... deviceIds) {
         LayoutInfo info = masterVs.getLayoutInfo();
         ViewsheetLayout layout = new ViewsheetLayout();
         layout.setName(name);
         layout.setMobileOnly(mobileOnly);
         layout.setDeviceIds(deviceIds);
         layout.setVSAssemblyLayouts(new ArrayList<>());
         List<ViewsheetLayout> layouts = new ArrayList<>(info.getViewsheetLayouts());
         layouts.add(layout);
         info.setViewsheetLayouts(layouts);
      }

      /**
       * A device (viewsheet) layout named {@code name} with one placed object -- for asserting
       * {@code pageIndex} is {@code null} on a device layout's own objects (a device layout has
       * no pages), which {@link #installDeviceLayout}'s empty object list can't exercise.
       */
      void installDeviceLayoutWithObject(String name, String objectName) {
         TextVSAssembly text = new TextVSAssembly(masterVs, objectName);
         text.setPixelOffset(new Point(10, 20));
         text.setPixelSize(new Dimension(15, 10));
         masterVs.addAssembly(text);

         LayoutInfo info = masterVs.getLayoutInfo();
         ViewsheetLayout layout = new ViewsheetLayout();
         layout.setName(name);
         layout.setMobileOnly(false);
         layout.setDeviceIds(new String[0]);
         List<VSAssemblyLayout> objects = new ArrayList<>();
         objects.add(new VSAssemblyLayout(objectName, new Point(50, 60), new Dimension(30, 40)));
         layout.setVSAssemblyLayouts(objects);
         List<ViewsheetLayout> layouts = new ArrayList<>(info.getViewsheetLayouts());
         layouts.add(layout);
         info.setViewsheetLayouts(layouts);
      }
   }
}
