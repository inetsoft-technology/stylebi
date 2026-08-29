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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 2 of the layout implementation plan (2026-08-20-layout-implementation.md), Task 3:
 * {@code set_print_layout}/{@code manage_device_layout}, patching {@code ScreensPaneModel} inside
 * the existing {@code ViewsheetPropertyDialogModel} via the same {@code
 * ViewsheetPropertyDialogService.getViewsheetInfo}/{@code setViewsheetInfo} pair {@code
 * SheetPropertyService} already wraps.
 *
 * <p>This task operates on the paired session's master runtime directly through {@code
 * ViewsheetSessionService} -- {@code screensPane} is a whole-viewsheet property, not layout-object
 * geometry, so Hazard 1 (and {@code LayoutSessionService}) does not apply here.
 *
 * <p>Two named regressions this file exists to pin down:
 * <ul>
 *   <li><b>Hazard 3</b> -- {@code VSPrintLayoutDialogModel.scaleFont} is a bare {@code float},
 *   defaulting to Java's {@code 0.0f}. {@code VSCompositeFormat.getFont()} multiplies every cell's
 *   font size by this value, so an unset scale font renders every crosstab/table cell at font size
 *   zero -- blank. {@code set_print_layout} must refuse an explicit {@code 0} outright and must
 *   default an omitted one to {@code 1.0f}, never let Java's bare-field default reach the write.</li>
 *   <li><b>Risk 2</b> -- the device catalogue ({@code DeviceRegistry}/{@code ScreenSizeDialogModel})
 *   is global/org-wide infrastructure with its own admin-gated REST surface ({@code
 *   DeviceController}), entirely outside this plugin's session model. {@code manage_device_layout}
 *   may only create/update/delete a named device LAYOUT on this viewsheet ({@code
 *   VSDeviceLayoutDialogModel}) referencing existing catalogue ids -- it must refuse anything
 *   shaped like a catalogue write instead of silently forwarding it.</li>
 * </ul>
 */
@Tag("core")
class PrintDeviceLayoutPropertyServiceTest {
   // ── set_print_layout ─────────────────────────────────────────────────────

