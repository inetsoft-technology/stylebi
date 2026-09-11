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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the modern-visualization default that a one-line properties change is meant to flip: with
 * nothing set, the gate must resolve on. Written to fail before that change and pass after it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ModernVisualizationDefaultTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void unsetResolvesModern() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      assertTrue(VSDensityDefaults.isModern());
   }

   @Test
   void explicitFalseStillWins() {
      // an org that has opted out stays opted out no matter what the shipped default is
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertFalse(VSDensityDefaults.isModern());
   }

   @Test
   void unsetStampsAMark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      assertEquals(VizMark.MODERN_LIGHT, VizMark.fromGate());

      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertNull(VizMark.fromGate());
   }

   @Test
   void unsetDoesNotImplyDark() {
      // dark is a modifier gated on modern; modern defaulting on must not drag dark on with it
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      assertFalse(VSDensityDefaults.isDark());
   }
}
