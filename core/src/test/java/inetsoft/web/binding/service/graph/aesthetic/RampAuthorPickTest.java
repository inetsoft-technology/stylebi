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
package inetsoft.web.binding.service.graph.aesthetic;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.web.binding.model.graph.aesthetic.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A ramp the author picks is flagged on its wrapper, an unchanged echo of the pane is not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RampAuthorPickTest {
   @Test
   void aPickedRampIsStamped() {
      TealColorFrameWrapper teal = new TealColorFrameWrapper();
      VarianceColorModel model = new VarianceColorModel(new VarianceColorFrameWrapper());
      model.setChanged(true);

      VisualFrameWrapper updated = newService().updateVisualFrameWrapper(teal, model);

      assertInstanceOf(VarianceColorFrameWrapper.class, updated);
      assertTrue(((LinearColorFrameWrapper) updated).isAuthorRamp());
   }

   @Test
   void aRePickedTealIsStamped() {
      TealColorFrameWrapper teal = new TealColorFrameWrapper();
      TealColorModel model = new TealColorModel(new TealColorFrameWrapper());
      model.setChanged(true);

      TealColorFrameWrapper updated = newService().updateVisualFrameWrapper(teal, model);

      assertTrue(updated.isAuthorRamp());
   }

   @Test
   void anOrdinaryApplyDoesNotStamp() {
      TealColorFrameWrapper teal = new TealColorFrameWrapper();
      TealColorModel model = new TealColorModel(teal);

      TealColorFrameWrapper updated = newService().updateVisualFrameWrapper(teal, model);

      assertFalse(updated.isAuthorRamp());
   }

   @Test
   void anUnchangedVarianceEchoKeepsTheSeededTeal() {
      TealColorFrameWrapper teal = new TealColorFrameWrapper();
      VarianceColorModel model = new VarianceColorModel(new VarianceColorFrameWrapper());
      assertFalse(model.isChanged(), "premise");

      VisualFrameWrapper updated = newService().updateVisualFrameWrapper(teal, model);

      assertSame(teal, updated);
      assertFalse(teal.isAuthorRamp());
   }

   @Test
   void anUnchangedVarianceOnAnAuthorsTealReplacesIt() {
      TealColorFrameWrapper teal = new TealColorFrameWrapper();
      teal.setAuthorRamp(true);
      VarianceColorModel model = new VarianceColorModel(new VarianceColorFrameWrapper());

      VisualFrameWrapper updated = newService().updateVisualFrameWrapper(teal, model);

      assertInstanceOf(VarianceColorFrameWrapper.class, updated);
   }

   @Test
   void aFlaggedRampSendsChanged() {
      VisualFrameModelFactoryService service = newService();
      TealColorFrameWrapper teal = new TealColorFrameWrapper();
      assertFalse(service.<TealColorFrameWrapper, TealColorModel>createVisualFrameModel(teal)
                     .isChanged());

      teal.setAuthorRamp(true);

      assertTrue(service.<TealColorFrameWrapper, TealColorModel>createVisualFrameModel(teal)
                    .isChanged());
   }

   private VisualFrameModelFactoryService newService() {
      List<VisualFrameModelFactory<?, ?>> factories = new ArrayList<>();
      factories.add(new ColorFrameModelFactory.TealColorFactory());
      factories.add(new ColorFrameModelFactory.VarianceColorFactory());
      factories.add(new ColorFrameModelFactory.BluesColorFactory());
      return new VisualFrameModelFactoryService(factories);
   }
}