   @Test
   void omittedScaleFontOnANewPrintLayoutDefaultsToOneNotJavasZero() throws Exception {
      // No existing print layout at all -- screensPane.getPrintLayout() reads back null, exactly
      // as ViewsheetPropertyDialogService.getViewsheetInfo leaves it when the viewsheet has never
      // had a print layout configured. A naive "new VSPrintLayoutDialogModel(), copy over the
      // patched fields" implementation leaves scaleFont at Java's bare-field default of 0.0f here,
      // which is the exact blank-text bug (Hazard 3) this test exists to catch.
      Harness h = new Harness(screensPaneWithNoPrintLayout());

      h.service.setPrintLayout("tok", h.principal, Map.of("paperSize", "Letter"), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(h.dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                        anyString(), any());
      VSPrintLayoutDialogModel written = captor.getValue().screensPane().getPrintLayout();
      assertNotNull(written);
      assertEquals(1.0f, written.getScaleFont(), 0.0001f);
      assertEquals("Letter [8.5x11 in]", written.getPaperSize());
   }

   @Test
   void paperSizeBareShortNamesCanonicalizeCaseInsensitively() throws Exception {
      Harness h = new Harness(screensPaneWithNoPrintLayout());

      h.service.setPrintLayout("tok", h.principal, Map.of("paperSize", "a4"), "");

      assertEquals("A4 [210x297 mm]", writtenPrintLayout(h).getPaperSize());
   }

   @Test
   void paperSizeAlreadyCanonicalPassesThroughUnchanged() throws Exception {
      Harness h = new Harness(screensPaneWithNoPrintLayout());

      h.service.setPrintLayout("tok", h.principal, Map.of("paperSize", "A4 [210x297 mm]"), "");

      assertEquals("A4 [210x297 mm]", writtenPrintLayout(h).getPaperSize());
   }

   @Test
   void paperSizeCustomSizeSentinelPassesThroughUnchanged() throws Exception {
      Harness h = new Harness(screensPaneWithNoPrintLayout());

      h.service.setPrintLayout("tok", h.principal, Map.of("paperSize", "(Custom Size)"), "");

      assertEquals("(Custom Size)", writtenPrintLayout(h).getPaperSize());
   }

   @Test
   void paperSizeUnrecognizedValueThrowsNamingTheFieldAndValueBeforeWritingAnything()
      throws Exception
   {
      Harness h = new Harness(screensPaneWithNoPrintLayout());

      Exception thrown = assertThrows(Exception.class,
         () -> h.service.setPrintLayout("tok", h.principal, Map.of("paperSize", "Leter"), ""));

      assertTrue(thrown.getMessage().contains("paperSize"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Leter"), thrown.getMessage());
      // canonicalize() throws while applyPrintLayoutPatch is still building the in-memory
      // printLayout, before screensPane.setPrintLayout/dialogService.setViewsheetInfo -- the
      // failed call never persists anything.
      verify(h.dialog, never())
         .setViewsheetInfo(anyString(), any(), any(), any(), anyString(), any());
   }

   @Test
   void omittedUnitsOnANewPrintLayoutDefaultsToInchesNotNull() throws Exception {
      // The sibling of the scaleFont case above, and fatal where that one is merely wrong. A null
      // units reaches ViewsheetPropertyDialogService's printInfo.setUnit(...) and then
      // VSLayoutService.getPLayoutSize's switch(unit) -- which recognises only "inches" and "mm" --
      // where it throws NPE. That throw lands AFTER the patch has mutated the live model, so the
      // failed call persists a half-written layout and every later write on the viewsheet fails
      // while re-reading it, taking manage_device_layout down with it.
      Harness h = new Harness(screensPaneWithNoPrintLayout());

      h.service.setPrintLayout("tok", h.principal, Map.of("paperSize", "Letter"), "");

      assertEquals("inches", writtenPrintLayout(h).getUnits());
   }

   @Test
   void anExplicitNullUnitsCannotOverwriteTheDefault() throws Exception {
      // applyPrintLayoutPatch's "units" case passes its value through verbatim, so a caller sending
      // units: null would write straight over a seeded default. The guard runs after the patch for
      // exactly this reason.
      Harness h = new Harness(screensPaneWithNoPrintLayout());
      Map<String, Object> patch = new HashMap<>();
      patch.put("paperSize", "Letter");
      patch.put("units", null);

      h.service.setPrintLayout("tok", h.principal, patch, "");

      assertEquals("inches", writtenPrintLayout(h).getUnits());
   }

   @Test
   void anExistingPrintLayoutWithNoUnitsIsRepairedRatherThanSkipped() throws Exception {
      // A layout persisted before the guard existed carries a null units and does NOT go through
      // the new-layout branch, so seeding only there would leave it crashing forever with no way
      // for a caller to repair it.
      VSPrintLayoutDialogModel existing = new VSPrintLayoutDialogModel();
      existing.setScaleFont(1.0f);
      existing.setPaperSize("Letter");
      ScreensPaneModel screensPane = screensPaneWithNoPrintLayout();
      screensPane.setPrintLayout(existing);
      Harness h = new Harness(screensPane);

      h.service.setPrintLayout("tok", h.principal, Map.of("scaleFont", 0.9f), "");

      assertEquals("inches", writtenPrintLayout(h).getUnits());
   }

   @Test
   void anExplicitUnitsValueIsLeftAlone() throws Exception {
      // Pins applyPrintLayoutPatch's passthrough, NOT the guard: this stays green with the guard
      // disabled, and should, because what it asserts is that the guard never overrules a value
      // the caller set. "mm" is the other value the renderer's switch recognises.
      Harness h = new Harness(screensPaneWithNoPrintLayout());

      h.service.setPrintLayout("tok", h.principal, Map.of("paperSize", "Letter", "units", "mm"), "");

      assertEquals("mm", writtenPrintLayout(h).getUnits());
   }

   @Test
   void manageDeviceLayoutRepairsAPersistedNullUnitsRatherThanCrashingOnIt() throws Exception {
      // manage_device_layout never touches the print layout -- but setViewsheetInfo computes the
      // page size from it unconditionally, so a viewsheet carrying a print layout persisted with a
      // null units takes this method down too. Guarding only setPrintLayout leaves such a
      // viewsheet permanently unusable, because no caller-side call can repair it.
      VSPrintLayoutDialogModel persisted = new VSPrintLayoutDialogModel();
      persisted.setScaleFont(1.0f);
      persisted.setPaperSize("Letter");
      ScreensPaneModel screensPane = screensPaneWithNoPrintLayout();
      screensPane.setPrintLayout(persisted);
      Harness h = new Harness(screensPane);
      h.registerDevices("wiz-mobile");

      h.service.manageDeviceLayout("tok", h.principal, "create",
         Map.of("name", "Phone", "selectedDevices", List.of("wiz-mobile")), "");

      assertEquals("inches", writtenPrintLayout(h).getUnits());
   }

   /** The print layout as it was handed to {@code setViewsheetInfo} -- the only thing that ships. */
   private static VSPrintLayoutDialogModel writtenPrintLayout(Harness h) throws Exception {
      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(h.dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                        anyString(), any());
      VSPrintLayoutDialogModel written = captor.getValue().screensPane().getPrintLayout();
      assertNotNull(written);
      return written;
   }

   @Test
   void refusesAnExplicitZeroScaleFontBeforeWritingAnything() throws Exception {
      Harness h = new Harness(screensPaneWithNoPrintLayout());

      Exception thrown = assertThrows(Exception.class,
         () -> h.service.setPrintLayout("tok", h.principal, Map.of("scaleFont", 0f), ""));

      assertTrue(thrown.getMessage().toLowerCase().contains("scalefont") ||
                 thrown.getMessage().toLowerCase().contains("blank"),
                 thrown.getMessage());

      // The refusal happens before any write is attempted -- not a silent clamp after the fact.
      verify(h.dialog, never())
         .setViewsheetInfo(anyString(), any(), any(), any(), anyString(), any());
      // And before the mutation seam even opens: no checkpoint should be attempted for a request
      // that never applies.
      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void aPatchTouchingOnlyScaleFontLeavesEveryOtherPrintLayoutFieldExactlyAsRead()
      throws Exception
   {
      VSPrintLayoutDialogModel existing = new VSPrintLayoutDialogModel();
      existing.setPaperSize("Legal");
      existing.setMarginTop(1.5);
      existing.setMarginLeft(2.5);
      existing.setMarginBottom(1.0);
      existing.setMarginRight(0.5);
      existing.setFooterFromEdge(0.3f);
      existing.setHeaderFromEdge(0.2f);
      existing.setLandscape(true);
      existing.setNumberingStart(5);
      existing.setCustomWidth(10);
      existing.setCustomHeight(20);
      existing.setUnits("inches");
      existing.setScaleFont(1.0f);

      ScreensPaneModel screensPane = new ScreensPaneModel();
      screensPane.setPrintLayout(existing);
      Harness h = new Harness(screensPane);

      h.service.setPrintLayout("tok", h.principal, Map.of("scaleFont", 2.0f), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(h.dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                        anyString(), any());
      VSPrintLayoutDialogModel written = captor.getValue().screensPane().getPrintLayout();

      assertEquals(2.0f, written.getScaleFont(), 0.0001f);
      // Everything else survives untouched -- the read-merge-write contract.
      assertEquals("Legal", written.getPaperSize());
      assertEquals(1.5, written.getMarginTop(), 0.0001);
      assertEquals(2.5, written.getMarginLeft(), 0.0001);
      assertEquals(1.0, written.getMarginBottom(), 0.0001);
      assertEquals(0.5, written.getMarginRight(), 0.0001);
      assertEquals(0.3f, written.getFooterFromEdge(), 0.0001f);
      assertEquals(0.2f, written.getHeaderFromEdge(), 0.0001f);
      assertTrue(written.isLandscape());
      assertEquals(5, written.getNumberingStart());
      assertEquals(10, written.getCustomWidth(), 0.0001);
      assertEquals(20, written.getCustomHeight(), 0.0001);
      assertEquals("inches", written.getUnits());
   }

   @Test
   void refusesAnEmptyPatchRatherThanOpeningACheckpointForNothing() {
      Harness h = new Harness(screensPaneWithNoPrintLayout());

      assertThrows(Exception.class,
         () -> h.service.setPrintLayout("tok", h.principal, Map.of(), ""));
   }

   // ── manage_device_layout ─────────────────────────────────────────────────

   @Test
   void refusesASelectedDeviceIdNotInTheCatalogueListingTheRealIds() throws Exception {
      Harness h = new Harness(screensPaneWithNoPrintLayout());
      h.registerDevices("wiz-mobile", "wiz-wide", "wiz-ultrawide");

      Exception thrown = assertThrows(Exception.class, () -> h.service.manageDeviceLayout(
         "tok", h.principal, "create",
         Map.of("name", "Phone", "selectedDevices", List.of("not-a-real-device")), ""));

      assertTrue(thrown.getMessage().contains("not-a-real-device"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("wiz-mobile"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("wiz-wide"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("wiz-ultrawide"), thrown.getMessage());
      verify(h.dialog, never())
         .setViewsheetInfo(anyString(), any(), any(), any(), anyString(), any());
   }

   @Test
   void refusesABareDeviceCatalogueShapedPayloadWithNoDeviceLayoutName() throws Exception {
      Harness h = new Harness(screensPaneWithNoPrintLayout());
      h.registerDevices("wiz-mobile", "wiz-wide", "wiz-ultrawide");

      // Shaped like a ScreenSizeDialogModel (a device catalogue ENTRY -- label/min/max width),
      // not a VSDeviceLayoutDialogModel (a device LAYOUT on this viewsheet) -- and critically,
      // has no "name", the one field every device-layout request carries.
      Exception thrown = assertThrows(Exception.class, () -> h.service.manageDeviceLayout(
         "tok", h.principal, "create",
         Map.of("label", "My New Phone Size", "minWidth", 100, "maxWidth", 400), ""));

      assertTrue(thrown.getMessage().toLowerCase().contains("admin"), thrown.getMessage());
      verify(h.dialog, never())
         .setViewsheetInfo(anyString(), any(), any(), any(), anyString(), any());
   }

   @Test
   void createsANewDeviceLayoutWithValidatedDeviceIds() throws Exception {
      Harness h = new Harness(screensPaneWithNoPrintLayout());
      h.registerDevices("wiz-mobile", "wiz-wide", "wiz-ultrawide");

      h.service.manageDeviceLayout("tok", h.principal, "create",
         Map.of("name", "Phone", "mobileOnly", true, "selectedDevices", List.of("wiz-mobile")),
         "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(h.dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                        anyString(), any());
      List<VSDeviceLayoutDialogModel> layouts = captor.getValue().screensPane().getDeviceLayouts();
      assertEquals(1, layouts.size());
      assertEquals("Phone", layouts.get(0).getName());
      assertTrue(layouts.get(0).isMobileOnly());
      assertEquals(List.of("wiz-mobile"), layouts.get(0).getSelectedDevices());
   }

   @Test
   void updatesAnExistingDeviceLayoutByName() throws Exception {
      VSDeviceLayoutDialogModel existing = new VSDeviceLayoutDialogModel();
      existing.setName("Phone");
      existing.setMobileOnly(true);
      existing.setSelectedDevices(List.of("wiz-mobile"));
      ScreensPaneModel screensPane = screensPaneWithNoPrintLayout();
      screensPane.getDeviceLayouts().add(existing);
      Harness h = new Harness(screensPane);
      h.registerDevices("wiz-mobile", "wiz-wide", "wiz-ultrawide");

      h.service.manageDeviceLayout("tok", h.principal, "update",
         Map.of("name", "Phone", "selectedDevices", List.of("wiz-wide")), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(h.dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                        anyString(), any());
      List<VSDeviceLayoutDialogModel> layouts = captor.getValue().screensPane().getDeviceLayouts();
      assertEquals(1, layouts.size());
      assertEquals(List.of("wiz-wide"), layouts.get(0).getSelectedDevices());
      // mobileOnly untouched by a patch that didn't mention it.
      assertTrue(layouts.get(0).isMobileOnly());
   }

   @Test
   void deletesAnExistingDeviceLayoutByName() throws Exception {
      VSDeviceLayoutDialogModel existing = new VSDeviceLayoutDialogModel();
      existing.setName("Phone");
      ScreensPaneModel screensPane = screensPaneWithNoPrintLayout();
      screensPane.getDeviceLayouts().add(existing);
      Harness h = new Harness(screensPane);
      h.registerDevices("wiz-mobile");

      h.service.manageDeviceLayout("tok", h.principal, "delete", Map.of("name", "Phone"), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(h.dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                        anyString(), any());
      assertTrue(captor.getValue().screensPane().getDeviceLayouts().isEmpty());
   }

   @Test
   void updateOnAnUnknownDeviceLayoutNameThrowsRatherThanSilentlyNoOpping() throws Exception {
      Harness h = new Harness(screensPaneWithNoPrintLayout());
      h.registerDevices("wiz-mobile");

      Exception thrown = assertThrows(Exception.class, () -> h.service.manageDeviceLayout(
         "tok", h.principal, "update", Map.of("name", "Does Not Exist"), ""));

      assertTrue(thrown.getMessage().contains("Does Not Exist"), thrown.getMessage());
      verify(h.dialog, never())
         .setViewsheetInfo(anyString(), any(), any(), any(), anyString(), any());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private static ScreensPaneModel screensPaneWithNoPrintLayout() {
      return new ScreensPaneModel();
   }

   /**
    * Wires one {@code PrintDeviceLayoutPropertyService} against a mocked {@code
    * ViewsheetSessionService} (whose {@code mutate} runs the mutation synchronously, exactly as
    * {@code SheetPropertyServiceTest}'s own harness does), a mocked {@code
    * ViewsheetPropertyDialogService} that reads back a model built around the given {@code
    * ScreensPaneModel}, and a mocked {@code DeviceRegistry}.
    */
   private static final class Harness {
      final Principal principal = () -> "admin";
      final ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      final ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      final DeviceRegistry deviceRegistry = mock(DeviceRegistry.class);
      final PrintDeviceLayoutPropertyService service =
         new PrintDeviceLayoutPropertyService(sessions, dialog, deviceRegistry);

      Harness(ScreensPaneModel screensPane) {
         try {
            RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
            when(rvs.getID()).thenReturn("rt1");
            when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
            doAnswer(invocation -> {
               ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
               mutation.run(rvs, "rt1", null);
               return null;
            }).when(sessions).mutate(anyString(), any(Principal.class), any());

            ViewsheetPropertyDialogModel model =
               ViewsheetPropertyDialogModel.builder().screensPane(screensPane).build();
            when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);
            when(deviceRegistry.getDevices()).thenReturn(new DeviceInfo[0]);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      void registerDevices(String... ids) {
         DeviceInfo[] devices = new DeviceInfo[ids.length];

         for(int i = 0; i < ids.length; i++) {
            DeviceInfo device = new DeviceInfo();
            device.setId(ids[i]);
            device.setName(ids[i]);
            devices[i] = device;
         }

         when(deviceRegistry.getDevices()).thenReturn(devices);
      }
   }
}
