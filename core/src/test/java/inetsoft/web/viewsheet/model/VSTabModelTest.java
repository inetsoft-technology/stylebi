/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.model;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.web.viewsheet.model.calendar.VSCalendarModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SreeHome()
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VSTabModelTest {
   private TabVSAssembly assembly;

   @BeforeEach
   void setup() {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("Viewsheet1");
      assembly = new TabVSAssembly();
      TabVSAssemblyInfo assemblyInfo = (TabVSAssemblyInfo) assembly.getVSAssemblyInfo();
      assemblyInfo.setName("Tab1");
      vs.addAssembly(assembly);
      when(rvs.getID()).thenReturn("Viewsheet1");
      when(rvs.getViewsheet()).thenReturn(vs);
   }

   // Bug #10627, show tab children in embedded viewsheet
   @Test
   void canShowActiveTabInEmbeddedVS() throws Exception {
      Viewsheet vs2 = new Viewsheet();
      vs2.getVSAssemblyInfo().setName("Viewsheet2");
      Viewsheet vs = assembly.getViewsheet();
      vs.setViewsheet(vs2);

      CalendarVSAssembly calendar = new CalendarVSAssembly();
      CalendarVSAssemblyInfo calendarInfo = (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
      calendarInfo.setName("Calendar1");
      vs.addAssembly(calendar);

      CalendarVSAssembly calendar2 = new CalendarVSAssembly();
      CalendarVSAssemblyInfo calendarInfo2 = (CalendarVSAssemblyInfo) calendar2.getVSAssemblyInfo();
      calendarInfo2.setName("Calendar2");
      vs.addAssembly(calendar2);

      TabVSAssemblyInfo assemblyInfo = (TabVSAssemblyInfo) assembly.getVSAssemblyInfo();
      assemblyInfo.setAssemblies(new String[] {calendar.getName(), calendar2.getName()});
      assembly.setSelectedValue(calendar.getName());
      assembly.getInfo().setVisible(true);

      VSCalendarModel calendarModel = new VSCalendarModel(calendar, rvs);
      assertTrue(calendarModel.isActive());
   }

   @Mock RuntimeViewsheet rvs;
}
