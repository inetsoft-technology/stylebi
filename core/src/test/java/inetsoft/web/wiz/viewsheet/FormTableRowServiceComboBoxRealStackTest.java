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
import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.table.VSFormTableService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #79694, real stack below the plugin: the real {@link FormTableRowService#setCell} drives
 * the real {@code VSFormTableService.changeFormInput} against a real {@link FormTableLens} with a
 * real {@link ComboBoxColumnOption}. Before the fix a value outside the ComboBox's list was stored
 * as is (the native client can only send a label, but nothing server-side checked it).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableRowServiceComboBoxRealStackTest {
   private static ListData list(String[] labels, Object[] values) {
      ListData data = new ListData();
      data.setLabels(labels);
      data.setValues(values);
      return data;
   }

   @Test
   void refusesAValueOutsideTheListAndLeavesTheCellUntouched() throws Exception {
      Fixture f = new Fixture(list(new String[] { "Fruit", "Bakery", "Dairy" },
                                   new Object[] { "Fruit", "Bakery", "Dairy" }));

      Exception e = assertThrows(IllegalArgumentException.class, () -> f.setCell("Toys"));

      assertTrue(e.getMessage().contains("'Toys'"), e.getMessage());
      assertTrue(e.getMessage().contains("'Cat'"), e.getMessage());
      assertTrue(e.getMessage().contains("'T'"), e.getMessage());
      assertTrue(e.getMessage().contains("'Fruit', 'Bakery', 'Dairy'"), e.getMessage());
      assertEquals("Fruit", f.pending());
   }

   @Test
   void acceptsAValueInTheList() throws Exception {
      Fixture f = new Fixture(list(new String[] { "Fruit", "Bakery", "Dairy" },
                                   new Object[] { "Fruit", "Bakery", "Dairy" }));

      f.setCell("Bakery");

      assertEquals("Bakery", f.pending());
   }

   @Test
   void matchIsCaseSensitiveAndUntrimmed() throws Exception {
      Fixture f = new Fixture(list(new String[] { "Fruit" }, new Object[] { "Fruit" }));

      assertThrows(IllegalArgumentException.class, () -> f.setCell("fruit"));
      assertThrows(IllegalArgumentException.class, () -> f.setCell(" Fruit"));
      assertEquals("Fruit", f.pending());
   }

   @Test
   void labelMapsToItsValueAndTheRawValueIsAlsoAccepted() throws Exception {
      Fixture f = new Fixture(list(new String[] { "F", "B" }, new Object[] { "1", "2" }));

      f.setCell("F");
      assertEquals("1", f.pending()); // native label -> value mapping

      f.setCell("2");
      assertEquals("2", f.pending());

      assertThrows(IllegalArgumentException.class, () -> f.setCell("3"));
      assertThrows(IllegalArgumentException.class, () -> f.setCell("Toys"));
      assertEquals("2", f.pending());
   }

   @Test
   void numericValuesMatchOnTheirStringForm() throws Exception {
      Fixture f = new Fixture(list(new String[] { "One", "Two" }, new Object[] { 1, 2 }));

      f.setCell("2");

      assertEquals("2", String.valueOf(f.pending()));
      assertThrows(IllegalArgumentException.class, () -> f.setCell("9"));
   }

   @Test
   void valuesShorterThanLabelsAreGuarded() throws Exception {
      Fixture f = new Fixture(list(new String[] { "A", "B", "C" }, new Object[] { "x" }));

      // the guard must not index past the short values array while scanning for a match
      Exception e = assertThrows(IllegalArgumentException.class, () -> f.setCell("Toys"));
      assertTrue(e.getMessage().contains("'A', 'B', 'C'"), e.getMessage());
   }

   @Test
   void nullValueAndEmptyStringPassThrough() throws Exception {
      Fixture f = new Fixture(list(new String[] { "Fruit" }, new Object[] { "Fruit" }));

      f.setCell("");
      f.setCell(null);

      assertNull(f.pending());
   }

   @Test
   void anUnresolvedListPassesThrough() throws Exception {
      new Fixture(null).setCell("Toys");
      new Fixture(new ListData()).setCell("Toys"); // empty labels, e.g. empty query result

      ListData nullLabels = new ListData();
      nullLabels.setLabels(null);
      new Fixture(nullLabels).setCell("Toys");
   }

   @Test
   void aQueryBackedListIsCheckedTheSameWay() throws Exception {
      ListData data = list(new String[] { "Fruit", "Dairy" }, new Object[] { "Fruit", "Dairy" });
      data.setBinding(true);
      Fixture f = new Fixture(data);

      assertThrows(IllegalArgumentException.class, () -> f.setCell("Toys"));
      f.setCell("Dairy");
      assertEquals("Dairy", f.pending());
   }

   @Test
   void theErrorCapsALongAllowedList() throws Exception {
      String[] labels = new String[30];
      java.util.Arrays.setAll(labels, i -> "L" + i);
      Fixture f = new Fixture(list(labels, labels));

      Exception e = assertThrows(IllegalArgumentException.class, () -> f.setCell("Toys"));

      assertTrue(e.getMessage().contains("'L19'"), e.getMessage());
      assertFalse(e.getMessage().contains("'L20'"), e.getMessage());
      assertTrue(e.getMessage().contains("10 more"), e.getMessage());
   }

   /** One String Form column "Cat", data row "Fruit", with a ComboBox option over the given list. */
   private static final class Fixture {
      Fixture(ListData list) {
         Object seed = "Fruit";
         DefaultTableLens data = new DefaultTableLens(new Object[][] { { "Cat" }, { seed } });
         lens = new FormTableLens(data);

         FormRef ref = new FormRef();
         ref.setDataRef(new AttributeRef(null, "Cat"));
         ref.setDataType("string");
         ComboBoxColumnOption option = new ComboBoxColumnOption(1, "string", "bad {0}", true);
         option.setListData(list);
         ref.setOption(option);
         ColumnSelection columns = new ColumnSelection();
         columns.addAttribute(ref);
         lens.setColumnSelection(columns);
         lens.setEdit(true);

         TableVSAssembly table = mock(TableVSAssembly.class);
         TableVSAssemblyInfo info = mock(TableVSAssemblyInfo.class);
         when(table.getVSAssemblyInfo()).thenReturn(info);
         when(table.getInfo()).thenReturn(info);
         when(info.clone(anyBoolean())).thenReturn(info);
         when(info.isForm()).thenReturn(true);
         when(info.getColumnSelection()).thenReturn(columns);

         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getAssembly(anyString())).thenReturn(table);

         ViewsheetSandbox box = mock(ViewsheetSandbox.class);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.isRuntime()).thenReturn(true);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

         ViewsheetService viewsheets = mock(ViewsheetService.class);
         CoreLifecycleService lifecycle = mock(CoreLifecycleService.class);
         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

         try {
            when(box.getFormTableLens(anyString())).thenReturn(lens);
            when(viewsheets.getViewsheet(anyString(), any())).thenReturn(rvs);
            when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

            // CoreLifecycleService.sendMessage's own body
            doAnswer(inv -> {
               MessageCommand command = new MessageCommand();
               command.setMessage(inv.getArgument(0));
               command.setType(inv.getArgument(1));
               ((CommandDispatcher) inv.getArgument(2)).sendCommand(command);
               return null;
            }).when(lifecycle).sendMessage(any(), any(), any());

            doAnswer(inv -> {
               ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
               CapturingCommandDispatcher.withCapturingDispatcher(inv.getArgument(1), d -> {
                  mutation.run(rvs, "rt1", d);
                  return null;
               });
               return List.of();
            }).when(sessions).mutate(anyString(), any(Principal.class), any());
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }

         service = new FormTableRowService(sessions, new VSFormTableService(viewsheets, lifecycle));
      }

      void setCell(String value) throws Exception {
         service.setCell("tok", mock(Principal.class), "T", 0, 0, value, "");
      }

      /** The lens's pending value of the data row. */
      Object pending() {
         return lens.rows()[1].get(0);
      }

      private final FormTableLens lens;
      private final FormTableRowService service;
   }
}
