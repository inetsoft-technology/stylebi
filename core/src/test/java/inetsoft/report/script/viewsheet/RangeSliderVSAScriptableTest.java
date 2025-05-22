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
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class RangeSliderVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private RangeSliderVSAScriptable rangeSliderVSAScriptable;
   private TimeSliderVSAssemblyInfo rangeSliderVSAssemblyInfo;
   private TimeSliderVSAssembly rangeSliderVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      rangeSliderVSAssembly = new TimeSliderVSAssembly();
      rangeSliderVSAssembly.setStateSelectionList(new SelectionList());
      rangeSliderVSAssemblyInfo =
         (TimeSliderVSAssemblyInfo) rangeSliderVSAssembly.getVSAssemblyInfo();
      rangeSliderVSAssemblyInfo.setName("RangeSlider1");
      viewsheet.addAssembly(rangeSliderVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      rangeSliderVSAScriptable = new RangeSliderVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      rangeSliderVSAScriptable.setAssembly("RangeSlider1");
      vsaScriptable.setAssembly("RangeSlider1");
   }

   @Test
   void testGetClassName() {
      assertEquals("RangeSliderVSA", rangeSliderVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      rangeSliderVSAScriptable.addProperties();

      String[] keys = {"minVisible", "maxVisible", "currentVisible",
                       "tickVisible", "logScale", "upperInclusive", "composite"};

      for (String key : keys) {
         assert rangeSliderVSAScriptable.get(key, rangeSliderVSAScriptable) instanceof Boolean;
      }
   }

   @Test
   void testPut() {
      assertEquals(true,
                   rangeSliderVSAScriptable.get("minVisible", rangeSliderVSAScriptable));
      rangeSliderVSAScriptable.put("minVisible", rangeSliderVSAScriptable, false);
      assertEquals(false,
                   rangeSliderVSAScriptable.get("minVisible", rangeSliderVSAScriptable));
   }

   @Test
   void testSetGetRangeMinMax() {
      SelectionList selectionList = new SelectionList();
      SelectionValue[] selectionValues = new SelectionValue[2];
      selectionValues[0] = new SelectionValue("label1", "5");
      selectionValues[1] = new SelectionValue("label2", "10");
      selectionValues[0].setSelected(true);
      selectionList.setSelectionValues(selectionValues);
      rangeSliderVSAssemblyInfo.setSelectionList(selectionList);

      rangeSliderVSAScriptable.setRangeMin(1);
      assertEquals(1, rangeSliderVSAScriptable.getRangeMin());
      rangeSliderVSAScriptable.setRangeMax(19);
      assertEquals(19, rangeSliderVSAScriptable.getRangeMax());
   }

   @Test
   void testSetGetRangeType() {
      rangeSliderVSAScriptable.setRangeType(1);
      assertEquals(1, rangeSliderVSAScriptable.getRangeType());
      rangeSliderVSAScriptable.setRangeTypeValue(3);
      assertEquals(3, rangeSliderVSAScriptable.getRangeType());
   }

   @Test
   void testSetGetRangeSize() {
      rangeSliderVSAScriptable.setRangeSize(15);
      assertEquals(15, rangeSliderVSAScriptable.getRangeSize());
      rangeSliderVSAScriptable.setRangeSizeValue(28);
      assertEquals(28, rangeSliderVSAScriptable.getRangeSize());

      rangeSliderVSAScriptable.setMaxRangeSize(42);
      assertEquals(42, rangeSliderVSAScriptable.getMaxRangeSize());
      rangeSliderVSAScriptable.setMaxRangeSizeValue(58);
      assertEquals(58, rangeSliderVSAScriptable.getMaxRangeSize());

      //composite mode
      rangeSliderVSAssemblyInfo.setComposite(true);
      rangeSliderVSAssemblyInfo.setTimeInfo(new CompositeTimeInfo());
      assertEquals(0, rangeSliderVSAScriptable.getRangeSize());
      assertEquals(0, rangeSliderVSAScriptable.getMaxRangeSize());
   }

   @Test
   void testGetSetFields() {
      assertArrayEquals(new Object[] {}, rangeSliderVSAScriptable.getFields());

      //normal mode
      Object [] fields = new Object[2];
      fields[0] = "field1";
      fields[1] = "field2";
      rangeSliderVSAScriptable.setFields(fields);
      assertArrayEquals(new Object[] { "field1" }, rangeSliderVSAScriptable.getFields());

      //composite mode
      rangeSliderVSAssemblyInfo.setComposite(true);
      CompositeTimeInfo compositeTimeInfo = new CompositeTimeInfo();
      ColumnRef [] columnRefs = new ColumnRef[2];
      columnRefs[0] = new ColumnRef();
      columnRefs[1] = new ColumnRef();
      columnRefs[0].setDataRef(new AttributeRef("entity", "field1"));
      columnRefs[1].setDataRef(new AttributeRef("entity", "field2"));
      compositeTimeInfo.setDataRefs(columnRefs);
      rangeSliderVSAssemblyInfo.setTimeInfo(compositeTimeInfo);
      rangeSliderVSAScriptable.setFields(fields);
      assertArrayEquals(fields, rangeSliderVSAScriptable.getFields());
   }
}