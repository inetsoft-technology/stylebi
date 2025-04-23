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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.awt.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

public class CrosstabVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;

   private CrosstabVSAScriptable crosstabVSAScriptable;

   private CrosstabVSAssemblyInfo crosstabVSAssemblyInfo;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs-crosstab-1");

      CrosstabVSAssembly crosstabVSAssembly = new CrosstabVSAssembly();

      crosstabVSAssemblyInfo = (CrosstabVSAssemblyInfo) crosstabVSAssembly.getVSAssemblyInfo();
      crosstabVSAssemblyInfo.setName("crosstab1");

      viewsheet.addAssembly(crosstabVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs-crosstab-1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      crosstabVSAScriptable = new CrosstabVSAScriptable(viewsheetSandbox);
      crosstabVSAScriptable.setAssembly("crosstab1");
   }

   @Test
   void testAddProperties() throws Exception {
      crosstabVSAScriptable.addProperties();

      String[] keys = {"fillBlankWithZero", "summarySideBySide", "drillEnabled", "mergeSpan", "sortOthersLast",
                       "computeTrendAndComparisonForTotals", "dateComparisonEnabled"};
      for (String key : keys) {
         assert crosstabVSAScriptable.get(key, null) instanceof Boolean;
      }

      crosstabVSAScriptable.setQuery("query1");
      assert crosstabVSAScriptable.get("query", null).equals("query1");

      assert crosstabVSAScriptable.get("bindingInfo", null) instanceof VSCrosstabBindingScriptable;
   }

   @Test
   void testSetGetFunction() throws Exception {
      crosstabVSAScriptable.setQuery("query1");
      assert crosstabVSAScriptable.getQuery().equals("query1");

      Dimension dimension = new Dimension(500,280);
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 5.0},
         {"b", 3, 10.0},
         {"b", 1, 2.5},
         {"c", 1, 3.0}
      });

      when(viewsheetSandbox.isRuntime()).thenReturn(true);
      when(viewsheetSandbox.getData("crosstab1")).thenReturn(tbl1);

      crosstabVSAScriptable.setTable(tbl1);
      crosstabVSAScriptable.setSize(dimension);
      assert crosstabVSAScriptable.getSize().equals(dimension);

      assert crosstabVSAScriptable.isCrosstabOrCalc();

      crosstabVSAScriptable.addProperties();
      assert crosstabVSAScriptable.getBindingInfo() != null;

      crosstabVSAScriptable.clearCache();
   }
}
