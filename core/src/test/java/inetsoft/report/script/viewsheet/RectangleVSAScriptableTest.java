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

import inetsoft.report.StyleConstants;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.RectangleVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class RectangleVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private RectangleVSAScriptable rectangleVSAScriptable;
   private RectangleVSAssemblyInfo rectangleVSAssemblyInfo;
   private RectangleVSAssembly rectangleVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      rectangleVSAssembly = new RectangleVSAssembly();
      rectangleVSAssemblyInfo = (RectangleVSAssemblyInfo) rectangleVSAssembly.getVSAssemblyInfo();
      rectangleVSAssemblyInfo.setName("Rectangle1");
      viewsheet.addAssembly(rectangleVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      rectangleVSAScriptable = new RectangleVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      rectangleVSAScriptable.setAssembly("Rectangle1");
      vsaScriptable.setAssembly("Rectangle1");
   }

   @Test
   void testGetClassName() {
      assertEquals("RectangleVSA", rectangleVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      rectangleVSAScriptable.addProperties();
      assertEquals(0, rectangleVSAScriptable.get("roundCorner", null));

      rectangleVSAScriptable.setProperty("lineStyle", StyleConstants.THICK_LINE);
      assertEquals(StyleConstants.THICK_LINE, rectangleVSAScriptable.get("lineStyle", null));

      rectangleVSAScriptable.setProperty("shadow", true);
      assertEquals(true, rectangleVSAScriptable.get("shadow", null));
   }

   @Test
   void testGetSetRoundCorner() {
      rectangleVSAScriptable.setRoundCorner(10);
      assertEquals(10, rectangleVSAScriptable.getRoundCorner());
   }

   @Test
   void testGetSetGradientColor() {
      GradientColor gradientColor = mock(GradientColor.class);
      rectangleVSAScriptable.setGradientColor(gradientColor);
      assertEquals(gradientColor, rectangleVSAScriptable.getGradientColor());
   }
}