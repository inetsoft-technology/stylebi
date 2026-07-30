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
package inetsoft.uql.viewsheet.graph;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests covering the dual-default invariant for nodeCornerRadius:
 * new PlotDescriptors default to 0.3, but parseXML must override to 0.0
 * when the attribute is missing so saved (legacy) tree charts stay sharp.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PlotDescriptorXmlTest {
   @Test
   void nodeCornerRadius_roundTripPreservesValue() throws Exception {
      PlotDescriptor written = new PlotDescriptor();
      written.setNodeCornerRadius(0.4);

      PlotDescriptor parsed = roundTrip(written);

      assertEquals(0.4, parsed.getNodeCornerRadius(), 1e-9);
   }

   @Test
   void nodeCornerRadius_legacyXmlWithoutAttributeDefaultsToZero() throws Exception {
      Document doc = Tool.parseXML(new StringReader("<plotDescriptor/>"));
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());

      assertEquals(0.0, parsed.getNodeCornerRadius(), 1e-9,
                   "Legacy charts (XML missing nodeCornerRadius) must stay sharp");
   }

   @Test
   void nodeCornerRadius_newInstanceDefaultsToRounded() {
      assertEquals(0.3, new PlotDescriptor().getNodeCornerRadius(), 1e-9,
                   "New tree charts default to rounded nodes");
   }

   @Test
   void setNodeCornerRadius_clampsBelowZero() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setNodeCornerRadius(-1.0);
      assertEquals(0.0, pd.getNodeCornerRadius(), 1e-9);
   }

   @Test
   void setNodeCornerRadius_clampsAboveHalf() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setNodeCornerRadius(2.0);
      assertEquals(0.5, pd.getNodeCornerRadius(), 1e-9);
   }

   @Test
   void treeLayout_roundTripsAllValues() throws Exception {
      String[] layouts = {
         PlotDescriptor.TREE_LAYOUT_TOP_BOTTOM,
         PlotDescriptor.TREE_LAYOUT_BOTTOM_TOP,
         PlotDescriptor.TREE_LAYOUT_LEFT_RIGHT,
         PlotDescriptor.TREE_LAYOUT_RIGHT_LEFT
      };

      for(String layout : layouts) {
         PlotDescriptor written = new PlotDescriptor();
         written.setTreeLayout(layout);
         assertEquals(layout, roundTrip(written).getTreeLayout());
      }
   }

   @Test
   void treeLayout_legacyXmlWithoutAttributeDefaultsToTopBottom() throws Exception {
      Document doc = Tool.parseXML(new StringReader("<plotDescriptor/>"));
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());

      assertEquals(PlotDescriptor.TREE_LAYOUT_TOP_BOTTOM, parsed.getTreeLayout());
   }

   @Test
   void setTreeLayout_unknownValueFallsBackToTopBottom() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setTreeLayout("BOGUS");
      assertEquals(PlotDescriptor.TREE_LAYOUT_TOP_BOTTOM, pd.getTreeLayout());
   }

   @Test
   void barCornerRadius_newInstanceStillDefaultsToZero() {
      // the 0.3 value arrives only via the gated seed, never from the field initializer
      assertEquals(0.0, new PlotDescriptor().getBarCornerRadiusValue(), 1e-9);
      assertFalse(new PlotDescriptor().isModernCornerSeed());
   }

   @Test
   void modernCornerSeed_roundTripsTrue() throws Exception {
      PlotDescriptor written = new PlotDescriptor();
      written.setBarCornerRadius(0.3);
      written.setModernCornerSeed(true);

      PlotDescriptor parsed = roundTrip(written);

      assertTrue(parsed.isModernCornerSeed());
      assertEquals(0.3, parsed.getBarCornerRadiusValue(), 1e-9);
   }

   @Test
   void modernCornerSeed_legacyXmlWithoutAttributeIsFalse() throws Exception {
      Document doc = Tool.parseXML(new StringReader("<plotDescriptor/>"));
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());

      assertFalse(parsed.isModernCornerSeed(),
                  "charts saved before this phase must not look gate-seeded");
      assertEquals(0.0, parsed.getBarCornerRadiusValue(), 1e-9);
   }

   @Test
   void modernCornerSeed_participatesInEqualsContent() {
      PlotDescriptor a = new PlotDescriptor();
      a.setBarCornerRadius(0.3);
      a.setModernCornerSeed(true);

      PlotDescriptor b = new PlotDescriptor();
      b.setBarCornerRadius(0.3);
      b.setModernCornerSeed(false);

      assertFalse(a.equalsContent(b), "a seeded descriptor differs from a user-authored one");
   }

   @Test
   void barCornerRadius_seededValueStrippedGateOff() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setBarCornerRadius(0.3);
      pd.setModernCornerSeed(true);

      withGate("true", () -> assertEquals(0.3, pd.getBarCornerRadius(), 1e-9));
      withGate("false", () -> assertEquals(0.0, pd.getBarCornerRadius(), 1e-9,
                                          "a gate-seeded radius reverts to square"));
      // the raw value is never gate-dependent
      withGate("false", () -> assertEquals(0.3, pd.getBarCornerRadiusValue(), 1e-9));
   }

   @Test
   void barCornerRadius_userValueSurvivesGateOff() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setBarCornerRadius(0.25);
      pd.setModernCornerSeed(false);

      withGate("false", () -> assertEquals(0.25, pd.getBarCornerRadius(), 1e-9,
                                          "a user-set radius is not gate-stripped"));
   }

   @Test
   void setBarCornerRadius_clearsModernCornerSeed() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setBarCornerRadius(0.3);
      pd.setModernCornerSeed(true);

      pd.setBarCornerRadius(0.4);

      assertFalse(pd.isModernCornerSeed(), "an explicit write makes the radius user-authored");
      assertEquals(0.4, pd.getBarCornerRadiusValue(), 1e-9);
   }

   @Test
   void smoothLines_newInstanceStillDefaultsToFalse() {
      assertFalse(new PlotDescriptor().isSmoothLinesValue());
      assertFalse(new PlotDescriptor().isModernSmoothSeed());
   }

   @Test
   void modernSmoothSeed_roundTripsTrue() throws Exception {
      PlotDescriptor written = new PlotDescriptor();
      written.setSmoothLines(true);
      written.setModernSmoothSeed(true);

      PlotDescriptor parsed = roundTrip(written);

      assertTrue(parsed.isModernSmoothSeed());
      assertTrue(parsed.isSmoothLinesValue());
   }

   @Test
   void modernSmoothSeed_legacyXmlWithoutAttributeIsFalse() throws Exception {
      Document doc = Tool.parseXML(new StringReader("<plotDescriptor/>"));
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());

      assertFalse(parsed.isModernSmoothSeed(),
                  "charts saved before this phase must not look gate-seeded");
      assertFalse(parsed.isSmoothLinesValue());
   }

   @Test
   void smoothLines_seededValueStrippedGateOff() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setSmoothLines(true);
      pd.setModernSmoothSeed(true);

      withGate("true", () -> assertTrue(pd.isSmoothLines()));
      withGate("false", () -> assertFalse(pd.isSmoothLines(),
                                         "a gate-seeded smooth reverts to straight"));
      // the raw value is never gate-dependent
      withGate("false", () -> assertTrue(pd.isSmoothLinesValue()));
   }

   @Test
   void smoothLines_userValueSurvivesGateOff() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setSmoothLines(true);   // clears the marker — this is a user-authored value

      withGate("false", () -> assertTrue(pd.isSmoothLines(),
                                        "a user-set smooth is not gate-stripped"));
   }

   @Test
   void setSmoothLines_clearsModernSmoothSeed() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setSmoothLines(true);
      pd.setModernSmoothSeed(true);

      pd.setSmoothLines(false);

      assertFalse(pd.isModernSmoothSeed(), "an explicit write makes the value user-authored");
      assertFalse(pd.isSmoothLinesValue());
   }

   @Test
   void modernSmoothSeed_participatesInEqualsContent() {
      PlotDescriptor a = new PlotDescriptor();
      a.setSmoothLines(true);
      a.setModernSmoothSeed(true);

      PlotDescriptor b = new PlotDescriptor();
      b.setSmoothLines(true);

      assertFalse(a.equalsContent(b), "a seeded descriptor differs from a user-authored one");
   }

   private void withGate(String value, Runnable body) {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", value);
         body.run();
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   private static PlotDescriptor roundTrip(PlotDescriptor source) throws Exception {
      StringWriter sw = new StringWriter();
      try(PrintWriter pw = new PrintWriter(sw)) {
         source.writeXML(pw);
      }
      Document doc = Tool.parseXML(new StringReader(sw.toString()));
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());
      return parsed;
   }
}
