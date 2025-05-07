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
import inetsoft.uql.viewsheet.LineVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.LineVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class LineVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private LineVSAScriptable lineVSAScriptable;
   private LineVSAssemblyInfo lineVSAssemblyInfo;
   private LineVSAssembly lineVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      lineVSAssembly = new LineVSAssembly();
      lineVSAssemblyInfo = (LineVSAssemblyInfo) lineVSAssembly.getVSAssemblyInfo();
      lineVSAssemblyInfo.setName("Line1");
      viewsheet.addAssembly(lineVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      lineVSAScriptable = new LineVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      lineVSAScriptable.setAssembly("Line1");
      vsaScriptable.setAssembly("Line1");
   }

   @Test
   void testGetClassName() {
      assertEquals("LineVSA", lineVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      lineVSAScriptable.addProperties();
      assertEquals(StyleConstants.NO_BORDER, lineVSAScriptable.get("beginArrowStyle", null));

      lineVSAScriptable.setProperty("lineStyle", StyleConstants.THICK_LINE);
      assertEquals(StyleConstants.THICK_LINE, lineVSAScriptable.get("lineStyle", null));
   }

   @Test
   void testSetSize() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      //default size if size value is invalid
      lineVSAScriptable.setSize(new Dimension(-1, -1));
      assertEquals(new Dimension(50, 50), lineVSAScriptable.getSize());

      //size value is valid
      Dimension size1 = new Dimension(180, 70);
      lineVSAScriptable.setSize(size1);
      assertEquals(size1, lineVSAScriptable.getSize());
   }
}