/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.model;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.table.XTableLens;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.model.table.VSTableModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test cases for {@link VSTableModel}.
 */
@SreeHome()
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VSTableModelTest {
   /**
    * Tests that an unbound, empty table model can be created successfully without
    * throwing any exceptions.
    *
    * @throws AssertionError if the test fails.
    * @throws Exception if an unexpected error occurs.
    *
    * @see <a href="http://issues.inetsoft/issues/16630">Bug #16630</a>
    */
   @Test
   void testCreateUnboundTable() throws Exception {
      Viewsheet viewsheet = new Viewsheet();
      TableVSAssembly assembly = new TableVSAssembly(viewsheet, "Table");
      assembly.initDefaultFormat();
      viewsheet.addAssembly(assembly);
      VSTableLens lens = createEmptyTable();

      Mockito.when(rvs.getViewsheet()).thenReturn(viewsheet);
      Mockito.when(rvs.getViewsheetSandbox()).thenReturn(sandbox);
      System.err.println("sandbox: " + sandbox);
      Mockito.when(sandbox.getVSTableLens(Mockito.anyString(), Mockito.anyBoolean()))
         .thenReturn(lens);


      VSTableModel.VSTableModelFactory factory = new VSTableModel.VSTableModelFactory();
      VSTableModel model = factory.createModel(assembly, rvs);
      assertNotNull(model);
   }

   private VSTableLens createEmptyTable() {
      XEmbeddedTable embedded = new XEmbeddedTable(new String[0], new Object[0][0]);
      TableLens table = new XTableLens(embedded);
      return new VSTableLens(table);
   }

   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetSandbox sandbox;
}
