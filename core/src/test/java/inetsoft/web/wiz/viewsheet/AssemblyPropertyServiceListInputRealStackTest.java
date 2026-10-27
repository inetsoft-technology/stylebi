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
import inetsoft.uql.viewsheet.ComboBoxVSAssembly;
import inetsoft.uql.viewsheet.ListData;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.util.List;
import java.util.Map;

import static inetsoft.web.wiz.viewsheet.AssemblyPropertyServiceListInputTest.E;
import static inetsoft.web.wiz.viewsheet.AssemblyPropertyServiceListInputTest.principal;
import static inetsoft.web.wiz.viewsheet.AssemblyPropertyServiceListInputTest.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78470 through the real {@code VSInputService} getter/setter over a real
 * {@link ComboBoxVSAssembly}: what the assembly stores and reads back across writes. The model
 * level rules are in {@link AssemblyPropertyServiceListInputTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class AssemblyPropertyServiceListInputRealStackTest {
   /**
    * VFI-011: before the fix the one-call patch left the static list typed "string", so the
    * assembly read back "string" and the next unrelated write dropped both bounds.
    */
   @Test
   void oneCallCalendarPatchKeepsItsBoundsAcrossTheNextWrite() throws Exception {
      RealStack stack = realStack();

      stack.service().set("tok", principal(), "ComboBox1", Map.of(
         E + ".calendar", true, E + ".dataType", "date",
         E + ".minDate", "2024-01-01", E + ".maxDate", "2025-10-31"), "");

      ComboBoxVSAssemblyInfo info = stack.info();
      assertEquals("date", info.getDataType());
      assertTrue(info.isCalendar());

      stack.service().set("tok", principal(), "ComboBox1", Map.of("visible", "true"), "");

      info = stack.info();
      assertEquals("date", info.getDataType());
      assertEquals("2024-01-01", info.getMinDateValue());
      assertEquals("2025-10-31", info.getMaxDateValue());
   }

   /** VFI-010: a malformed bound is refused and nothing reaches the assembly. */
   @Test
   void refusesAMalformedBoundWithoutStoringIt() {
      RealStack stack = realStack();
      ComboBoxVSAssemblyInfo before = stack.info();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> stack.service().set("tok", principal(), "ComboBox1", Map.of(
            E + ".calendar", true, E + ".dataType", "date",
            E + ".minDate", "05/12/2024", E + ".maxDate", "2025-10-31"), ""));

      assertTrue(e.getMessage().contains("yyyy-MM-dd"), e.getMessage());
      assertSame(before, stack.info());
      assertNull(stack.info().getMinDateValue());
   }

   /** A timeInstant pair in the dialog's H:m:s shape is stored zero-padded and survives. */
   @Test
   void storesTimeInstantBoundsPaddedAndKeepsThemAcrossTheNextWrite() throws Exception {
      RealStack stack = realStack();

      stack.service().set("tok", principal(), "ComboBox1", Map.of(
         E + ".calendar", true, E + ".dataType", "timeInstant",
         E + ".minDate", "2024-1-5 9:5:0", E + ".maxDate", "2024-12-31 23:59:59"), "");
      stack.service().set("tok", principal(), "ComboBox1", Map.of("visible", "true"), "");

      ComboBoxVSAssemblyInfo info = stack.info();
      assertEquals("timeInstant", info.getDataType());
      assertEquals("2024-01-05 09:05:00", info.getMinDateValue());
      assertEquals("2024-12-31 23:59:59", info.getMaxDateValue());
   }

   /**
    * Lead decision C3: a malformed bound stored by another writer (the native dialog only
    * warns) does not block an unrelated write, but a write of the calendar flag checks it.
    */
   @Test
   void leavesALegacyMalformedBoundAloneUntilTheCalendarIsWritten() throws Exception {
      RealStack stack = realStack();
      stack.service().set("tok", principal(), "ComboBox1", Map.of(
         E + ".calendar", true, E + ".dataType", "date",
         E + ".minDate", "2024-01-01", E + ".maxDate", "2025-10-31"), "");
      stack.info().setMinDateValue("05/12/2024");

      stack.service().set("tok", principal(), "ComboBox1", Map.of("visible", "true"), "");
      assertEquals("05/12/2024", stack.info().getMinDateValue());

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> stack.service().set("tok", principal(), "ComboBox1",
                                   Map.of(E + ".calendar", true), ""));
      assertTrue(e.getMessage().contains(E + ".minDate"), e.getMessage());
   }

   /**
    * Refute C2: a combo left drifted by the old write path (dtype date, static list typed
    * string, so it reads "string") is repaired by re-sending the dataType with its bounds.
    */
   @Test
   void repairsADriftedComboWhenTheBoundsAreReSentWithTheDataType() throws Exception {
      RealStack stack = realStack();
      ComboBoxVSAssemblyInfo drifted = stack.info();
      drifted.setDataType("date");
      ListData list = new ListData();
      list.setDataType("string");
      drifted.setListData(list);
      drifted.setCalendar(true);
      drifted.setMinDateValue("2024-01-01");
      drifted.setMaxDateValue("2025-10-31");
      assertEquals("string", drifted.getDataType());

      stack.service().set("tok", principal(), "ComboBox1", Map.of(
         E + ".dataType", "date", E + ".minDate", "2024-01-01",
         E + ".maxDate", "2025-10-31"), "");
      stack.service().set("tok", principal(), "ComboBox1", Map.of("visible", "true"), "");

      ComboBoxVSAssemblyInfo info = stack.info();
      assertEquals("date", info.getDataType());
      assertTrue(info.isCalendar());
      assertEquals("2024-01-01", info.getMinDateValue());
      assertEquals("2025-10-31", info.getMaxDateValue());
   }

   /**
    * Bug #79094 through the real {@code VSInputService} setter: it silently replaces an unusable
    * pattern with yyyy-MM-dd, so the guard must refuse before it, and a usable pattern the
    * setter keeps verbatim must read back as written.
    */
   @Test
   void refusesAnUnusableDateFormatBeforeTheSetterReplacesItAndKeepsAUsableOne() throws Exception {
      RealStack stack = realStack();
      String pattern = "dataInputPaneModel.dateFormatPattern";
      String query = "dataInputPaneModel.queryDateFormat";
      stack.info().setDateFormatPattern("dd.MM.yyyy");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> stack.service().set("tok", principal(), "ComboBox1",
                                   Map.of(pattern, "abc", query, true), ""));
      assertTrue(e.getMessage().contains(pattern), e.getMessage());
      assertEquals("dd.MM.yyyy", stack.info().getDateFormatPattern());

      // formats fine at runtime, but the Composer dialog would refuse it
      e = assertThrows(IllegalArgumentException.class,
         () -> stack.service().set("tok", principal(), "ComboBox1",
                                   Map.of(pattern, "yyyy-MM", query, true), ""));
      assertTrue(e.getMessage().contains(pattern), e.getMessage());
      assertEquals("dd.MM.yyyy", stack.info().getDateFormatPattern());

      stack.service().set("tok", principal(), "ComboBox1",
                          Map.of(pattern, "MM/dd/yyyy", query, true), "");
      assertEquals("MM/dd/yyyy", stack.info().getDateFormatPattern());
      assertTrue(stack.info().isQueryDateFormat());

      stack.info().setDateFormatPattern(null);
      e = assertThrows(IllegalArgumentException.class,
         () -> stack.service().set("tok", principal(), "ComboBox1", Map.of(query, true), ""));
      assertTrue(e.getMessage().contains(pattern), e.getMessage());
   }

   // ── harness ─────────────────────────────────────────────────────────────

   private record RealStack(AssemblyPropertyService service, ComboBoxVSAssembly combo) {
      ComboBoxVSAssemblyInfo info() {
         return (ComboBoxVSAssemblyInfo) combo.getVSAssemblyInfo();
      }
   }

   /**
    * A real {@code VSInputService} (only its table-list lookups stubbed) over a real combo, with
    * {@code editObjectProperty} reduced to installing the info the setter built.
    */
   private static RealStack realStack() {
      Viewsheet vs = new Viewsheet();
      ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "ComboBox1");
      vs.addAssembly(combo);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");

      try {
         ViewsheetService viewsheets = mock(ViewsheetService.class);
         when(viewsheets.getViewsheet(anyString(), any())).thenReturn(rvs);
         VSDialogService dialogs = mock(VSDialogService.class);
         when(dialogs.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
         when(dialogs.getAssemblySize(any(), any())).thenReturn(new Dimension(100, 20));
         VSObjectPropertyService properties = mock(VSObjectPropertyService.class);
         doAnswer(invocation -> {
            combo.setVSAssemblyInfo(invocation.getArgument(1, VSAssemblyInfo.class));
            return null;
         }).when(properties).editObjectProperty(any(), any(), any(), any(), any(), any(), any());

         VSInputService input = spy(new VSInputService(
            mock(VSObjectService.class), mock(CoreLifecycleService.class), viewsheets, properties,
            dialogs, mock(VSTrapService.class), mock(DataRefModelFactoryService.class),
            mock(VSAssemblyInfoHandler.class), mock(VSColumnHandler.class),
            mock(QueryManagerService.class)));
         doReturn(List.of(new String[0], new String[0], new String[0]))
            .when(input).getInputTablesArray(any(), any());
         doReturn(null).when(input).getInputTablesTree(any(), anyBoolean(), any());

         return new RealStack(service(rvs, input), combo);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }
}
