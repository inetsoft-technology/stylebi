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
package inetsoft.web.composer.vs.controller;

import inetsoft.test.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.vs.PresenterValueHint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The presenter dropdown's tooltip says what a presenter does with a value it cannot draw.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ComposerPresenterControllerHintTest {
   @Test
   void theNumberPresentersCarryTheHintAndTheOthersCarryNone() throws Exception {
      List<TreeNodeModel> leaves = new ArrayList<>();
      collect(new ComposerPresenterController().getPresenterTree(), leaves);

      assertFalse(leaves.isEmpty());

      for(TreeNodeModel leaf : leaves) {
         String cls = String.valueOf(((java.util.Map<?, ?>) leaf.data()).get("class"));

         if(cls.endsWith(".BarPresenter") || cls.endsWith(".TrafficLightPresenter") ||
            cls.endsWith(".IconCounterPresenter") || cls.endsWith(".Bar2Presenter") ||
            cls.endsWith(".BulletGraphPresenter"))
         {
            assertEquals(PresenterValueHint.NUMBERS, leaf.tooltip(), cls);
         }
         else if(cls.endsWith(".BooleanPresenter")) {
            assertEquals(PresenterValueHint.NUMBERS_AND_BOOLEANS, leaf.tooltip(), cls);
         }
         else if(cls.endsWith(".ImagePresenter") || cls.endsWith(".HTMLPresenter") ||
                 cls.endsWith(".ButtonPresenter"))
         {
            assertNull(leaf.tooltip(), cls);
         }
      }
   }

   private static void collect(TreeNodeModel node, List<TreeNodeModel> leaves) {
      if(node.leaf() && node.data() instanceof java.util.Map) {
         leaves.add(node);
      }

      for(TreeNodeModel child : node.children()) {
         collect(child, leaves);
      }
   }
}
