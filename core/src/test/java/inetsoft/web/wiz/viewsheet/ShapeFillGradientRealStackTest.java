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
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.model.vs.FillPropPaneModel;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79108 through the real {@code RectanglePropertyDialogService} and
 * {@code OvalPropertyDialogService} over real assemblies: a solid fill written while the gradient
 * is applied (stored, or in the same patch). Axis: shape type (rectangle, oval) x gradient state
 * (stored, same patch) x fill kind (dynamic script, static color), each run for both shapes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class ShapeFillGradientRealStackTest {
   private static final String RED = "=\"#FF0000\"";
   private static final List<Map<String, Object>> STOPS = List.of(
      Map.of("color", "#DCEBFA", "offset", 0), Map.of("color", "#9CC3EB", "offset", 100));

   private static Map<String, Object> gradient(boolean apply) {
      return Map.of("apply", apply, "direction", "linear", "angle", 90, "colors", STOPS);
   }

   /** The fill patch for a kind: dynamic script, or static color. */
   private static Map<String, Object> fill(boolean dynamic) {
      return dynamic ? Map.of("fillColor", RED)
         : Map.of("fillColor", "Static", "fillColorValue", "#00FF00");
   }

   private static void assertFillStored(Stack s, boolean dynamic) throws Exception {
      Map<String, Object> read = s.read();

      if(dynamic) {
         assertEquals(RED, read.get("fillColor"));
      }
      else {
         assertEquals("Static", read.get("fillColor"));
         assertEquals("#00ff00", String.valueOf(read.get("fillColorValue")).toLowerCase());
      }
   }

   // -- stored gradient, fill-only patch: fill wins, gradient off, warning -------------------

   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void dynamicFillOverStoredGradientStoresFillTurnsGradientOffAndWarns(String type)
      throws Exception
   {
      Stack s = stack(type);
      s.set(Map.of(s.gradientPath(), gradient(true)));
      assertTrue(s.gradientApply());

      List<String> warnings = s.set(fill(true));

      assertFillStored(s, true);
      assertFalse(s.gradientApply());
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).contains("fillColor") && warnings.get(0).contains("gradient"),
                 warnings.get(0));
      assertEquals(2, s.gradientStops(), "stops are kept so the gradient can be re-enabled");
   }

   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void staticFillOverStoredGradientStoresFillTurnsGradientOffAndWarns(String type)
      throws Exception
   {
      Stack s = stack(type);
      s.set(Map.of(s.gradientPath(), gradient(true)));

      List<String> warnings = s.set(fill(false));

      assertFillStored(s, false);
      assertFalse(s.gradientApply());
      assertEquals(1, warnings.size(), warnings.toString());
   }

   // -- same patch: fill + gradient left applied is refused, state unchanged -----------------

   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void dynamicFillWithGradientAppliedInOnePatchIsRefusedAndNothingChanges(String type)
      throws Exception
   {
      assertRefusedSamePatch(type, true, s -> Map.of(s.gradientPath(), gradient(true)), false);
   }

   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void staticFillWithGradientAppliedInOnePatchIsRefusedAndNothingChanges(String type)
      throws Exception
   {
      assertRefusedSamePatch(type, false, s -> Map.of(s.gradientPath(), gradient(true)), false);
   }

   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void fillWithGradientApplyPathTrueInOnePatchIsRefused(String type) throws Exception {
      assertRefusedSamePatch(type, true, s -> Map.of(s.gradientPath() + ".apply", true), true);
   }

   /** A partial gradient edit that leaves apply true is a gradient write too. */
   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void fillWithGradientAngleOverStoredAppliedGradientIsRefused(String type) throws Exception {
      Stack s = stack(type);
      s.set(Map.of(s.gradientPath(), gradient(true)));
      String before = s.snapshot();
      Map<String, Object> patch = new HashMap<>(fill(false));
      patch.put(s.gradientPath() + ".angle", 45);

      Exception e = assertThrows(IllegalArgumentException.class, () -> s.set(patch));

      assertNamesBoth(e);
      assertEquals(before, s.snapshot());
   }

   /**
    * A whole fillPropPaneModel write carries the fill with no leaf key to match on. PropertyPath
    * cannot build the pane from JSON, so it arrives as a bean, as an in-process caller would.
    */
   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void wholeFillPaneWriteWithAppliedGradientIsRefused(String type) throws Exception {
      Stack s = stack(type);
      String before = s.snapshot();
      FillPropPaneModel pane = new FillPropPaneModel();
      pane.setColor(RED);
      pane.setAlpha(100);
      GradientColor on = new GradientColor();
      on.setApply(true);
      pane.setGradientColor(on);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> s.set(Map.of(s.fillPanePath(), pane)));

      assertNamesBoth(e);
      assertEquals(before, s.snapshot());
   }

   // -- controls: unchanged behavior ----------------------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void fillWithNoGradientIsStoredWithoutWarning(String type) throws Exception {
      Stack s = stack(type);

      assertTrue(s.set(fill(true)).isEmpty());

      assertFillStored(s, true);
      assertFalse(s.gradientApply());
   }

   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void fillWithGradientApplyFalseInTheSamePatchIsStoredWithoutWarning(String type)
      throws Exception
   {
      Stack s = stack(type);
      s.set(Map.of(s.gradientPath(), gradient(true)));
      Map<String, Object> patch = new HashMap<>(fill(true));
      patch.put(s.gradientPath() + ".apply", false);

      assertTrue(s.set(patch).isEmpty());

      assertFillStored(s, true);
      assertFalse(s.gradientApply());
   }

   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void fillWithAWholeGradientObjectApplyFalseIsStored(String type) throws Exception {
      Stack s = stack(type);
      s.set(Map.of(s.gradientPath(), gradient(true)));
      Map<String, Object> patch = new HashMap<>(fill(false));
      patch.put(s.gradientPath(), gradient(false));

      assertTrue(s.set(patch).isEmpty());

      assertFillStored(s, false);
      assertFalse(s.gradientApply());
   }

   /** A whole gradient object with no apply builds apply=false, so it is not a conflict. */
   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void fillWithAWholeGradientObjectWithoutApplyMeansApplyFalse(String type) throws Exception {
      Stack s = stack(type);
      s.set(Map.of(s.gradientPath(), gradient(true)));
      Map<String, Object> patch = new HashMap<>(fill(true));
      patch.put(s.gradientPath(), Map.of("direction", "linear", "angle", 90, "colors", STOPS));

      assertTrue(s.set(patch).isEmpty());

      assertFillStored(s, true);
      assertFalse(s.gradientApply());
   }

   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void gradientOnlyPatchLeavesTheFillAlone(String type) throws Exception {
      Stack s = stack(type);
      s.set(fill(true));

      assertTrue(s.set(Map.of(s.gradientPath(), gradient(true))).isEmpty());

      assertTrue(s.gradientApply());
      // Oval keeps the fill beside the gradient; Rectangle's own dialog clears it by design.
      if("oval".equals(type)) {
         assertEquals(RED, s.read().get("fillColor"));
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "rectangle", "oval" })
   void unrelatedPatchOverStoredGradientDoesNotTouchIt(String type) throws Exception {
      Stack s = stack(type);
      s.set(Map.of(s.gradientPath(), gradient(true)));

      assertTrue(s.set(Map.of("fillAlpha", 50, "lineColor", RED)).isEmpty());

      assertTrue(s.gradientApply());
   }

   // -- harness --------------------------------------------------------------------------------

   private interface PatchFor {
      Map<String, Object> of(Stack s);
   }

   /** @param stored true to store an unapplied gradient first, so a bare {@code .apply} path exists */
   private static void assertRefusedSamePatch(String type, boolean dynamic, PatchFor gradient,
                                              boolean stored)
      throws Exception
   {
      Stack s = stack(type);

      if(stored) {
         s.set(Map.of(s.gradientPath(), gradient(false)));
      }

      String before = s.snapshot();
      Map<String, Object> patch = new HashMap<>(fill(dynamic));
      patch.putAll(gradient.of(s));

      Exception e = assertThrows(IllegalArgumentException.class, () -> s.set(patch));

      assertNamesBoth(e);
      assertEquals(before, s.snapshot());
      assertFalse(s.gradientApply());
   }

   private static void assertNamesBoth(Exception e) {
      assertTrue(e.getMessage().contains("fillColor"), e.getMessage());
      assertTrue(e.getMessage().contains("gradientColor"), e.getMessage());
   }

   private static final class Stack {
      Stack(String type, AssemblyPropertyService service, String name) {
         this.type = type;
         this.service = service;
         this.name = name;
      }

      String fillPanePath() {
         return type + "PropertyPaneModel.fillPropPaneModel";
      }

      String gradientPath() {
         return fillPanePath() + ".gradientColor";
      }

      List<String> set(Map<String, Object> patch) throws Exception {
         return service.set("tok", PRINCIPAL, name, patch, "");
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> read() throws Exception {
         return (Map<String, Object>) service.get("tok", PRINCIPAL, name, false);
      }

      Object raw() throws Exception {
         return service.get("tok", PRINCIPAL, name, true);
      }

      boolean gradientApply() throws Exception {
         return PropertyPath.get(raw(), gradientPath() + ".apply") == Boolean.TRUE;
      }

      int gradientStops() throws Exception {
         Object colors = PropertyPath.get(raw(), gradientPath() + ".colors");
         return colors instanceof Object[] a ? a.length
            : colors instanceof List<?> l ? l.size() : -1;
      }

      /** Fill and gradient state, the only fields the bug is about. */
      String snapshot() throws Exception {
         Map<String, Object> read = read();
         Object g = PropertyPath.get(raw(), gradientPath());
         return read.get("fillColor") + "|" + read.get("fillColorValue") + "|" +
            gradientApply() + "|" + (g == null ? "none" : gradientStops());
      }

      private final String type;
      private final AssemblyPropertyService service;
      private final String name;
   }

   private static final Principal PRINCIPAL = () -> "admin";

   /** The real dialog service for the type, over a real assembly, through the real set/get. */
   private static Stack stack(String type) {
      try {
         Viewsheet vs = new Viewsheet();
         String name = "Shape1";
         VSAssembly assembly = "oval".equals(type) ? new OvalVSAssembly(vs, name)
            : new RectangleVSAssembly(vs, name);
         vs.addAssembly(assembly);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getID()).thenReturn("rt1");
         ViewsheetService viewsheets = mock(ViewsheetService.class);
         when(viewsheets.getViewsheet(anyString(), any())).thenReturn(rvs);
         VSDialogService dialogs = mock(VSDialogService.class);
         when(dialogs.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
         when(dialogs.getAssemblySize(any(), any())).thenReturn(new Dimension(100, 20));
         VSObjectPropertyService properties = mock(VSObjectPropertyService.class);
         when(properties.editObjectProperty(any(), any(), any(), any(), any(), any(), any(),
                                            anyBoolean(), any())).thenAnswer(invocation -> {
            assembly.setVSAssemblyInfo(invocation.getArgument(1, VSAssemblyInfo.class));
            return true;
         });

         RectanglePropertyDialogService rectangle =
            new RectanglePropertyDialogService(properties, dialogs, viewsheets);
         OvalPropertyDialogService oval =
            new OvalPropertyDialogService(properties, dialogs, viewsheets);

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         when(sessions.mutate(anyString(), any(Principal.class), any())).thenAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            return CapturingCommandDispatcher.withCapturingDispatcher(PRINCIPAL, dispatcher -> {
               mutation.run(rvs, "rt1", dispatcher);
               return dispatcher.getWarnings();
            });
         });

         AssemblyPropertyService service = new AssemblyPropertyService(
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
            mock(LinePropertyDialogService.class), oval, rectangle,
            mock(SelectionContainerPropertyDialogService.class),
            mock(SubmitPropertyDialogService.class), mock(VSOutputService.class),
            mock(VSColumnHandler.class), mock(DataOutputService.class));

         return new Stack(type, service, name);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }
}
