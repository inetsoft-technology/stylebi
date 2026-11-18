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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Feature #79534: {@code getValues} reads an output assembly's computed value through the
 * checkpoint-free {@code sessions.read}, never mutates, and refuses an unknown or non-output name
 * with a named error.
 */
@Tag("core")
class OutputValueServiceTest {
   private Viewsheet vs;
   private ViewsheetSessionService sessions;

   private ImageVSAssembly image(String name, Object value, boolean bound) {
      ImageVSAssemblyInfo info = mock(ImageVSAssemblyInfo.class);
      ScalarBindingInfo binding = mock(ScalarBindingInfo.class);
      when(binding.isEmpty()).thenReturn(!bound);
      when(info.getScalarBindingInfo()).thenReturn(binding);
      when(info.getValue()).thenReturn(value);
      ImageVSAssembly assembly = mock(ImageVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getAbsoluteName()).thenReturn(name);
      when(vs.getAssembly(name)).thenReturn(assembly);
      return assembly;
   }

   private OutputValueService service() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      sessions = mock(ViewsheetSessionService.class);
      when(sessions.read(any(), any(), any())).thenAnswer(inv -> {
      ViewsheetSessionService.Read<?> r = inv.getArgument(2);
      return r.run(rvs, "rt", null);
      });
      return new OutputValueService(sessions, (info, r) -> "Sum(REVENUE)");
   }

   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> outputs(Map<String, Object> r) {
      return (List<Map<String, Object>>) r.get("outputs");
   }

   @Test
   void readsValueBindingAndTooltipWithoutMutating() throws Exception {
      vs = mock(Viewsheet.class);
      image("Image1", 7302921.8, true);
      OutputValueService service = service();

      Map<String, Object> r = service.getValues("tok", null, List.of(" Image1 "));

      Map<String, Object> e = outputs(r).get(0);
      assertEquals("Image1", e.get("assembly"));
      assertEquals(true, e.get("bound"));
      assertEquals("Sum(REVENUE)", e.get("binding"));
      assertEquals(7302921.8, e.get("value"));
      assertEquals("Sum(REVENUE): " + e.get("formattedValue"), e.get("tooltip"));
      assertNull(e.get("note"));

      verify(sessions, never()).mutate(any(), any(), any());
   }

   @Test
   void emptyValueIsReportedAsNullWithANote() throws Exception {
      vs = mock(Viewsheet.class);
      image("Image1", "", true);

      Map<String, Object> e = outputs(service().getValues("tok", null, List.of("Image1"))).get(0);

      assertNull(e.get("value"));
      assertNull(e.get("tooltip"));
      assertTrue(String.valueOf(e.get("note")).contains("no value"), e.toString());
   }

   @Test
   void noNamesReadsEveryBoundOutputAndSkipsOthers() throws Exception {
      vs = mock(Viewsheet.class);
      ImageVSAssembly bound = image("Image1", 5, true);
      ImageVSAssembly unbound = image("Image2", "", false);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[] { chart, bound, unbound });

      Map<String, Object> r = service().getValues("tok", null, null);

      assertEquals(List.of("Image1"),
                   outputs(r).stream().map(e -> e.get("assembly")).toList());
   }

   @Test
   void namedUnboundOutputReportsBoundFalse() throws Exception {
      vs = mock(Viewsheet.class);
      image("Image2", "", false);

      Map<String, Object> e = outputs(service().getValues("tok", null, List.of("Image2"))).get(0);

      assertEquals(false, e.get("bound"));
      assertNull(e.get("value"));
      assertTrue(String.valueOf(e.get("note")).contains("no data binding"), e.toString());
   }

   @Test
   void unknownNameIsRefusedByName() throws Exception {
      vs = mock(Viewsheet.class);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
      () -> service().getValues("tok", null, List.of("Nope")));

      assertTrue(ex.getMessage().contains("'Nope'"), ex.getMessage());
   }

   @Test
   void nonOutputNameIsRefusedWithItsType() throws Exception {
      vs = mock(Viewsheet.class);
      when(vs.getAssembly("Chart1")).thenReturn(mock(ChartVSAssembly.class));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
      () -> service().getValues("tok", null, List.of("Chart1")));

      assertTrue(ex.getMessage().contains("'Chart1'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("not a data-bound output"), ex.getMessage());
   }
}
