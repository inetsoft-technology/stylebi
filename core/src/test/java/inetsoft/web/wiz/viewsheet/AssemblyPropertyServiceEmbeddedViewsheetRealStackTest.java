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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ViewsheetVSAssemblyInfo;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Point;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80150 Part B through the real {@code ViewsheetObjectPropertyDialogService} over a real
 * {@code Viewsheet} that embeds a real child {@code Viewsheet}: list/get/set of the embedded
 * viewsheet assembly, the width/height and negative-position refusals, and the rename going
 * through {@code editObjectProperty} with the new name. Axis: operation (list, get, set) x field
 * (name, visible, primary, top, left, width, height) x outcome (applied, refused).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class AssemblyPropertyServiceEmbeddedViewsheetRealStackTest {
   private static final Principal PRINCIPAL = () -> "admin";
   private static final String EMBED = "Embedded1";

   private AssemblyPropertyService service;
   private VSObjectPropertyService properties;
   private Viewsheet embedded;

   @BeforeEach
   void setup() throws Exception {
      Viewsheet vs = new Viewsheet();
      embedded = new Viewsheet().createVSAssembly(EMBED);
      vs.addAssembly(embedded);
      ((ViewsheetVSAssemblyInfo) embedded.getVSAssemblyInfo()).setPixelOffset(new Point(10, 20));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");
      ViewsheetService viewsheets = mock(ViewsheetService.class);
      when(viewsheets.getViewsheet(anyString(), any())).thenReturn(rvs);
      properties = mock(VSObjectPropertyService.class);
      when(properties.editObjectProperty(any(), any(), any(), any(), any(), any(), any(),
                                         anyBoolean(), any())).thenAnswer(invocation -> {
         embedded.setVSAssemblyInfo(invocation.getArgument(1));
         return true;
      });
      ViewsheetObjectPropertyDialogService embeddedService =
         new ViewsheetObjectPropertyDialogService(viewsheets, properties,
                                                  new VSDialogService(null, null));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      when(sessions.mutate(anyString(), any(Principal.class), any())).thenAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(PRINCIPAL, dispatcher -> {
            mutation.run(rvs, "rt1", dispatcher);
            return dispatcher.getWarnings();
         });
      });

      service = new AssemblyPropertyService(
         sessions, mock(GaugePropertyDialogService.class),
         mock(ImagePropertyDialogService.class), mock(TextPropertyDialogService.class),
         mock(ChartPropertyDialogService.class), mock(TableViewPropertyDialogService.class),
         mock(CrosstabPropertyDialogService.class),
         mock(SelectionListPropertyDialogService.class),
         mock(SelectionTreePropertyDialogService.class), mock(VSInputService.class),
         mock(RangeSliderPropertyDialogService.class),
         mock(CalendarPropertyDialogService.class), mock(TabPropertyDialogService.class),
         mock(CalcTablePropertyDialogService.class),
         mock(GroupContainerPropertyDialogService.class),
         mock(LinePropertyDialogService.class), mock(OvalPropertyDialogService.class),
         mock(RectanglePropertyDialogService.class),
         mock(SelectionContainerPropertyDialogService.class),
         mock(SubmitPropertyDialogService.class), mock(VSOutputService.class),
         mock(VSColumnHandler.class), mock(DataOutputService.class), embeddedService);
   }

   private ViewsheetVSAssemblyInfo info() {
      return (ViewsheetVSAssemblyInfo) embedded.getVSAssemblyInfo();
   }

   @SuppressWarnings("unchecked")
   private Map<String, Object> read() throws Exception {
      return (Map<String, Object>) service.get("tok", PRINCIPAL, EMBED, false);
   }

   private List<String> set(Map<String, Object> patch) throws Exception {
      return service.set("tok", PRINCIPAL, EMBED, patch, "");
   }

   @Test
   void listsExactlyTheSevenAliases() throws Exception {
      Object listed = service.list("tok", PRINCIPAL, EMBED);
      String text = String.valueOf(listed);

      for(String alias : List.of("name", "visible", "primary", "top", "left", "width", "height")) {
         assertTrue(text.contains(alias), alias + " missing from " + text);
      }

      for(String absent : List.of("enabled", "locked", "script", "titleHeight", "cellHeight",
                                  "container", "refresh"))
      {
         assertFalse(text.contains(absent), absent + " listed in " + text);
      }
   }

   @Test
   void getReturnsTheDialogValues() throws Exception {
      Map<String, Object> read = read();

      assertEquals(EMBED, read.get("name"));
      assertEquals(10, ((Number) read.get("left")).intValue());
      assertEquals(20, ((Number) read.get("top")).intValue());
      assertEquals(Set.of("name", "visible", "primary", "top", "left", "width", "height"),
                   read.keySet());
   }

   @Test
   void setVisiblePrimaryLeftAndTopLandOnTheRealInfo() throws Exception {
      set(Map.of("visible", "false", "primary", true, "left", 80, "top", 60));

      assertEquals("false", info().getVisibleValue());
      assertTrue(info().isPrimary());
      assertEquals(new Point(80, 60), info().getPixelOffset());
      assertEquals(EMBED, info().getAbsoluteName());
   }

   @Test
   void setNameGoesThroughEditObjectPropertyWithTheNewName() throws Exception {
      set(Map.of("name", "Renamed"));

      verify(properties).editObjectProperty(any(), any(), eq(EMBED), eq("Renamed"), any(), any(),
                                            any(), eq(true), any());
   }

   @Test
   void widthAndHeightAreRefusedAndTheStoredSizeIsUnchanged() throws Exception {
      Object before = info().getPixelSize();

      for(String field : List.of("width", "height", "sizePositionPaneModel.width",
                                 "sizePositionPaneModel.height"))
      {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> set(Map.of(field, 500)), field);
         assertTrue(e.getMessage().contains("embedded viewsheet") &&
                    e.getMessage().contains("laid out"), e.getMessage());
      }

      assertEquals(before, info().getPixelSize());
   }

   @Test
   void everyOtherNonDialogPathIsRefusedAndNothingIsWritten() throws Exception {
      for(String path : List.of(
         "sizePositionPaneModel.cellHeight", "sizePositionPaneModel.scaleVertical",
         "sizePositionPaneModel.container", "sizePositionPaneModel.locked",
         "sizePositionPaneModel.titleHeight", "generalPropPaneModel.enabled"))
      {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> set(Map.of("visible", "false", path, 1)), path);
         assertTrue(e.getMessage().contains(path), e.getMessage());
      }

      assertNotEquals("false", info().getVisibleValue());
      verify(properties, never()).editObjectProperty(any(), any(), any(), any(), any(), any(),
                                                     any(), anyBoolean(), any());
   }

   @Test
   void negativeTopAndLeftAreRefusedZeroAndNumericStringsAreAccepted() throws Exception {
      List<Map<String, Object>> negatives = List.of(
         Map.of("top", -5), Map.of("left", -1), Map.of("top", "-3"));

      for(Map<String, Object> patch : negatives) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> set(patch), patch.toString());
         assertTrue(e.getMessage().contains("0 or greater"), e.getMessage());
      }

      assertEquals(new Point(10, 20), info().getPixelOffset());

      set(Map.of("top", 0, "left", "12"));
      assertEquals(new Point(12, 0), info().getPixelOffset());
   }

   @Test
   void negativePositionGuardIsForTheViewsheetTypeOnly() {
      assertDoesNotThrow(() -> AssemblyPropertyService.requireNonNegativePosition(
         "gauge", "top", "gaugeGeneralPaneModel.sizePositionPaneModel.top", -5));
      assertThrows(IllegalArgumentException.class,
                   () -> AssemblyPropertyService.requireNonNegativePosition(
                      "viewsheet", "top", "sizePositionPaneModel.top", -5));
      assertDoesNotThrow(() -> AssemblyPropertyService.requireNonNegativePosition(
         "viewsheet", "name", "generalPropPaneModel.basicGeneralPaneModel.name", "-5"));
   }
}
