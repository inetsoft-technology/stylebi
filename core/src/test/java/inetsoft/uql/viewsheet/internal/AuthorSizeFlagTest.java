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

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Dimension;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AuthorSizeFlagTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void anUnsetFlagWritesNoAttributeAndParsesAsFalse() throws Exception {
      CurrentSelectionVSAssemblyInfo saved = new CurrentSelectionVSAssemblyInfo();

      assertFalse(xml(saved).contains("userSize"), "a box nobody resized keeps its saved form");

      CurrentSelectionVSAssemblyInfo loaded = new CurrentSelectionVSAssemblyInfo();
      loaded.parseXML(element(saved));
      assertFalse(loaded.isUserSize());
   }

   @Test
   void aSetFlagRoundTrips() throws Exception {
      SelectionListVSAssemblyInfo saved = new SelectionListVSAssemblyInfo();
      saved.setUserSize(true);

      assertTrue(xml(saved).contains("userSize=\"true\""));

      SelectionListVSAssemblyInfo loaded = new SelectionListVSAssemblyInfo();
      loaded.parseXML(element(saved));
      assertTrue(loaded.isUserSize());
   }

   @Test
   void copyCarriesTheFlag() {
      CurrentSelectionVSAssemblyInfo from = new CurrentSelectionVSAssemblyInfo();
      from.setUserSize(true);
      CurrentSelectionVSAssemblyInfo to = new CurrentSelectionVSAssemblyInfo();

      assertTrue(to.copyViewInfo(from, false), "a changed flag reports a change");
      assertTrue(to.isUserSize());
   }

   // open, density change, Modernize and Revert all run this hook
   @Test
   void anAuthorSizeSurvivesEveryRerun() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo container = authored(new CurrentSelectionVSAssemblyInfo(),
                                                          new Dimension(300, 240));
      SelectionListVSAssemblyInfo list = authored(new SelectionListVSAssemblyInfo(),
                                                  new Dimension(100, 120));
      SelectionTreeVSAssemblyInfo tree = authored(new SelectionTreeVSAssemblyInfo(),
                                                  new Dimension(100, 120));

      for(VSAssemblyInfo info : new VSAssemblyInfo[] { container, list, tree }) {
         VizModernizeUtil.reseedAfterRestore(info);
         info.seedChromeDefaults(VizContext.of(info));
         SreeEnv.setProperty("viewsheet.density", "compact");
         info.seedChromeDefaults(VizContext.of(info));
         info.setVizMark(null);
         info.seedChromeDefaults(VizContext.ofTransition(null, null));
         SreeEnv.setProperty("viewsheet.density", "comfortable");
      }

      assertEquals(new Dimension(300, 240), container.getPixelSize());
      assertEquals(new Dimension(100, 120), list.getPixelSize());
      assertEquals(new Dimension(100, 120), tree.getPixelSize());
   }

   @Test
   void anUnflaggedSeededSizeStillFollowsTheRule() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo container = new CurrentSelectionVSAssemblyInfo();
      container.setVizMark(VizMark.MODERN_LIGHT);
      container.setPixelSize(new Dimension(300, 240));

      container.seedChromeDefaults(VizContext.of(container));

      assertEquals(new Dimension(300, 360), container.getPixelSize());
   }

   @Test
   void resetSizeClearsTheFlagAndWritesTheTierSize() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo container = authored(new CurrentSelectionVSAssemblyInfo(),
                                                          new Dimension(300, 500));
      SelectionListVSAssemblyInfo list = authored(new SelectionListVSAssemblyInfo(),
                                                  new Dimension(300, 400));
      SelectionTreeVSAssemblyInfo tree = authored(new SelectionTreeVSAssemblyInfo(),
                                                  new Dimension(300, 400));

      container.resetSize(VizContext.of(container));
      list.resetSize(VizContext.of(list));
      tree.resetSize(VizContext.of(tree));

      assertEquals(new Dimension(300, 360), container.getPixelSize());
      assertEquals(new Dimension(132, 202), list.getPixelSize());
      assertEquals(new Dimension(132, 202), tree.getPixelSize());
      assertFalse(container.isUserSize() || list.isUserSize() || tree.isUserSize());
   }

   @Test
   void resetSizeDoesNothingWithoutADensitySize() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo chart = new ChartVSAssemblyInfo();
      chart.setVizMark(VizMark.MODERN_LIGHT);
      chart.setPixelSize(new Dimension(400, 300));
      chart.setUserSize(true);

      chart.resetSize(VizContext.of(chart));

      assertFalse(chart.takesDensitySize());
      assertEquals(new Dimension(400, 300), chart.getPixelSize());
      assertTrue(chart.isUserSize(), "a type without a density size keeps its flag untouched");
   }

   private static <T extends VSAssemblyInfo> T authored(T info, Dimension size) {
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(size);
      info.setUserSize(true);
      return info;
   }

   private static String xml(VSAssemblyInfo info) {
      StringWriter sw = new StringWriter();
      PrintWriter writer = new PrintWriter(sw);
      info.writeXML(writer);
      writer.flush();
      return sw.toString();
   }

   private static Element element(VSAssemblyInfo info) throws Exception {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document doc = dbf.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml(info).getBytes(StandardCharsets.UTF_8)));
      return doc.getDocumentElement();
   }
}
