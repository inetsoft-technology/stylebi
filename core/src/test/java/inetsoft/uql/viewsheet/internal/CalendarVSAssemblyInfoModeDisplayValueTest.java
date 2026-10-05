/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77809: CalendarVSAssemblyInfo.getDisplayValue() parsed the raw runtime value of the
 * view mode with Integer.parseInt, so a viewsheet whose calendar mode is not an integer
 * literal ("abc", "2.0", " 2 ", or an unevaluated "$(m)" / "=2") threw
 * NumberFormatException. The method must use the lenient getViewMode() like the renderer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CalendarVSAssemblyInfoModeDisplayValueTest {
   private static final String[] DATES = { "y2025", "y2026" };

   /**
    * @param attr     the attribute name written for the mode ("modeValue" or the legacy "mode")
    * @param mode     the attribute value
    * @param expected the view mode getViewMode() resolves the value to
    */
   @ParameterizedTest(name = "{0}=[{1}] -> {2}")
   @CsvSource(value = {
      "modeValue|abc|1",
      "modeValue|2.0|2",
      "modeValue|$(m)|1",
      "modeValue|=2|1",
      "modeValue| 2 |2",
      "mode|abc|1",
      "mode|2.0|2",
      "modeValue|1|1",
      "modeValue|2|2",
      "modeValue||1",
      "modeValue|3|3",
   }, delimiter = '|', ignoreLeadingAndTrailingWhitespace = false)
   void displayValueFollowsViewMode(String attr, String mode, int expected) throws Exception {
      Viewsheet vs = load(attr, mode == null ? "" : mode, expected);
      CalendarVSAssemblyInfo info = calendarInfo(vs);
      assertEquals(expected, info.getViewMode());

      info.setDates(DATES);
      // reference: same calendar with the resolved mode set as an integer runtime value
      CalendarVSAssemblyInfo ref = (CalendarVSAssemblyInfo) info.clone();
      ref.setViewMode(info.getViewMode());

      String all = assertDoesNotThrow(() -> info.getDisplayValue());
      String list = assertDoesNotThrow(() -> info.getDisplayValue(true));
      assertEquals(ref.getDisplayValue(), all);
      assertEquals(ref.getDisplayValue(true), list);

      boolean dual = expected == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE;
      assertEquals(dual, list.contains(CalendarUtil.rangeSpliter.trim()),
                   "range (double) formatting for " + list);

      // with no dates selected the method returns the title / null without throwing
      info.setDates(new String[0]);
      assertDoesNotThrow(() -> info.getDisplayValue());
      assertNull(assertDoesNotThrow(() -> info.getDisplayValue(true)));
   }

   @Test
   void currentSelectionShowsCalendarWithNonIntegerMode() throws Exception {
      Viewsheet vs = load("modeValue", "2.0", 2);
      CalendarVSAssemblyInfo info = calendarInfo(vs);
      info.setDates(DATES);
      CurrentSelectionVSAssembly cs = (CurrentSelectionVSAssembly) vs.getAssembly("CS1");

      assertDoesNotThrow(cs::updateOutSelection);

      CurrentSelectionVSAssemblyInfo csInfo =
         (CurrentSelectionVSAssemblyInfo) cs.getVSAssemblyInfo();
      String[] values = csInfo.getOutSelectionValues();
      assertEquals(1, values.length);
      assertEquals(info.getDisplayValue(true), values[0]);
      assertTrue(values[0].contains(CalendarUtil.rangeSpliter.trim()), values[0]);
   }

   private static CalendarVSAssemblyInfo calendarInfo(Viewsheet vs) {
      CalendarVSAssembly cal = (CalendarVSAssembly) vs.getAssembly("Calendar1");
      return (CalendarVSAssemblyInfo) cal.getVSAssemblyInfo();
   }

   /**
    * Writes a viewsheet with Calendar1 shown in the current selection CS1, replaces the
    * calendar mode attribute and parses it back.
    */
   private static Viewsheet load(String attr, String mode, int stateMode) throws Exception {
      Viewsheet vs = new Viewsheet();
      CalendarVSAssembly cal = new CalendarVSAssembly(vs, "Calendar1");
      cal.getVSAssemblyInfo().setPixelOffset(new Point(10, 10));
      cal.getVSAssemblyInfo().setPixelSize(new Dimension(300, 200));
      vs.addAssembly(cal);
      CurrentSelectionVSAssembly cs = new CurrentSelectionVSAssembly(vs, "CS1");
      cs.getVSAssemblyInfo().setPixelOffset(new Point(400, 10));
      ((CurrentSelectionVSAssemblyInfo) cs.getVSAssemblyInfo())
         .setShowCurrentSelectionValue(true);
      vs.addAssembly(cs);

      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<viewsheet>");
      vs.writeXML(writer);
      writer.println("</viewsheet>");
      writer.flush();
      String xml = buffer.toString();
      String target = " mode=\"1\" modeValue=\"1\"";
      assertEquals(2, xml.split(target, -1).length, "one calendar mode attribute pair");

      String esc = Tool.escape(mode);
      // "modeValue": the current attribute; "mode": a legacy file without modeValue
      xml = xml.replace(target, "modeValue".equals(attr) ?
         " mode=\"1\" modeValue=\"" + esc + "\"" : " mode=\"" + esc + "\"");
      // keep <state_view> as the product writes it (the lenient getViewModeValue()),
      // otherwise parseStateContent overwrites the mode under test
      xml = xml.replace("<state_view mode=\"1\"", "<state_view mode=\"" + stateMode + "\"");

      Viewsheet result = new Viewsheet();
      result.parseXML(Tool.parseXML(new ByteArrayInputStream(
         xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement(), false);
      return result;
   }
}
