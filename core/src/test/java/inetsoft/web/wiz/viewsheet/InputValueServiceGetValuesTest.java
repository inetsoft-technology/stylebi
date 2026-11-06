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
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.web.viewsheet.controller.OnClickService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.VSInputService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug 80465: there was no read-only way to get an input's current value. {@code getValues} reads
 * through the checkpoint-free {@code sessions.read}, never mutates, masks a Password editor the way
 * {@code setValue} does, and refuses an unknown or non-input name with a named error.
 */
@Tag("core")
class InputValueServiceGetValuesTest {
   private static final String SECRET = "s3cr3t-Hunter2";

   private Viewsheet vs;
   private ViewsheetSessionService sessions;

   private TextInputVSAssembly text(String name, ColumnOption option, Object... stored) {
      TextInputVSAssemblyInfo info = mock(TextInputVSAssemblyInfo.class);
      when(info.getColumnOption()).thenReturn(option);
      when(info.isPassword()).thenCallRealMethod();
      when(info.getSelectedObjects()).thenReturn(stored);
      TextInputVSAssembly assembly = mock(TextInputVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getAbsoluteName()).thenReturn(name);
      when(vs.getAssembly(name)).thenReturn(assembly);
      return assembly;
   }

   private CheckBoxVSAssembly checkBox(String name, Object... stored) {
      CheckBoxVSAssemblyInfo info = mock(CheckBoxVSAssemblyInfo.class);
      when(info.getSelectedObjects()).thenReturn(stored);
      CheckBoxVSAssembly assembly = mock(CheckBoxVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getAbsoluteName()).thenReturn(name);
      when(vs.getAssembly(name)).thenReturn(assembly);
      return assembly;
   }

   private InputValueService service() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      sessions = mock(ViewsheetSessionService.class);
      when(sessions.read(any(), any(), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Read<?> r = inv.getArgument(2);
         return r.run(rvs, "rt", null);
      });
      return new InputValueService(sessions, mock(VSInputService.class),
                                   mock(CoreLifecycleService.class), null);
   }

   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> inputs(Map<String, Object> r) {
      return (List<Map<String, Object>>) r.get("inputs");
   }

   @Test
   void readsNamedInputsWithoutMutating() throws Exception {
      vs = mock(Viewsheet.class);
      text("Name", new TextColumnOption(), "hello");
      checkBox("Pick", "a", null, "b");
      InputValueService service = service();

      Map<String, Object> r = service.getValues("tok", null, List.of("Name", "Pick"));

      List<Map<String, Object>> in = inputs(r);
      assertEquals(2, in.size());
      assertEquals("Name", in.get(0).get("assembly"));
      assertEquals("a text input", in.get(0).get("type"));
      assertEquals(List.of("hello"), in.get(0).get("values"));
      assertNull(in.get(0).get("masked"));
      assertEquals("a check box", in.get(1).get("type"));
      assertEquals(List.of("a", "b"), in.get(1).get("values"));
      verify(sessions, never()).mutate(any(), any(), any());
   }

   @Test
   void passwordEditorIsMasked() throws Exception {
      vs = mock(Viewsheet.class);
      text("Pw", new PasswordColumnOption(), SECRET);

      Map<String, Object> r = service().getValues("tok", null, List.of("Pw"));

      Map<String, Object> entry = inputs(r).get(0);
      assertEquals(List.of(InputValueService.SECRET_MASK), entry.get("values"));
      assertEquals(true, entry.get("masked"));
      assertFalse(r.toString().contains(SECRET), r.toString());
   }

   @Test
   void clearedInputReportsEmptyList() throws Exception {
      vs = mock(Viewsheet.class);
      checkBox("Pick");

      assertEquals(List.of(), inputs(service().getValues("tok", null, List.of("Pick")))
         .get(0).get("values"));
   }

   @Test
   void noNamesReadsEveryInputAndSkipsOtherAssemblies() throws Exception {
      vs = mock(Viewsheet.class);
      TextInputVSAssembly a = text("Name", new TextColumnOption(), "x");
      CheckBoxVSAssembly b = checkBox("Pick", "a");
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[] { chart, a, b });

      Map<String, Object> r = service().getValues("tok", null, null);

      assertEquals(List.of("Name", "Pick"),
                   inputs(r).stream().map(e -> e.get("assembly")).toList());
   }

   @Test
   void unknownNameIsRefusedByName() throws Exception {
      vs = mock(Viewsheet.class);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().getValues("tok", null, List.of("Nope")));

      assertTrue(ex.getMessage().contains("'Nope'"), ex.getMessage());
      assertFalse(ex.getMessage().contains("endpoint"), ex.getMessage());
   }

   @Test
   void nonInputNameIsRefusedByName() throws Exception {
      vs = mock(Viewsheet.class);
      when(vs.getAssembly("Chart1")).thenReturn(mock(ChartVSAssembly.class));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().getValues("tok", null, List.of("Chart1")));

      assertTrue(ex.getMessage().contains("'Chart1'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("not an input"), ex.getMessage());
   }
}
