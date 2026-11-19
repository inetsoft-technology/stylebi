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
package inetsoft.report.internal;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.LegendDescriptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The merged-legend proxy passes the author's visibility edits to every child legend.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AllLegendDescriptorTest {
   @Test
   void anAuthorsVisibilityReachesEveryChild() {
      LegendDescriptor a = new LegendDescriptor();
      LegendDescriptor b = new LegendDescriptor();
      AllLegendDescriptor all = new AllLegendDescriptor(List.of(a, b));

      all.setAuthorVisible(false);
      all.setAuthorMaxModeVisible(false);

      for(LegendDescriptor child : List.of(a, b)) {
         assertFalse(child.isVisible());
         assertFalse(child.isMaxModeVisible());
         assertTrue(child.isUserVisible());
      }

      assertTrue(all.isUserVisible());
   }

   @Test
   void theFlagCanBeClearedThroughTheProxy() {
      LegendDescriptor a = new LegendDescriptor();
      LegendDescriptor b = new LegendDescriptor();
      a.setAuthorVisible(true);
      b.setAuthorVisible(true);

      new AllLegendDescriptor(List.of(a, b)).setUserVisible(false);

      assertFalse(a.isUserVisible());
      assertFalse(b.isUserVisible());
   }
}
