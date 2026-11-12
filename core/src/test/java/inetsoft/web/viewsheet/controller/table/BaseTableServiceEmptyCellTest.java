/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ConditionList;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.internal.TableConditionUtil;
import inetsoft.util.Catalog;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The empty-cell check of {@link BaseTableService#createCalcTableConditions}: when none of the
 * selected cells of a freehand table is bound, show details and flyovers have nothing to filter
 * by, so the user gets an info message and an empty condition list instead of a condition list
 * built from nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BaseTableServiceEmptyCellTest {
   private final CalcTableVSAssembly assembly = mock(CalcTableVSAssembly.class);
   private final ViewsheetSandbox box = mock(ViewsheetSandbox.class);
   private final TableLayout layout = mock(TableLayout.class);
   private final CommandDispatcher dispatcher = mock(CommandDispatcher.class);

   private static Map<Integer, int[]> selection(int[]... rowAndCols) {
      Map<Integer, int[]> selected = new HashMap<>();

      for(int[] rowAndCol : rowAndCols) {
         selected.put(rowAndCol[0], new int[] { rowAndCol[1] });
      }

      return selected;
   }

   private static RuntimeCalcTableLens runtimeLens() {
      return new RuntimeCalcTableLens(new CalcTableLens(new Object[][] {
         { "a", "b" }, { 1, 2 }, { 3, 4 } }));
   }

   private ConditionList create(Map<Integer, int[]> selected) throws Exception {
      when(assembly.getTableLayout()).thenReturn(layout);
      return BaseTableService.createCalcTableConditions(
         assembly, box, "Calc1", selected, null, dispatcher);
   }

   private void bind(int row, int col) {
      when(layout.getCellBinding(row, col)).thenReturn(
         mock(TableCellBinding.class));
   }

   private void assertEmptyCellMessage() throws Exception {
      ArgumentCaptor<MessageCommand> message = ArgumentCaptor.forClass(MessageCommand.class);
      verify(dispatcher, times(1)).sendCommand(message.capture());
      assertEquals(MessageCommand.Type.INFO, message.getValue().getType());
      assertEquals(Catalog.getCatalog().getString("composer.vs.calctable.empty.cell.showdetail"),
                   message.getValue().getMessage());
   }

   @Test
   void noDataOfTheTableGivesNoConditionsAndTheEmptyCellMessage() throws Exception {
      when(box.getData("Calc1")).thenReturn(null);

      assertTrue(create(selection(new int[] { 1, 0 })).isEmpty());
      assertEmptyCellMessage();
   }

   @Test
   void aTableThatIsNotAFreehandRuntimeTableGivesNoConditionsAndTheEmptyCellMessage()
      throws Exception
   {
      when(box.getData("Calc1")).thenReturn(new DefaultTableLens(new Object[][] { { "a" }, { 1 } }));

      assertTrue(create(selection(new int[] { 1, 0 })).isEmpty());
      assertEmptyCellMessage();
   }

   @Test
   void aSelectedCellWithoutBindingGivesNoConditionsAndTheEmptyCellMessage() throws Exception {
      when(box.getData("Calc1")).thenReturn(runtimeLens());
      when(layout.getCellBinding(1, 0)).thenReturn(null);

      assertTrue(create(selection(new int[] { 1, 0 })).isEmpty());
      verify(layout).getCellBinding(1, 0);
      assertEmptyCellMessage();
   }

   @Test
   void anEmptySelectionIsTreatedAsAnEmptyCell() throws Exception {
      when(box.getData("Calc1")).thenReturn(runtimeLens());

      assertTrue(create(new HashMap<>()).isEmpty());
      assertEmptyCellMessage();
   }

   @Test
   void aSelectedBoundCellIsNotAnEmptyCell() throws Exception {
      when(box.getData("Calc1")).thenReturn(runtimeLens());
      bind(1, 0);
      ConditionList built = new ConditionList();

      try(MockedStatic<TableConditionUtil> util = mockStatic(TableConditionUtil.class)) {
         util.when(() -> TableConditionUtil.createCalcTableConditions(
            any(), any(int[][].class), any(), any())).thenReturn(built);

         assertSame(built, create(selection(new int[] { 1, 0 })));
      }

      verify(dispatcher, never()).sendCommand(any(MessageCommand.class));
   }

   @Test
   void oneBoundCellAmongTheSelectedCellsIsEnough() throws Exception {
      when(box.getData("Calc1")).thenReturn(runtimeLens());
      when(layout.getCellBinding(1, 0)).thenReturn(null);
      bind(2, 1);
      ConditionList built = new ConditionList();

      try(MockedStatic<TableConditionUtil> util = mockStatic(TableConditionUtil.class)) {
         util.when(() -> TableConditionUtil.createCalcTableConditions(
            any(), any(int[][].class), any(), any())).thenReturn(built);

         assertSame(built, create(selection(new int[] { 1, 0 }, new int[] { 2, 1 })));
      }

      verify(dispatcher, never()).sendCommand(any(MessageCommand.class));
   }
}
