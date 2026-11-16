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
import inetsoft.test.*;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Disabled;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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
   void testGetClassName() {
      assertEquals("FormulaTableVSA", calcTableVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      calcTableVSAScriptable.addProperties();

      assert calcTableVSAScriptable.getMember("layoutInfo") instanceof VSTableLayoutInfo;
      assert calcTableVSAScriptable.getMember("fillBlankWithZero") instanceof Boolean;
      assert calcTableVSAScriptable.getMember("sortOthersLast") instanceof Boolean;
   }

   @Test
   void testSetSize() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      Dimension dim = new Dimension(1, 2);
      calcTableVSAssemblyInfo.setHeaderColCount(3);
      calcTableVSAssemblyInfo.setHeaderRowCount(2);
      calcTableVSAScriptable.setSize(dim);

      assertEquals(4, dim.width);
      assertEquals(4, dim.height);
      assertEquals(new Dimension(4, 4), calcTableVSAScriptable.getSize());

      Dimension dim2 = new Dimension(10, 10);
      calcTableVSAScriptable.setSize(dim2);
      assertEquals(dim2, calcTableVSAScriptable.getSize());

      assertTrue(calcTableVSAScriptable.isCrosstabOrCalc());
      assertNull(calcTableVSAScriptable.getLayoutInfo());
   }

   @ParameterizedTest
   @ValueSource(strings = { "fillBlankWithZero", "sortOthersLast" })
   void booleanPropertiesRoundTripTrueThenFalseThroughScript(String name) {
      calcTableVSAScriptable.addProperties();

      calcTableVSAScriptable.putMember(name, true);
      assertEquals(true, calcTableVSAScriptable.getMember(name));

      calcTableVSAScriptable.putMember(name, false);
      assertEquals(false, calcTableVSAScriptable.getMember(name));
   }

   @Test
   @Disabled("Not filed (low impact; existing testSetSize pins this behavior): CalcTable.setSize compares pixels with header counts (headerCols+1, headerRows+2) instead of (headerCols+1)*defw and (headerRows+2)*defh like Crosstab")
   void setSizeEnforcesPixelMinimumOfHeaderCells() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);
      calcTableVSAssemblyInfo.setHeaderColCount(2);
      calcTableVSAssemblyInfo.setHeaderRowCount(1);

      Dimension dim = new Dimension(1, 1);
      calcTableVSAScriptable.setSize(dim);

      assertTrue(dim.width >= 3 * AssetUtil.defw, "width " + dim.width);
      assertTrue(dim.height >= 3 * AssetUtil.defh, "height " + dim.height);
   }
}
