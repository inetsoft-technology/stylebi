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
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.web.binding.model.graph.aesthetic.StaticColorModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A static colour model that echoes the colour the frame shows without an author colour is a
 * reset, and clears the user tier; any other colour is still the author's.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class StaticColorFrameFactoryTest {
   private static final Color DEFAULT = new Color(0x518DB9);

   @Test
   void aResetClearsTheAuthorColour() {
      StaticColorFrameWrapper wrapper = wrapper();
      StaticColorModel model = model(wrapper, "#518DB9", false);

      StaticColorFrameWrapper updated = newService().updateVisualFrameWrapper(wrapper, model);

      assertNull(updated.getUserColor());
      assertEquals(DEFAULT, updated.getColor());
   }

   @Test
   void aResetHonoursTheStylesheetColour() {
      StaticColorFrameWrapper wrapper = wrapper();
      StaticColorModel model = model(wrapper, "#123456", false);
      model.setCssColor("#123456");

      StaticColorFrameWrapper updated = newService().updateVisualFrameWrapper(wrapper, model);

      assertNull(updated.getUserColor());
      assertEquals(DEFAULT, updated.getDefaultColor());
   }

   @Test
   void aPickIsStillTheAuthors() {
      StaticColorFrameWrapper wrapper = wrapper();
      StaticColorModel model = model(wrapper, "#00FF00", false);

      StaticColorFrameWrapper updated = newService().updateVisualFrameWrapper(wrapper, model);

      assertEquals(new Color(0x00FF00), updated.getUserColor());
   }

   @Test
   void aPickedDefaultMarkedChangedStays() {
      StaticColorFrameWrapper wrapper = wrapper();
      StaticColorModel model = model(wrapper, "#518DB9", true);

      StaticColorFrameWrapper updated = newService().updateVisualFrameWrapper(wrapper, model);

      assertEquals(DEFAULT, updated.getUserColor());
      assertTrue(updated.isChanged());
   }

   private static StaticColorFrameWrapper wrapper() {
      StaticColorFrameWrapper wrapper = new StaticColorFrameWrapper();
      wrapper.setDefaultColor(DEFAULT);
      wrapper.setUserColor(Color.RED);
      return wrapper;
   }

   private static StaticColorModel model(StaticColorFrameWrapper wrapper, String color,
                                         boolean changed)
   {
      StaticColorModel model = new StaticColorModel(wrapper);
      model.setColor(color);
      model.setDefaultColor("#518DB9");
      model.setCssColor("");
      model.setChanged(changed);
      return model;
   }

   private VisualFrameModelFactoryService newService() {
      List<VisualFrameModelFactory<?, ?>> factories = new ArrayList<>();
      factories.add(new ColorFrameModelFactory.StaticColorFrameFactory());
      return new VisualFrameModelFactoryService(factories);
   }
}
