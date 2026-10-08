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

import inetsoft.test.*;
import inetsoft.uql.viewsheet.ComboBoxVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78047: a combo box default value such as "R&D" was written unescaped into an XML
 * attribute, so the saved viewsheet could not be parsed and never opened again. The same
 * applies to a calendar week format, which a script can set to any text.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class InputDefaultValueEscapeTest {
   @ParameterizedTest
   @ValueSource(strings = { "R&D", "AT&T", "a<b", "say \"hi\"", "&amp;", "it's", "plain" })
   void comboBoxDefaultValueSurvivesSaveAndReload(String value) throws Exception {
      ComboBoxVSAssemblyInfo info = new ComboBoxVSAssemblyInfo();
      info.setDefaultValue(value);

      for(int i = 0; i < 2; i++) {
         info = parse(new ComboBoxVSAssemblyInfo(), write(info));
         assertEquals(value, info.getDefaultValue(), "pass " + i);
      }
   }

   @Test
   void comboBoxNullDefaultValueStaysNull() throws Exception {
      ComboBoxVSAssemblyInfo info = new ComboBoxVSAssemblyInfo();
      info.setDefaultValue(null);
      String xml = write(info);

      assertTrue(xml.contains(" defaultValue=\"" + Tool.NULL_PARAMETER_VALUE + "\""), xml);
      assertNull(parse(new ComboBoxVSAssemblyInfo(), xml).getDefaultValue());
   }

   @Test
   void viewsheetWithComboBoxDefaultValueCanBeReopened() throws Exception {
      Viewsheet vs = new Viewsheet();
      ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "ComboBox1");
      ((ComboBoxVSAssemblyInfo) combo.getVSAssemblyInfo()).setDefaultValue("R&D");
      vs.addAssembly(combo);

      ByteArrayOutputStream out = new ByteArrayOutputStream();

      try(PrintWriter writer = new PrintWriter(
         new OutputStreamWriter(out, StandardCharsets.UTF_8)))
      {
         vs.writeXML(writer);
      }

      Element elem = Tool.parseXML(new ByteArrayInputStream(out.toByteArray()))
         .getDocumentElement();
      Viewsheet reopened = new Viewsheet();
      reopened.parseXML(elem);

      ComboBoxVSAssembly reloaded = (ComboBoxVSAssembly) reopened.getAssembly("ComboBox1");
      assertNotNull(reloaded);
      assertEquals("R&D",
         ((ComboBoxVSAssemblyInfo) reloaded.getVSAssemblyInfo()).getDefaultValue());
   }

   @Test
   void calendarWeekFormatSurvivesSaveAndReload() throws Exception {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setWeekFormatValue("W & \"yyyy\" <x>");

      CalendarVSAssemblyInfo parsed = parse(new CalendarVSAssemblyInfo(), write(info));
      assertEquals("W & \"yyyy\" <x>", parsed.getWeekFormatValue());
   }

   @Test
   void calendarScriptWeekFormatCanBeSaved() throws Exception {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setWeekFormat("W & yyyy");

      assertDoesNotThrow(() -> parse(new CalendarVSAssemblyInfo(), write(info)));
   }

   private static <T extends VSAssemblyInfo> T parse(T info, String xml) throws Exception {
      Element elem = Tool.parseXML(new StringReader(xml)).getDocumentElement();
      info.parseXML(elem);
      return info;
   }

   private static String write(VSAssemblyInfo info) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         info.writeXML(writer);
      }

      return buffer.toString();
   }
}
