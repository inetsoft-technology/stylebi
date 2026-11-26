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
package inetsoft.report.composition.graph;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82426: an undo checkpoint clones the chart descriptor, and the band fill is later
 * edited in place on the live target, so a cloned target must own its band fill.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GraphTargetCloneTest {
   @Test
   void cloneOwnsBandFill() {
      GraphTarget target = new GraphTarget();
      target.getBandFill().setUserColor(0, Color.decode("#CCE5FF"));

      GraphTarget copy = (GraphTarget) target.clone();
      target.getBandFill().setUserColor(0, Color.decode("#00FF00"));

      assertNotSame(target.getBandFill(), copy.getBandFill());
      assertEquals(Color.decode("#CCE5FF"), copy.getBandFill().getUserColors().get(0));
      assertEquals(Color.decode("#00FF00"), target.getBandFill().getUserColors().get(0));
   }

   @Test
   void cloneWithNullBandFill() {
      GraphTarget target = new GraphTarget();
      target.setBandFill(null);

      GraphTarget copy = (GraphTarget) target.clone();

      assertNotNull(copy);
      assertNull(copy.getBandFill());
   }

   @Test
   void descriptorCloneKeepsCheckpointBandFill() {
      ChartDescriptor desc = new ChartDescriptor();
      GraphTarget target = new GraphTarget();
      target.getBandFill().setUserColor(0, Color.decode("#CCE5FF"));
      desc.addTarget(target);

      ChartDescriptor checkpoint = (ChartDescriptor) desc.clone();
      desc.getTarget(0).getBandFill().setUserColor(0, Color.decode("#00FF00"));

      assertEquals(Color.decode("#CCE5FF"),
                   checkpoint.getTarget(0).getBandFill().getUserColors().get(0));
   }
}
