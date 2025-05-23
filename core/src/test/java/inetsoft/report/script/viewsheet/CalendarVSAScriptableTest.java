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

import static org.junit.jupiter.api.Assertions.*;
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
      String[] keys = {"daySelection", "period", "singleSelection", "submitOnChange",
                       "yearView", "dropdown", "doubleCalendar"};

      for (String key : keys) {
         assert calendarVSAScriptable.get(key, null) instanceof Boolean;
      }
   }

   @Test
   void testSetGetProperties() {
      // Set the showType property to true and assert that it is true
      calendarVSAScriptable.setShowType(true);
      assertTrue(calendarVSAScriptable.getShowType());

      calendarVSAScriptable.setShowTypeValue(false);
      assertEquals(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE,
                   calendarVSAssemblyInfo.getShowTypeValue());

      // Set the viewMode property to true and assert that it is true
      calendarVSAScriptable.setViewMode(true);
      assertTrue(calendarVSAScriptable.getViewMode());
      calendarVSAScriptable.setViewModeValue(false);
      assertEquals(CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE,
                   calendarVSAssemblyInfo.getViewModeValue());

      calendarVSAssemblyInfo.setShowType(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      assertTrue(calendarVSAScriptable.getShowType());

      // Assert that the suffix property for "selectedObjects" is "[]"
      assertEquals("[]", calendarVSAScriptable.getSuffix("selectedObjects"));

      // Assert that the fields property is null
      assertNull(calendarVSAScriptable.getFields());

      // Set the fields property to an array containing "field1" and assert that it is not null
      calendarVSAScriptable.setFields(new Object[] {"field1"});
      assertNotNull(calendarVSAScriptable.getFields());

      // Assert that the min property is null
      assertNull(calendarVSAScriptable.getMin());
      // check set min as String
      calendarVSAScriptable.setMin("2025-03-19");
      assertEquals("2025-03-19", simpleDateFormat.format(calendarVSAScriptable.getMin()));
      // check set min as Date
      calendarVSAScriptable.setMin(new Date(125,1,20));
      assertEquals("2025-02-20", simpleDateFormat.format(calendarVSAScriptable.getMin()));

      // Assert that the max property is null
      assertNull(calendarVSAScriptable.getMax());
      // Set the max property to a Date object and assert that it is not null
      calendarVSAScriptable.setMax(new Date(125,2,20));
      assertEquals("2025-03-20", simpleDateFormat.format(calendarVSAScriptable.getMax()));
      // Set the max property to a String object and assert that it is not null
      calendarVSAScriptable.setMax("2025-04-19");
      assertEquals("2025-04-19", simpleDateFormat.format(calendarVSAScriptable.getMax()));
   }

   @Test
   void testSetSelectedObjectsOnWeek() {
      //check isPeriod is false, week selected, string
      Object[] arrs2 = new Object[2];
      arrs2[0] = "w2025-1-2";
      arrs2[1] = "w2025-1-4";
      calendarVSAScriptable.setSelectedObjects(arrs2);
      assertArrayEquals(arrs2, calendarVSAScriptable.getSelectedObjects());

      //check isPeriod is true, value is string
      calendarVSAssemblyInfo.setPeriod(true);
      String[] range1 = new String[2];
      range1[0] = "w2025-1-3";
      range1[1] = "w2025-2-4";
      String[] arrs3 = {"w2025-2-1", "w2025-3-2"};
      calendarVSAssemblyInfo.setDates(arrs3);

      String[] expect = {"w2025-2-1", "w2025-2-4"};
      calendarVSAScriptable.setSelectedObjects(range1);
      assertArrayEquals(expect, calendarVSAScriptable.getSelectedObjects());
   }

   @Test
   void testSetSelectedObjectsOnMonthDay() {
      calendarVSAssemblyInfo.setDaySelection(true);
      calendarVSAssemblyInfo.setYearView(false);
      String[] d1 = {"d2025-2-12", "d2025-3-20"};
      calendarVSAScriptable.setSelectedObjects(d1);
      assertArrayEquals(d1, calendarVSAScriptable.getSelectedObjects());

      //set year view, and set month
      calendarVSAssemblyInfo.setYearView(true);
      calendarVSAssemblyInfo.setDaySelection(false);
      String[] m2 = {"m2025-2", "m2025-3"};
      calendarVSAScriptable.setSelectedObjects(m2);
      assertArrayEquals(m2, calendarVSAScriptable.getSelectedObjects());

      //set year view, and set month
      String[] m3 = {"y2025", "y2027"};
      calendarVSAScriptable.setSelectedObjects(m3);
      assertArrayEquals(m3, calendarVSAScriptable.getSelectedObjects());
   }

   /**
    * Set the selectedObjects property to an array containing a Date object
    * see Bug #70543
    */
   @Test
   void testSetDateTypeOnSelectedObjects() {
      // check set multi date on no month,  selected week, day selected is false
      Object[] dateArrays = new Object[2];
      dateArrays[0] = new Date(125, 1, 5);
      dateArrays[1] = new Date(125, 1, 25);
      calendarVSAScriptable.setSelectedObjects(dateArrays);
      String[] expect1 = {"w2025-1-2", "w2025-1-5"};
      assertArrayEquals(expect1, calendarVSAScriptable.getSelectedObjects());

      //check set multi date on week, double calendar is true
      dateArrays[1] = new Date(125, 2, 25);
      calendarVSAScriptable.setViewMode(true); //set double calendar
      calendarVSAScriptable.setSelectedObjects(dateArrays);
      String[] expect2 = {"w2025-1-2", "w2025-2-5"};
      assertArrayEquals(expect2, calendarVSAScriptable.getSelectedObjects());

      //check set multi date on week, day selected is true
      calendarVSAssemblyInfo.setDaySelection(true);
      calendarVSAScriptable.setViewMode(false); //set single calendar
      calendarVSAScriptable.setSelectedObjects(dateArrays);
      String[] expect3 = {"d2025-1-5", "d2025-2-25"};
      assertArrayEquals(expect3, calendarVSAScriptable.getSelectedObjects());

      //check set multi date on year view,double calendar is true
      calendarVSAScriptable.setViewMode(true);
      calendarVSAssemblyInfo.setYearView(true);
      Object[] dateArrays2 = new Object[2];
      dateArrays2[0] = new Date(125, 1, 5);
      dateArrays2[1] = new Date(126, 3, 25);
      calendarVSAScriptable.setSelectedObjects(dateArrays2);
      String[] expect4 = {"m2025-1", "m2026-3"};
      assertArrayEquals(expect4, calendarVSAScriptable.getSelectedObjects());
   }
}