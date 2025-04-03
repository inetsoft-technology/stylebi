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

      calendarVSAssemblyInfo.setShowType(2);
      assert calendarVSAScriptable.getShowType();

      // Assert that the suffix property for "selectedObjects" is "[]"
      assert calendarVSAScriptable.getSuffix("selectedObjects").equals("[]");

      // Assert that the fields property is null
      assert calendarVSAScriptable.getFields() == null;

      // Set the fields property to an array containing "field1" and assert that it is not null
      calendarVSAScriptable.setFields(new Object[] {"field1"});
      assert calendarVSAScriptable.getFields() != null;

      // Assert that the min property is null
      assert calendarVSAScriptable.getMin() == null;
      // check set min as String
      calendarVSAScriptable.setMin("2025-03-19");
      assert simpleDateFormat.format(calendarVSAScriptable.getMin()).equals("2025-03-19");
      // check set min as Date
      calendarVSAScriptable.setMin(new Date(125,1,20));
      assert simpleDateFormat.format(calendarVSAScriptable.getMin()).equals("2025-02-20");

      // Assert that the max property is null
      assert calendarVSAScriptable.getMax() == null;
      // Set the max property to a Date object and assert that it is not null
      calendarVSAScriptable.setMax(new Date(125,2,20));
      assert simpleDateFormat.format(calendarVSAScriptable.getMax()).equals("2025-03-20");
      // Set the max property to a String object and assert that it is not null
      calendarVSAScriptable.setMax("2025-04-19");
      assert simpleDateFormat.format(calendarVSAScriptable.getMax()).equals("2025-04-19");
   }

   @Test
   void testSetSelectedObjectsOnWeek() {
      // Set the selectedObjects property to an array containing a Date object and assert that it is not null
      // check isPeriod is false, week selected, date, exited Issue #70543.
      /*Object[] dateArrarys = new Object[2];
      dateArrarys[0] = new Date(125, 1, 2);
      dateArrarys[1] = new Date(125, 1, 4);;
      calendarVSAScriptable.setSelectedObjects(dateArrarys);
      Object[] res1 = calendarVSAScriptable.getSelectedObjects();
      assert printObject(res1).equals("[w2025-2-2, w2025-2-16]");*/

      //check isPeriod is false, week selected, string
      Object[] arrs2 = new Object[2];
      arrs2[0] = "w2025-1-2";
      arrs2[1] = "w2025-1-4";
      calendarVSAScriptable.setSelectedObjects(arrs2);
      Object[] res2 = calendarVSAScriptable.getSelectedObjects();
      assert printObject(res2).equals("[w2025-1-2, w2025-1-4]");

      //check isPeriod is true, value is Date, To Do, see Issue #70543
      //check isPeriod is true, value is string
      calendarVSAssemblyInfo.setPeriod(true);
      String[] range1 = new String[2];
      range1[0] = "w2025-1-3";
      range1[1] = "w2025-2-4";
      String[] arrs3 = {"w2025-2-1", "w2025-3-2"};
      calendarVSAssemblyInfo.setDates(arrs3);

      calendarVSAScriptable.setSelectedObjects(range1);
      Object[] res3 = calendarVSAScriptable.getSelectedObjects();
      assert printObject(res3).equals("[w2025-2-1, w2025-2-4]");
   }

   @Test
   void testSetSelectedObjectsOnMonthDay() {
      calendarVSAssemblyInfo.setDaySelection(true);
      calendarVSAssemblyInfo.setYearView(false);
      String[] d1 = {"d2025-2-12", "d2025-3-20"};
      calendarVSAScriptable.setSelectedObjects(d1);
      Object[] res1 = calendarVSAScriptable.getSelectedObjects();
      assert printObject(res1).equals("[d2025-2-12, d2025-3-20]");

      //set year view, and set month
      calendarVSAssemblyInfo.setYearView(true);
      calendarVSAssemblyInfo.setDaySelection(false);
      String[] m2 = {"m2025-2", "m2025-3"};
      calendarVSAScriptable.setSelectedObjects(m2);
      Object[] res2 = calendarVSAScriptable.getSelectedObjects();
      assert printObject(res2).equals("[m2025-2, m2025-3]");

      //set year view, and set month
      String[] m3 = {"y2025", "y2027"};
      calendarVSAScriptable.setSelectedObjects(m3);
      Object[] res3 = calendarVSAScriptable.getSelectedObjects();
      assert printObject(res3).equals("[y2025, y2027]");
   }

   /**
    * Set the selectedObjects property to an array containing a Date object
    * see Bug #70543
    */
   @Test
   void testSetDateTypeOnSelectedObjects() {
      // check set multi date on no month,  selected week, day selected is false
      Object[] dateArrarys = new Object[2];
      dateArrarys[0] = new Date(125, 1, 5);
      dateArrarys[1] = new Date(125, 1, 25);
      calendarVSAScriptable.setSelectedObjects(dateArrarys);
      Object[] res1 = calendarVSAScriptable.getSelectedObjects();
      assert printObject(res1).equals("[w2025-1-2, w2025-1-5]");

      //check set multi date on week, double calendar is true
      dateArrarys[1] = new Date(125, 2, 25);
      calendarVSAScriptable.setViewMode(true); //set double calendar
      calendarVSAScriptable.setSelectedObjects(dateArrarys);
      Object[] res2 = calendarVSAScriptable.getSelectedObjects();
      assert printObject(res2).equals("[w2025-1-2, w2025-2-5]");

      //check set multi date on week, day selected is true
      calendarVSAssemblyInfo.setDaySelection(true);
      calendarVSAScriptable.setViewMode(false); //set single calendar
      calendarVSAScriptable.setSelectedObjects(dateArrarys);
      Object[] res3 = calendarVSAScriptable.getSelectedObjects();
      assert printObject(res3).equals("[d2025-1-5, d2025-2-25]");

      //check set multi date on year view,double calendar is true
      calendarVSAScriptable.setViewMode(true);
      calendarVSAssemblyInfo.setYearView(true);
      Object[] dateArrarys2 = new Object[2];
      dateArrarys2[0] = new Date(125, 1, 5);
      dateArrarys2[1] = new Date(126, 3, 25);
      calendarVSAScriptable.setSelectedObjects(dateArrarys2);
      Object[] res4 = calendarVSAScriptable.getSelectedObjects();
      assert printObject(res4).equals("[m2025-1, m2026-3]");
   }

   String printObject(Object[] obj) {
      Object[] copy = Arrays.copyOf(obj, obj.length);
      return Arrays.toString(copy);
   }
}
