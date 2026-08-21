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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier precedence for roundCorner: a USER-tier radius beats a DEFAULT-tier one, and a DEFAULT-tier
 * radius is honoured whatever the org gate says.
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
   void defaultTierSeedSurvivesGateOff() {
      withGate("false", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         assertEquals(12, fmt.getRoundCorner(),
                      "a seeded card keeps its radius; the gate no longer strips it");
         assertEquals(12, fmt.getRoundCornerValue());
      });
   }

   @Test
   void userTierRadiusSurvivesGateOff() {
      withGate("false", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         fmt.getUserDefinedFormat().setRoundCornerValue(12);
         assertEquals(12, fmt.getRoundCorner(), "the USER tier answers, not the DEFAULT tier");
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
}
