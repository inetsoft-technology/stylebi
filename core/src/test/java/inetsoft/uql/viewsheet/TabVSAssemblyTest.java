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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TabVSAssemblyTest {

   @Test
   void writeAndParseStateRoundTripBottomTabsTrue() throws Exception {
      TabVSAssembly assembly = new TabVSAssembly();
      assembly.getTabInfo().setBottomTabs(true);

      String xml = writeState(assembly);

      assertTrue(xml.contains("<state_bottomTabs>true</state_bottomTabs>"),
         "state_bottomTabs=true element should be written");

      TabVSAssembly restored = new TabVSAssembly();
      restored.parseState(parseXml(xml));

      assertTrue(restored.getTabInfo().isBottomTabs(), "isBottomTabs should be true after round-trip");
   }

   @Test
   void writeAndParseStateRoundTripBottomTabsExplicitFalse() throws Exception {
      // Script explicitly sets bottomTabs to false. The element must be written and
      // restored, so an explicit script-set false survives bookmark navigation (the
      // pre-fix code only wrote when true and could not distinguish explicit false
      // from an absent/old bookmark element).
      TabVSAssembly assembly = new TabVSAssembly();
      assembly.getTabInfo().setBottomTabs(false);

      String xml = writeState(assembly);

      assertTrue(xml.contains("<state_bottomTabs>false</state_bottomTabs>"),
         "state_bottomTabs=false element should be written for explicit false");

      TabVSAssembly restored = new TabVSAssembly();
      restored.parseState(parseXml(xml));

      assertFalse(restored.getTabInfo().isBottomTabs(), "isBottomTabs should be false after round-trip");
   }

   @Test
   void writeStateAlwaysIncludesBottomTabsForDefault() throws Exception {
      // Default constructor leaves bottomTabs at design-time false. writeStateContent
      // always emits the element so parseStateContent can distinguish "explicit false"
      // from "absent element" (old bookmark). Per DynamicValue.getRValue auto-promotion,
      // there is no meaningful "no rValue" state by the time the state is written.
      TabVSAssembly assembly = new TabVSAssembly();

      String xml = writeState(assembly);

      assertTrue(xml.contains("<state_bottomTabs>false</state_bottomTabs>"),
         "state_bottomTabs=false element should be present in default state");
   }

   @Test
   void parseStateBackwardCompatibilityWithoutBottomTabsElement() throws Exception {
      // Simulate old bookmark XML created before this fix — no <state_bottomTabs> element.
      // parseStateContent must leave the current rValue alone so any onInit-set value is
      // preserved across loads of legacy bookmarks.
      String legacyXml = "<assembly class=\"inetsoft.uql.viewsheet.TabVSAssembly\">" +
         "<name><![CDATA[Tab1]]></name>" +
         "</assembly>";

      TabVSAssembly assembly = new TabVSAssembly();
      assembly.getTabInfo().setBottomTabs(true); // simulate onInit script having run first
      assembly.parseState(parseXml(legacyXml));

      assertTrue(assembly.getTabInfo().isBottomTabs(),
         "isBottomTabs should remain true (onInit-set) when element is absent in legacy bookmark");
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
