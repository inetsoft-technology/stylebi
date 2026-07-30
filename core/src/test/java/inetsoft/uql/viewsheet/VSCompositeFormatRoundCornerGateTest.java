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

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.css.CSSConstants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The DEFAULT tier of roundCorner is gate-owned: a seeded 12 reverts to square when the modern gate is
 * off. USER and CSS tier values are never gate-stripped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSCompositeFormatRoundCornerGateTest {
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

   private VSCompositeFormat withDefaultTierRadius(int radius) {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getDefaultFormat().setRoundCornerValue(radius);
      return fmt;
   }

   @Test
   void defaultTierSeedHonoredUnderGate() {
      withGate("true", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         assertEquals(12, fmt.getRoundCorner());
         assertEquals(12, fmt.getRoundCornerValue());
      });
   }

   @Test
   void defaultTierSeedStrippedGateOff() {
      withGate("false", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         assertEquals(0, fmt.getRoundCorner(), "seeded card reverts to square when the gate is off");
         assertEquals(0, fmt.getRoundCornerValue(), "format editor shows what is rendered");
      });
   }

   @Test
   void defaultTierNonSeedValuePreservedGateOff() {
      // a format.css TableStyle radius lands on the DEFAULT tier too; only the exact seed is gate-owned
      withGate("false", () -> assertEquals(8, withDefaultTierRadius(8).getRoundCorner()));
   }

   @Test
   void userTierRadiusSurvivesGateOff() {
      withGate("false", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         fmt.getUserDefinedFormat().setRoundCornerValue(12);
         assertEquals(12, fmt.getRoundCorner(), "a user-set radius is never gate-stripped");
         assertEquals(12, fmt.getRoundCornerValue());
      });
   }

   @Test
   void userTierRadiusWinsUnderGate() {
      withGate("true", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         fmt.getUserDefinedFormat().setRoundCornerValue(4);
         assertEquals(4, fmt.getRoundCorner(), "USER tier beats the DEFAULT-tier seed");
      });
   }

   @Test
   void bareFormatIsSquareInBothGateStates() {
      withGate("true", () -> assertEquals(0, new VSCompositeFormat().getRoundCorner()));
      withGate("false", () -> assertEquals(0, new VSCompositeFormat().getRoundCorner()));
   }

   @Test
   void resolvedRadiusCopiedToUserTierIsNotDoubleStripped() {
      // export resolves the chart's radius, then re-applies it to a synthetic rectangle's USER tier
      VSCompositeFormat synthetic = new VSCompositeFormat();

      withGate("true", () -> {
         VSCompositeFormat source = withDefaultTierRadius(12);
         assertEquals(12, source.getRoundCorner(), "gate on: the chart card resolves to 12");
         synthetic.getUserDefinedFormat().setRoundCornerValue(source.getRoundCorner());
      });

      withGate("false", () -> {
         // control: the same value on the DEFAULT tier IS stripped, so the assertion below is real
         assertEquals(0, withDefaultTierRadius(12).getRoundCorner());
         assertEquals(12, synthetic.getRoundCorner(),
                      "the USER-tier copy survives the strip");
      });
   }

   @Test
   void tabDefaultTierRadiusIsNotStripped() {
      // FormatInfo.copyDefaultFormat launders a resolved radius onto a tab's active-format default
      // tier; a user radius equal to the seed must survive there
      withGate("false", () -> {
         VSCompositeFormat tab = new VSCompositeFormat();
         tab.getCSSFormat().setCSSType(CSSConstants.TAB);
         tab.getDefaultFormat().setRoundCornerValue(12);
         assertEquals(12, tab.getRoundCorner(), "a tab default-tier radius is never gate-stripped");
         assertEquals(12, tab.getRoundCornerValue());

         // control: the same value on a non-tab format IS stripped
         assertEquals(0, withDefaultTierRadius(12).getRoundCorner());
      });
   }
}
