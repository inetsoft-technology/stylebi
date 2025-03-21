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
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

public class CalcTableVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private CalcTableVSAScriptable calcTableVSAScriptable;

   private CalcTableVSAssemblyInfo calcTableVSAssemblyInfo;


   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs-calc-1");

      CalcTableVSAssembly calcTableVSAssembly = new CalcTableVSAssembly();
      calcTableVSAssemblyInfo = (CalcTableVSAssemblyInfo) calcTableVSAssembly.getVSAssemblyInfo();
      calcTableVSAssemblyInfo.setName("CalcTable1");
      viewsheet.addAssembly(calcTableVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs-calc-1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      calcTableVSAScriptable = new CalcTableVSAScriptable(viewsheetSandbox);
      calcTableVSAScriptable.setAssembly("CalcTable1");
   }

   @Test
   void testAddProperties() {
      calcTableVSAScriptable.addProperties();

      assert calcTableVSAScriptable.get("layoutInfo") instanceof VSTableLayoutInfo;
      assert calcTableVSAScriptable.get("fillBlankWithZero") instanceof Boolean;
      assert calcTableVSAScriptable.get("sortOthersLast") instanceof Boolean;
   }

   @Test
   void testSetSize() {
      Dimension dim = new Dimension(1, 2);
      calcTableVSAssemblyInfo.setHeaderColCount(3);
      calcTableVSAssemblyInfo.setHeaderRowCount(2);
      calcTableVSAScriptable.setSize(dim);

      assert dim.width == 4 && dim.height == 4;

      assert calcTableVSAScriptable.isCrosstabOrCalc();
      assert calcTableVSAScriptable.getLayoutInfo() == null;
   }

}
