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

package inetsoft.uql.xmla;

import inetsoft.util.xml.XMLAParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82387: the MDSCHEMA_MEASURES loader marked a measure calculated only when an EXPRESSION
 * column was present. Mondrian sends none; it reports a calculated member (FoodMart's Profit)
 * with MEASURE_AGGREGATOR 127, so no stored measure was ever calculated. The rows below are the
 * shape the FoodMart fixture returns for the Sales cube.
 */
@Tag("core")
class DiscoverHandlerCalcMeasureTest {
   private static String row(String name, int aggregator, String extra) {
      return "<row><MEASURE_NAME>" + name + "</MEASURE_NAME><MEASURE_UNIQUE_NAME>[Measures].[" +
         name + "]</MEASURE_UNIQUE_NAME><MEASURE_CAPTION>" + name + "</MEASURE_CAPTION>" +
         "<MEASURE_AGGREGATOR>" + aggregator + "</MEASURE_AGGREGATOR><DATA_TYPE>5</DATA_TYPE>" +
         extra + "</row>";
   }

   private static Map<String, Measure> load(String rows) throws Exception {
      XMLAParser parser = new XMLAParser();
      parser.setInput(new StringReader("<root>" + rows + "</root>"));
      DiscoverHandler handler = new DiscoverHandler();
      handler.reqType = XMLAUtil.MEASURES_REQUEST;
      List list = new ArrayList();
      handler.objList = list;
      handler.setXMLPParser(parser);
      int state;

      while((state = parser.next()) != XMLAParser.END_DOCUMENT) {
         switch(state) {
         case XMLAParser.START_TAG -> handler.startElement();
         case XMLAParser.TEXT -> handler.characters();
         case XMLAParser.END_TAG -> handler.endElement();
         default -> {
         }
         }
      }

      Map<String, Measure> result = new LinkedHashMap<>();

      for(Object o : list) {
         Measure m = (Measure) o;
         result.put(m.getName(), m);
      }

      return result;
   }

   @Test
   void aggregator127IsACalculatedMeasureAndTheStoredMeasuresAreNot() throws Exception {
      Map<String, Measure> m = load(row("Unit Sales", 1, "") + row("Profit", 127, "") +
                                    row("Customer Count", 0, ""));

      assertEquals(3, m.size());
      assertTrue(m.get("Profit").isCalcMeasure());
      assertFalse(m.get("Unit Sales").isCalcMeasure());
      assertFalse(m.get("Customer Count").isCalcMeasure());
   }

   @Test
   void anExpressionColumnStillMarksACalculatedMeasure() throws Exception {
      Map<String, Measure> m = load(row("Margin", 1, "<EXPRESSION>[a]-[b]</EXPRESSION>"));

      assertTrue(m.get("Margin").isCalcMeasure());
   }
}
