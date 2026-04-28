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
package inetsoft.uql.viewsheet;

import org.junit.jupiter.api.Test;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class TabVSAssemblyTest {

   @Test
   void writeAndParseStateRoundTripBottomTabsTrue() throws Exception {
      TabVSAssembly assembly = new TabVSAssembly();
      assembly.getTabInfo().setBottomTabs(true);

      String xml = writeState(assembly);

      assertTrue(xml.contains("<state_bottomTabs>"), "state_bottomTabs element should be present when bottomTabs=true");

      TabVSAssembly restored = new TabVSAssembly();
      restored.parseState(parseXml(xml));

      assertTrue(restored.getTabInfo().isBottomTabs(), "isBottomTabs should be true after round-trip");
   }

   @Test
   void writeStateOmitsElementWhenBottomTabsFalse() throws Exception {
      TabVSAssembly assembly = new TabVSAssembly();

      String xml = writeState(assembly);

      assertFalse(xml.contains("<state_bottomTabs>"), "state_bottomTabs element should be absent for default bottomTabs=false");
   }

   @Test
   void parseStateBackwardCompatibilityWithoutBottomTabsElement() throws Exception {
      // Simulate old bookmark XML that has no <state_bottomTabs> element
      String legacyXml = "<assembly class=\"inetsoft.uql.viewsheet.TabVSAssembly\">" +
         "<name><![CDATA[Tab1]]></name>" +
         "</assembly>";

      TabVSAssembly assembly = new TabVSAssembly();
      assembly.parseState(parseXml(legacyXml));

      assertFalse(assembly.getTabInfo().isBottomTabs(), "isBottomTabs should remain false when element is absent");
   }

   private static String writeState(TabVSAssembly assembly) {
      StringWriter sw = new StringWriter();
      assembly.writeState(new PrintWriter(sw), false);
      return sw.toString();
   }

   private static Element parseXml(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      Document doc = factory.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes()));
      return doc.getDocumentElement();
   }
}
