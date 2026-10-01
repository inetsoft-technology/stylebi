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
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.util.RoundDecimalFormat;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.text.DecimalFormat;
import java.text.Format;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77500, an empty &lt;labelFmt&gt; / &lt;valueFmt&gt; element of a decimal format class
 * must not leave the parsed format with unlimited fraction digits, or writeXML's toPattern()
 * builds a ~2^31 char string.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TimeSliderSelectionEmptyFormatTest {
   private static final String DEFAULT_PATTERN = "#,##0.###";
   private static final String LABEL = "<labelFmt class=\"java.text.DecimalFormat\">#,##0</labelFmt>";
   private static final String VALUE = "<valueFmt class=\"java.text.DecimalFormat\">#0</valueFmt>";

   /**
    * Builds valid TimeSliderSelection XML (numeric values 1..3, increment 1) by writing a real
    * object, then replaces the label and/or value format element.
    */
   private static String craftXml(String label, String value) {
      SelectionList list = new SelectionList();
      SelectionValue[] values = new SelectionValue[3];

      for(int i = 0; i < values.length; i++) {
         values[i] = new SelectionValue(String.valueOf(i + 1), String.valueOf(i + 1));
         values[i].setSelected(true);
      }

      list.setSelectionValues(values);

      TimeSliderSelection sel = new TimeSliderSelection();
      sel.setIncrement(1);
      sel.setLabelFormat(new DecimalFormat("#,##0"));
      sel.setValueFormat(new DecimalFormat("0"));

      String xml = write(sel, list);
      assertTrue(xml.contains(LABEL) && xml.contains(VALUE), xml);
      return xml.replace(LABEL, label).replace(VALUE, value);
   }

   private static String write(TimeSliderSelection sel, SelectionList list) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      sel.writeXML(pw, list);
      pw.flush();
      return sw.toString();
   }

   private static TimeSliderSelection parse(String xml, SelectionList list) throws Exception {
      Element elem = Tool.parseXML(new StringReader(xml)).getDocumentElement();
      TimeSliderSelection sel = new TimeSliderSelection();
      sel.parseXML(elem, list);
      return sel;
   }

   private static void assertDefault(Format fmt) {
      assertNotNull(fmt);
      assertEquals(3, ((DecimalFormat) fmt).getMaximumFractionDigits());
      assertEquals(DEFAULT_PATTERN, ((DecimalFormat) fmt).toPattern());
   }

   @Test
   void validFormatsRoundTrip() throws Exception {
      SelectionList list = new SelectionList();
      TimeSliderSelection sel = parse(craftXml(LABEL, VALUE), list);
      assertEquals("#,##0", ((DecimalFormat) sel.getLabelFormat()).toPattern());
      assertEquals("#0", ((DecimalFormat) sel.getValueFormat()).toPattern());
      assertEquals(3, list.getSelectionValueCount());
   }

   @Test
   void emptyLabelFmtElementUsesDefaultPattern() throws Exception {
      SelectionList list = new SelectionList();
      TimeSliderSelection sel = parse(
         craftXml("<labelFmt class=\"java.text.DecimalFormat\"></labelFmt>", VALUE), list);
      assertDefault(sel.getLabelFormat());
      assertTrue(write(sel, list).contains(
         "<labelFmt class=\"java.text.DecimalFormat\">" + DEFAULT_PATTERN + "</labelFmt>"));
   }

   @Test
   void emptyValueFmtElementUsesDefaultPattern() throws Exception {
      SelectionList list = new SelectionList();
      TimeSliderSelection sel = parse(
         craftXml(LABEL, "<valueFmt class=\"java.text.DecimalFormat\"></valueFmt>"), list);
      assertDefault(sel.getValueFormat());
      assertTrue(write(sel, list).contains(
         "<valueFmt class=\"java.text.DecimalFormat\">" + DEFAULT_PATTERN + "</valueFmt>"));
   }

   @Test
   void emptyRoundDecimalFormatElementUsesDefaultPattern() throws Exception {
      String cls = RoundDecimalFormat.class.getName();
      TimeSliderSelection sel = parse(
         craftXml("<labelFmt class=\"" + cls + "\"></labelFmt>",
                  "<valueFmt class=\"" + cls + "\"></valueFmt>"), new SelectionList());
      assertDefault(sel.getLabelFormat());
      assertDefault(sel.getValueFormat());
   }
}
