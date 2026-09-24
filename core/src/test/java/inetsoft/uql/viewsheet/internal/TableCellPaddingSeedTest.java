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
import inetsoft.uql.CompositeValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Insets;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked table seeds a density-derived cell padding; an unmarked one has none, which is what
 * Revert has to restore. Authorship rides on CompositeValue's USER tier rather than a boolean,
 * so an author value survives a reseed and clearing it hands the value back to the density.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCellPaddingSeedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void markedTableSeedsTheDensityCellPadding() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding());
   }

   @Test
   void eachTierSeedsItsOwnValue() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TableVSAssemblyInfo compact = new TableVSAssemblyInfo();
      compact.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(4, 6, 4, 6), compact.getCellPadding());

      SreeEnv.setProperty("viewsheet.density", "dense");
      TableVSAssemblyInfo dense = new TableVSAssemblyInfo();
      dense.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(3, 4, 3, 4), dense.getCellPadding());
   }

   @Test
   void crosstabAndCalcTableSeedTheSameValue() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CrosstabVSAssemblyInfo crosstab = new CrosstabVSAssemblyInfo();
      CalcTableVSAssemblyInfo calc = new CalcTableVSAssemblyInfo();

      crosstab.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      calc.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(4, 6, 4, 6), crosstab.getCellPadding());
      assertEquals(new Insets(4, 6, 4, 6), calc.getCellPadding());
   }

   @Test
   void unmarkedTableHasNoCellPadding() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(info.getCellPadding());
   }

   @Test
   void revertClearsASeededCellPadding() {
      // Review Focus 5: Revert calls the seed with an unmarked context and needs the legacy
      // absence written, not left alone - otherwise a reverted table keeps modern spacing
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertNotNull(info.getCellPadding(), "seeded before revert");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(info.getCellPadding(), "cleared by revert");
   }

   @Test
   void authorValueSurvivesAReseed() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(new Insets(1, 2, 1, 2), CompositeValue.Type.USER);

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertTrue(info.isUserCellPadding());
      assertEquals(new Insets(1, 2, 1, 2), info.getCellPadding());
   }

   @Test
   void clearingTheAuthorValueHandsItBackToDensity() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      info.setCellPadding(new Insets(1, 2, 1, 2), CompositeValue.Type.USER);

      info.resetUserCellPadding();

      assertFalse(info.isUserCellPadding());
      assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding());
   }

   @Test
   void seededOnlyCellPaddingSurvivesARoundTrip() throws Exception {
      // Review Focus 5's defect: a 2-arg CompositeValue writes nothing for a DEFAULT-only tier,
      // so a density-seeded, never-overridden table lost its padding on save/reload
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      TableVSAssemblyInfo restored = roundTrip(info, new TableVSAssemblyInfo());

      assertEquals(new Insets(6, 8, 6, 8), restored.getCellPadding());
      assertFalse(restored.isUserCellPadding());
   }

   @Test
   void userAndDefaultCellPaddingBothSurviveARoundTrip() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      info.setCellPadding(new Insets(1, 2, 1, 2), CompositeValue.Type.USER);

      TableVSAssemblyInfo restored = roundTrip(info, new TableVSAssemblyInfo());

      assertEquals(new Insets(1, 2, 1, 2), restored.getCellPadding());
      assertTrue(restored.isUserCellPadding());
   }

   @Test
   void anAssetSavedBeforeTheFieldExistedParsesWithNoCellPadding() throws Exception {
      // the attribute is simply absent; that must read as "nothing defined", not as a crash
      TableVSAssemblyInfo read = new TableVSAssemblyInfo();
      Element elem = parseElement("<assembly class=\"TableVSAssemblyInfo\"/>");
      read.parseAttributes(elem);

      assertNull(read.getCellPadding());
      assertFalse(read.isUserCellPadding());
   }

   // round-trips through writeAttributes/parseAttributes, the path a saved or exported asset
   // actually takes - the ten other tests here never leave memory
   private static TableVSAssemblyInfo roundTrip(TableVSAssemblyInfo source,
                                                 TableVSAssemblyInfo target) throws Exception
   {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      source.writeAttributes(pw);
      pw.flush();

      Element elem = parseElement("<assembly" + sw + "/>");

      target.parseAttributes(elem);
      return target;
   }

   private static Element parseElement(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return factory.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes()))
         .getDocumentElement();
   }
}
