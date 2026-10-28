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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionDensitySizeTest {
   @Test
   void aFreshListTakesTheTierSize() {
      assertEquals(new Dimension(132, 202), sizeAfterSeed("comfortable", new Dimension(100, 120)));
      assertEquals(new Dimension(124, 170), sizeAfterSeed("compact", new Dimension(100, 120)));
      assertEquals(new Dimension(116, 136), sizeAfterSeed("dense", new Dimension(100, 120)));
   }

   @Test
   void anAuthorSizeIsLeftAlone() {
      assertEquals(new Dimension(300, 400), sizeAfterSeed("comfortable", new Dimension(300, 400)));
   }

   @Test
   void aDensityChangeMovesASeededSizeToTheNewTier() {
      assertEquals(new Dimension(124, 170), sizeAfterSeed("compact", new Dimension(132, 202)),
                   "a size seeded at comfortable follows to compact rather than stranding");
   }

   // Revert seeds under a transition context, which is what lets it shrink a tier-sized box
   @Test
   void revertRestoresTheLegacySize() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setPixelSize(new Dimension(132, 202));
      info.setVizMark(null);

      info.seedChromeDefaults(VizContext.ofTransition(null, null));

      assertEquals(new Dimension(100, 120), info.getPixelSize());
   }

   @Test
   void revertLeavesAnAuthorSizeAlone() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setPixelSize(new Dimension(300, 400));
      info.setVizMark(null);

      info.seedChromeDefaults(VizContext.ofTransition(null, null));

      assertEquals(new Dimension(300, 400), info.getPixelSize());
   }

   // every open re-runs the seed; an unmarked list that happens to be tier-sized must keep its box
   @Test
   void openingAnUnmarkedListLeavesATierSizedBoxAlone() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setPixelSize(new Dimension(132, 202));
      info.setVizMark(null);

      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(new Dimension(132, 202), info.getPixelSize());
   }

   @Test
   void parsingAnAssetDoesNotResizeIt() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssemblyInfo saved = new SelectionListVSAssemblyInfo();
      saved.setVizMark(VizMark.MODERN_LIGHT);
      saved.setPixelSize(new Dimension(140, 150));

      SelectionListVSAssemblyInfo loaded = new SelectionListVSAssemblyInfo();
      loaded.parseXML(toElement(saved));

      assertEquals(VizMark.MODERN_LIGHT, loaded.getVizMark(), "the mark must survive the round trip");
      assertEquals(new Dimension(140, 150), loaded.getPixelSize(),
                   "a marked list stored at a non-default size keeps it until something seeds it");
   }

   @Test
   void aModernizedContainerTakesNoInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo unmarked = new CurrentSelectionVSAssemblyInfo();
      CurrentSelectionVSAssemblyInfo marked = new CurrentSelectionVSAssemblyInfo();
      marked.setVizMark(VizMark.MODERN_LIGHT);

      marked.seedChromeDefaults(VizContext.of(marked));

      // a frame around children that inset themselves; its size follows the density instead
      assertEquals(unmarked.getPadding(), marked.getPadding());
   }

   private Dimension sizeAfterSeed(String density, Dimension start) {
      SreeEnv.setProperty("viewsheet.density", density);
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setPixelSize(start);
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
      return info.getPixelSize();
   }

   // serialises the info and parses it back, the way an asset load reaches it
   private Element toElement(SelectionListVSAssemblyInfo info) throws Exception {
      StringWriter sw = new StringWriter();
      PrintWriter writer = new PrintWriter(sw);
      info.writeXML(writer);
      writer.flush();
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document doc = dbf.newDocumentBuilder()
         .parse(new ByteArrayInputStream(sw.toString().getBytes(StandardCharsets.UTF_8)));
      return doc.getDocumentElement();
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // the rows are the dropdown's own six, as legacy's; the box it opens to is the tier box
   @ParameterizedTest
   @CsvSource({ "comfortable, 202", "compact, 170", "dense, 136" })
   void aFreshMarkedListKeepsSixRows(String density, int height) {
      SelectionListVSAssemblyInfo info = created(density, VizMark.MODERN_LIGHT);

      assertEquals(6, info.getListHeight());
      assertEquals(height, info.getContainedListHeight());
   }

   @Test
   void aFreshMarkedTreeKeepsSixRows() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();

      assertEquals(6, info.getListHeight());
   }

   @Test
   void aFreshUnmarkedListKeepsSixRows() {
      SelectionListVSAssemblyInfo info = created("comfortable", null);

      assertEquals(new Dimension(100, 120), info.getPixelSize());
      assertEquals(6, info.getListHeight(), "legacy creation is unchanged");
   }

   // a density change moves the box, not the rows
   @Test
   void aDensityChangeLeavesTheRowsAlone() {
      SelectionListVSAssemblyInfo info = created("comfortable", VizMark.MODERN_LIGHT);

      SreeEnv.setProperty("viewsheet.density", "dense");
      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Dimension(116, 136), info.getPixelSize());
      assertEquals(6, info.getListHeight());
      assertEquals(136, info.getContainedListHeight());
   }

   // a density change moves pixels, not rows: an author's box keeps its size and its row count.
   // It takes no top or bottom inset (#79565), so the contained height is 20 + 6 x 20
   @Test
   void aDensityChangeLeavesAnAuthorSizedListsRows() {
      SelectionListVSAssemblyInfo info = created("comfortable", VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(132, 230));
      info.setUserSize(true);
      info.setListHeight(6);

      SreeEnv.setProperty("viewsheet.density", "dense");
      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Dimension(132, 230), info.getPixelSize());
      assertEquals(6, info.getListHeight());
      assertEquals(140, info.getContainedListHeight());
   }

   // a dropdown's rows are the author's, even when its box still carries a seeded size
   @Test
   void theSeedLeavesADropdownsRowsAlone() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setListHeight(10);

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(10, info.getListHeight());
   }

   @Test
   void modernizeLeavesALegacyListsRowsAlone() {
      SelectionListVSAssemblyInfo info = created("comfortable", null);

      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.ofTransition(null, VizMark.MODERN_LIGHT));

      assertEquals(new Dimension(132, 202), info.getPixelSize());
      assertEquals(6, info.getListHeight());
   }

   // the box goes back to 100x120 and the rows stay; unmarked, it opens to the legacy 20 + 6 x 20
   @Test
   void revertLeavesTheRowsAlone() {
      SelectionListVSAssemblyInfo info = created("comfortable", VizMark.MODERN_LIGHT);

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.ofTransition(null, null));

      assertEquals(new Dimension(100, 120), info.getPixelSize());
      assertEquals(6, info.getListHeight());
      assertEquals(140, info.getContainedListHeight());
   }

   @Test
   void openingAnUnmarkedListLeavesItsRowsAlone() {
      SelectionListVSAssemblyInfo info = created("comfortable", null);

      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(6, info.getListHeight());
   }

   @Test
   void theSeedLeavesTheRowsAloneUnderAnAuthorCellHeight() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setCellHeight(40);
      info.setUserCellHeight(true);

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Dimension(132, 202), info.getPixelSize());
      assertEquals(6, info.getListHeight());
   }

   private SelectionListVSAssemblyInfo created(String density, VizMark mark) {
      SreeEnv.setProperty("viewsheet.density", density);
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(mark);
      info.initDefaultFormat();
      return info;
   }
}
