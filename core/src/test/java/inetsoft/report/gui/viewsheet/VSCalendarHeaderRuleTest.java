/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.gui.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSTableStructureDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The exported month view draws the rule under its header itself. A modern calendar takes the
 * colour its header format seeds, as the viewer draws it; legacy and the dropdown keep the grey.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSCalendarHeaderRuleTest {
   private static final Color LEGACY = new Color(0xDDDDDD);

   @Test
   void aLightModernCalendarDrawsTheHeaderSeparator() {
      assertEquals(VSTableStructureDefaults.headerSeparator(VizContext.of(VizMark.MODERN_LIGHT)),
                   rule(VizMark.MODERN_LIGHT, CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE));
   }

   @Test
   void aDarkModernCalendarDrawsTheDarkHeaderSeparator() {
      assertEquals(VSTableStructureDefaults.headerSeparator(VizContext.of(VizMark.MODERN_DARK)),
                   rule(VizMark.MODERN_DARK, CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE));
   }

   @Test
   void aLegacyCalendarKeepsTheGrey() {
      assertEquals(LEGACY, rule(null, CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE));
   }

   @Test
   void aModernDropdownCalendarKeepsTheGrey() {
      assertEquals(LEGACY, rule(VizMark.MODERN_DARK, CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE));
   }

   private static Color rule(VizMark mark, int showType) {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setVizMark(mark);
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.initDefaultFormat();
      vs.addAssembly(calendar);
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
      assertEquals(mark, info.getVizMark(), "precondition: the calendar carries the sheet's mark");

      if(showType != info.getShowType()) {
         CalendarVSAssemblyInfo source = (CalendarVSAssemblyInfo) info.clone();
         source.setShowTypeValue(showType);
         info.copyInfo(source);
      }

      VSCompositeFormat header =
         info.getFormatInfo().getFormat(CalendarVSAssemblyInfo.CALENDAR_TITLE_PATH, false);
      return VSCalendar.headerRuleColor(info, header);
   }
}
