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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.viewsheet.EmbeddedTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.EmbeddedTableVSAssemblyInfo;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79418: an EmbeddedTableVSAssembly is edited through the Table property dialog, so the
 * assembly property tools resolve it to the "table" vocabulary instead of refusing it as "not
 * covered yet". The write goes through the real {@link VSObjectPropertyService#editObjectProperty}
 * so the assembly must stay an embedded table (the Bug #79076 swap to a plain table must not
 * come back).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class AssemblyPropertyServiceEmbeddedTableTest {
   @Test
   void embeddedTableResolvesToTheTableVocabulary() throws Exception {
      Stack stack = stack();

      Map<String, Object> listed = stack.service().list("tok", principal(), NAME);

      assertTrue(listed.toString().contains("maxRows"), listed.toString());
      assertTrue(listed.toString().contains("submitOnChange"), listed.toString());
   }

   @Test
   @SuppressWarnings("unchecked")
   void getReturnsMaxRowsAndSubmitOnChangeForAnEmbeddedTable() throws Exception {
      Stack stack = stack();
      stack.info().setMaxRows(25);

      Map<String, Object> values =
         (Map<String, Object>) stack.service().get("tok", principal(), NAME, false);

      assertEquals(25, values.get("maxRows"));
      assertTrue(values.containsKey("submitOnChange"));
   }

   @Test
   void setMaxRowsKeepsTheAssemblyAnEmbeddedTable() throws Exception {
      Stack stack = stack();
      assertFalse(stack.info().isEmbeddedTable(), "a freshly created embedded table");

      stack.service().set("tok", principal(), NAME, Map.of("maxRows", 37), "");

      Object after = stack.vs().getAssembly(NAME);
      assertInstanceOf(EmbeddedTableVSAssembly.class, after,
                       "the write must not swap the assembly for a plain table (Bug #79076)");
      assertSame(stack.table(), after);
      assertEquals(37, ((EmbeddedTableVSAssemblyInfo) ((EmbeddedTableVSAssembly) after)
         .getVSAssemblyInfo()).getMaxRows());
   }

   @Test
   @SuppressWarnings("unchecked")
   void submitOnChangeRoundTripsOnAnEmbeddedTable() throws Exception {
      Stack stack = stack();
      for(boolean submit : new boolean[] { false, true }) {
         stack.service().set("tok", principal(), NAME, Map.of("submitOnChange", submit), "");

         assertInstanceOf(EmbeddedTableVSAssembly.class, stack.vs().getAssembly(NAME));
         assertEquals(submit, Boolean.parseBoolean(stack.info().getSubmitOnChangeValue()));
         Map<String, Object> values =
            (Map<String, Object>) stack.service().get("tok", principal(), NAME, false);
         assertEquals(submit, values.get("submitOnChange"));
      }
   }

   private static final String NAME = "EmbeddedTable1";

   private record Stack(AssemblyPropertyService service, Viewsheet vs,
                        EmbeddedTableVSAssembly table)
   {
      EmbeddedTableVSAssemblyInfo info() {
         return (EmbeddedTableVSAssemblyInfo) table.getVSAssemblyInfo();
      }
   }

   private static Principal principal() {
      return AssemblyPropertyServiceListInputTest.principal();
   }

   /**
    * The real table dialog service over the real {@code VSObjectPropertyService.editObjectProperty}
    * and a real viewsheet holding a real embedded table; only the collaborators around them are
    * mocks.
    */
   private static Stack stack() throws Exception {
      Viewsheet vs = new Viewsheet();
      EmbeddedTableVSAssembly table = new EmbeddedTableVSAssembly(vs, NAME);
      vs.addAssembly(table);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      ViewsheetService viewsheets = mock(ViewsheetService.class);
      when(viewsheets.getViewsheet(anyString(), any())).thenReturn(rvs);
      VSDialogService dialogs = mock(VSDialogService.class);
      when(dialogs.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      when(dialogs.getAssemblySize(any(), any())).thenReturn(new Dimension(100, 20));

      CoreLifecycleService lifecycle = mock(CoreLifecycleService.class);
      when(lifecycle.createList(anyBoolean(), any(), any(), any()))
         .thenReturn(mock(ChangedAssemblyList.class));
      VSObjectPropertyService properties = spy(new VSObjectPropertyService(
         lifecycle, mock(VSColumnHandler.class),
         mock(VSObjectTreeService.class), mock(VSAssemblyInfoHandler.class),
         mock(VSWizardTemporaryInfoService.class), mock(VSCompositionService.class),
         mock(SharedFilterService.class), mock(DataSourceRegistry.class),
         mock(QueryManagerService.class)));
      doReturn(new String[0]).when(properties).getObjectNames(any(), any());
      doReturn(new String[0]).when(properties)
         .getSupportedTablePopComponents(any(), any(), anyBoolean());
      doReturn(false).when(properties).isEmbeddedEnabled(any(), any());
      TableViewPropertyDialogService tableService =
         new TableViewPropertyDialogService(properties, dialogs, viewsheets);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      AssemblyPropertyService service = new AssemblyPropertyService(
         sessions, mock(GaugePropertyDialogService.class), mock(ImagePropertyDialogService.class),
         mock(TextPropertyDialogService.class), mock(ChartPropertyDialogService.class),
         tableService, mock(CrosstabPropertyDialogService.class),
         mock(SelectionListPropertyDialogService.class),
         mock(SelectionTreePropertyDialogService.class), mock(VSInputService.class),
         mock(RangeSliderPropertyDialogService.class), mock(CalendarPropertyDialogService.class),
         mock(TabPropertyDialogService.class), mock(CalcTablePropertyDialogService.class),
         mock(GroupContainerPropertyDialogService.class), mock(LinePropertyDialogService.class),
         mock(OvalPropertyDialogService.class), mock(RectanglePropertyDialogService.class),
         mock(SelectionContainerPropertyDialogService.class),
         mock(SubmitPropertyDialogService.class), mock(VSOutputService.class),
         mock(VSColumnHandler.class), mock(DataOutputService.class));
      return new Stack(service, vs, table);
   }
}