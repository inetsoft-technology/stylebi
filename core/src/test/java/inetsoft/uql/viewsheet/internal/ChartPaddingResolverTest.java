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
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartPaddingResolverTest {
   @Test
   void unmarkedKeepsTheStoredInset() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      Insets stored = new Insets(10, 10, 10, 10);

      assertEquals(stored, VSObjectChromeDefaults.chartPadding(info, stored),
                   "an unmarked chart must not move");
   }

   @Test
   void markedAtTheLegacyDefaultTakesTheCardInset() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(new Insets(12, 12, 12, 12),
                   VSObjectChromeDefaults.chartPadding(info, new Insets(10, 10, 10, 10)));
   }

   @Test
   void markedWithAnAuthorInsetKeepsIt() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserPadding(true);

      Insets stored = new Insets(4, 4, 4, 4);
      assertEquals(stored, VSObjectChromeDefaults.chartPadding(info, stored),
                   "the author's value wins on a marked chart");
   }

   @Test
   void markedWithAStoredInsetOffTheLegacyDefaultKeepsIt() {
      // the belt behind the flag: content saved before the flag existed carries no opinion, so a
      // stored value that is not the legacy default is treated as deliberate
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      Insets stored = new Insets(3, 3, 3, 3);
      assertEquals(stored, VSObjectChromeDefaults.chartPadding(info, stored));
   }

   @Test
   void aSubstitutionDoesNotAliasTheStoredInsets() {
      // Insets is mutable and the stored one belongs to the assembly; a caller must never be handed
      // an object that writes back into storage
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      Insets stored = new Insets(10, 10, 10, 10);

      Insets resolved = VSObjectChromeDefaults.chartPadding(info, stored);
      resolved.left = 99;

      assertEquals(10, stored.left, "the stored insets were mutated through the resolved copy");
   }

   @Test
   void nullStoredIsReturnedUnchanged() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      assertNull(VSObjectChromeDefaults.chartPadding(info, null));
   }

   @Test
   void theFlagSurvivesARoundTrip() throws Exception {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setUserPadding(true);

      java.io.StringWriter buf = new java.io.StringWriter();
      java.io.PrintWriter writer = new java.io.PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();

      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new java.io.StringReader(buf.toString()))
                           .getDocumentElement());

      assertTrue(restored.isUserPadding());
   }

   @Test
   void anAbsentFlagAttributeMeansNoOpinion() {
      // content saved before the flag existed: parse must not invent an author
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      assertFalse(info.isUserPadding());
   }

   @Test
   void theGetterResolvesForAMarkedChart() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(new Insets(12, 12, 12, 12), info.getPadding(),
                   "every read site follows through the getter");
   }

   @Test
   void theGetterIsLegacyForAnUnmarkedChart() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();

      assertEquals(new Insets(10, 10, 10, 10), info.getPadding());
   }

   @Test
   void persistenceCarriesTheStoredInsetAndNotTheResolvedOne() throws Exception {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      ChartVSAssemblyInfo restored = roundTrip(info);

      assertEquals(new Insets(12, 12, 12, 12), restored.getPadding(),
                   "the restored chart still resolves");
      assertFalse(restored.isUserPadding(),
                  "a resolved value must never be written back as the author's");
   }

   @Test
   void anAuthorInsetSurvivesARoundTripAndIsNotResolvedAway() throws Exception {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserPadding(true);
      info.setPadding(new Insets(4, 4, 4, 4));

      ChartVSAssemblyInfo restored = roundTrip(info);

      assertTrue(restored.isUserPadding());
      assertEquals(new Insets(4, 4, 4, 4), restored.getPadding(),
                   "the getter must not substitute over an author's inset");
   }

   @Test
   void contentSavedBeforeTheFlagExistedCarriesNoOpinion() throws Exception {
      // the attribute is absent, so the comparison guard decides: a legacy-default inset resolves,
      // anything else is left alone
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      String xml = write(info).replaceAll(" userPadding=\"[a-z]*\"", "");
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new java.io.StringReader(xml)).getDocumentElement());

      assertFalse(restored.isUserPadding());
      assertEquals(new Insets(12, 12, 12, 12), restored.getPadding());
   }

   private static String write(ChartVSAssemblyInfo info) {
      java.io.StringWriter buf = new java.io.StringWriter();
      java.io.PrintWriter writer = new java.io.PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static ChartVSAssemblyInfo roundTrip(ChartVSAssemblyInfo info) throws Exception {
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new java.io.StringReader(write(info))).getDocumentElement());
      restored.setVizMark(info.getVizMark());
      return restored;
   }
}
