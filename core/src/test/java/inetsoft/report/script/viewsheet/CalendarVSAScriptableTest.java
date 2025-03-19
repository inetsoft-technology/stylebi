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
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class CalendarVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private CalendarVSAScriptable calendarVSAScriptable;
   private CalendarVSAssemblyInfo calendarVSAssemblyInfo;
   private CalendarVSAssembly calendarVSAssembly;
   private VSAScriptable vsaScriptable;
   private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      calendarVSAssembly = new CalendarVSAssembly();
      calendarVSAssemblyInfo = (CalendarVSAssemblyInfo) calendarVSAssembly.getVSAssemblyInfo();
      calendarVSAssemblyInfo.setName("Calendar1");
      viewsheet.addAssembly(calendarVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      calendarVSAScriptable = new CalendarVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      calendarVSAScriptable.setAssembly("Calendar1");
      vsaScriptable.setAssembly("Calendar1");
   }

   @Test
   void testAddProperties() {
      calendarVSAScriptable.addProperties();
      String[] keys = {"daySelection", "period", "singleSelection", "submitOnChange", "yearView", "dropdown", "doubleCalendar"};

      for (String key : keys) {
         assert calendarVSAScriptable.get(key, null) instanceof Boolean;
      }
   }

   @Test
   void testSetGetProperties() {
      // Set the showType property to true and assert that it is true
      calendarVSAScriptable.setShowType(true);
      assert calendarVSAScriptable.getShowType();

      // Set the viewMode property to true and assert that it is true
      calendarVSAScriptable.setViewMode(true);
      assert calendarVSAScriptable.getViewMode();

      // Assert that the suffix property for "selectedObjects" is "[]"
      assert calendarVSAScriptable.getSuffix("selectedObjects").equals("[]");

      // Assert that the fields property is null
      assert calendarVSAScriptable.getFields() == null;

      // Set the fields property to an array containing "field1" and assert that it is not null
      calendarVSAScriptable.setFields(new Object[] {"field1"});
      assert calendarVSAScriptable.getFields() != null;

      // Set the selectedObjects property to an array containing a Date object and assert that it is not null
      Object[] dateArrarys = new Object[2];
      dateArrarys[0] = new Date(125, 3, 20);
      dateArrarys[1] = "d2025-03-20";
      calendarVSAScriptable.setSelectedObjects(dateArrarys);
      Object[] resResult = calendarVSAScriptable.getSelectedObjects();
      Object[] copiedArray = Arrays.copyOf(resResult, resResult.length);
      assert Arrays.toString(copiedArray).equals("[w2025-2-5, d2025-3-20]");

      // Assert that the min property is null
      assert calendarVSAScriptable.getMin() == null;
      // check set min as String
      calendarVSAScriptable.setMin("2025-03-19");
      assert simpleDateFormat.format(calendarVSAScriptable.getMin()).equals("2025-03-19");

      // Assert that the max property is null
      assert calendarVSAScriptable.getMax() == null;
      // Set the max property to a Date object and assert that it is not null
      calendarVSAScriptable.setMax(new Date(125,2,20));
      assert simpleDateFormat.format(calendarVSAScriptable.getMax()).equals("2025-03-20");
   }
}
