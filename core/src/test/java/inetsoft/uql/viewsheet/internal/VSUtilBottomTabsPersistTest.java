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
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82256: a script set Tab.bottomTabs value must not be persisted into a design asset.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSUtilBottomTabsPersistTest {
   @Test
   void pollutedViewsheetIsClonedAndStripped() throws Exception {
      Viewsheet vs = new Viewsheet();
      TabVSAssembly tab = addTab(vs, "Tab1");
      info(tab).setBottomTabsValue(false);
      info(tab).setBottomTabs(true);

      Viewsheet copy = VSUtil.forDesignPersist(vs);

      assertNotSame(vs, copy);
      String xml = toXml(copy);
      assertTrue(xml.contains("<state_bottomTabs>false</state_bottomTabs>"), xml);
      assertFalse(xml.contains("<state_bottomTabs>true</state_bottomTabs>"), xml);

      Viewsheet reloaded = parse(xml);
      assertFalse(info((TabVSAssembly) reloaded.getAssembly("Tab1")).isBottomTabs());

      // the live design viewsheet is not modified
      assertTrue(info(tab).isBottomTabs());
      assertFalse(info(tab).getBottomTabsValue());
   }

   @Test
   void unaffectedTabKeepsValueAndOnlyPollutedTabIsStripped() throws Exception {
      Viewsheet vs = new Viewsheet();
      TabVSAssembly polluted = addTab(vs, "Tab1");
      info(polluted).setBottomTabsValue(false);
      info(polluted).setBottomTabs(true);
      TabVSAssembly design = addTab(vs, "Tab2");
      info(design).setBottomTabsValue(true);

      Viewsheet reloaded = parse(toXml(VSUtil.forDesignPersist(vs)));

      assertFalse(info((TabVSAssembly) reloaded.getAssembly("Tab1")).isBottomTabs());
      assertTrue(info((TabVSAssembly) reloaded.getAssembly("Tab2")).isBottomTabs());
   }

   @Test
   void cleanViewsheetIsNotCopied() {
      Viewsheet vs = new Viewsheet();
      addTab(vs, "Tab1");
      assertSame(vs, VSUtil.forDesignPersist(vs));
   }

   @Test
   void scriptValueEqualToDesignValueIsNotCopied() {
      Viewsheet vs = new Viewsheet();
      TabVSAssembly tab = addTab(vs, "Tab1");
      info(tab).setBottomTabsValue(true);
      info(tab).setBottomTabs(true);

      assertSame(vs, VSUtil.forDesignPersist(vs));
   }

   @Test
   void cloneWriteMatchesOriginalForCleanViewsheet() {
      Viewsheet vs = new Viewsheet();
      TabVSAssembly tab = addTab(vs, "Tab1");
      info(tab).setBottomTabsValue(true);

      // the action names are a set, its serialization order is not stable
      assertEquals(stable(toXml(vs)), stable(toXml(vs.clone())));
   }

   @Test
   void embeddedViewsheetTabIsStripped() {
      Viewsheet outer = new Viewsheet();
      Viewsheet inner = new Viewsheet();
      inner.getInfo().setName("Inner");
      TabVSAssembly tab = addTab(inner, "Tab1");
      info(tab).setBottomTabsValue(false);
      info(tab).setBottomTabs(true);
      outer.addAssembly(inner);

      Viewsheet copy = VSUtil.forDesignPersist(outer);

      assertNotSame(outer, copy);
      Viewsheet copyInner = (Viewsheet) copy.getAssembly("Inner");
      assertFalse(info((TabVSAssembly) copyInner.getAssembly("Tab1")).isBottomTabs());
      assertTrue(info(tab).isBottomTabs());
   }

   @Test
   void clearRuntimeBottomTabsHealsStalePersistedState() throws Exception {
      Viewsheet vs = new Viewsheet();
      TabVSAssembly tab = addTab(vs, "Tab1");
      info(tab).setBottomTabsValue(false);
      info(tab).setBottomTabs(true);
      // a pre-fix save wrote the effective value
      Viewsheet stale = parse(toXml(vs));
      TabVSAssemblyInfo info = info((TabVSAssembly) stale.getAssembly("Tab1"));
      assertTrue(info.isBottomTabs());
      assertTrue(info.isPositionNeedsSync());

      VSUtil.clearRuntimeBottomTabs(stale);

      assertFalse(info.isBottomTabs());
      assertFalse(info.isPositionNeedsSync());
   }

   private static TabVSAssemblyInfo info(TabVSAssembly tab) {
      return (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
   }

   private static TabVSAssembly addTab(Viewsheet vs, String name) {
      TabVSAssembly tab = new TabVSAssembly(vs, name);
      vs.addAssembly(tab);
      return tab;
   }

   private static String stable(String xml) {
      return xml.replaceAll("(?s)<actionNames>.*?</actionNames>", "");
   }

   private static String toXml(Viewsheet vs) {
      StringWriter sw = new StringWriter();
      PrintWriter writer = new PrintWriter(sw);
      vs.writeXML(writer);
      writer.flush();
      return sw.toString();
   }

   private static Viewsheet parse(String xml) throws Exception {
      Element root = Tool.parseXML(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
      Viewsheet vs = new Viewsheet();
      vs.parseXML(root);
      return vs;
   }
}
