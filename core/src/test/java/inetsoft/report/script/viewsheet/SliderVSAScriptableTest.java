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
import inetsoft.uql.viewsheet.SliderVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class SliderVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private SliderVSAScriptable sliderVSAScriptable;
   private SliderVSAssemblyInfo sliderVSAssemblyInfo;
   private SliderVSAssembly sliderVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      sliderVSAssembly = new SliderVSAssembly();
      sliderVSAssemblyInfo = (SliderVSAssemblyInfo) sliderVSAssembly.getVSAssemblyInfo();
      sliderVSAssemblyInfo.setName("Slider1");
      viewsheet.addAssembly(sliderVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      sliderVSAScriptable = new SliderVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      sliderVSAScriptable.setAssembly("Slider1");
      vsaScriptable.setAssembly("Slider1");
   }

   @Test
   void testGetClassName() {
      assertEquals("SliderVSA", sliderVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      sliderVSAScriptable.addProperties();

      String[] keys = {"tickVisible", "currentVisible", "labelVisible", "snap"};

      for (String key : keys) {
         assert sliderVSAScriptable.get(key, null) instanceof Boolean;
      }
   }

   @Test
   void testGetSetMax(){
      sliderVSAScriptable.setMax("100");
      assertEquals("100.0", sliderVSAScriptable.getMax());

      //make the max value is < selected value
      sliderVSAScriptable.setSelectedObject("75");
      sliderVSAScriptable.setMax("50");
      assertEquals("50.0", sliderVSAScriptable.getMax());
      assertEquals(Double.parseDouble("50.0"), sliderVSAScriptable.getSelectedObject());
   }

   @Test
   void testGetSetMaxValue(){
      sliderVSAScriptable.setMaxValue("200");
      assertEquals("200.0", sliderVSAScriptable.getMax());
   }

   @Test
   void testGetSetMin(){
      sliderVSAScriptable.setMin("10");
      assertEquals("10.0", sliderVSAScriptable.getMin());

      //make the min value is > selected value
      sliderVSAScriptable.setSelectedObject("15");
      sliderVSAScriptable.setMin("18");
      assertEquals("18.0", sliderVSAScriptable.getMin());
      assertEquals(Double.parseDouble("18.0"), sliderVSAScriptable.getSelectedObject());
   }

   @Test
   void testGetSetMinValue(){
      sliderVSAScriptable.setMinValue("9");
      assertEquals("9.0", sliderVSAScriptable.getMin());
   }
}
