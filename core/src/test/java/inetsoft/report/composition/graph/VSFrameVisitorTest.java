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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameContext;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #76111: a script that mutates an already-live CategoricalColorFrame in place
 * (e.g. {@code chart.colorFrame.setColor("NJ", RED)}) never goes through
 * {@code AbstractChartBindingScriptable.setColorFrame()}'s useGlobal(false) fix (bug #45391) --
 * that only fires on {@code chart.colorFrame = newFrame} reassignment, not get-then-mutate. So
 * useGlobal stays true, and on the next redraw {@link VSFrameVisitor#applyGlobalColors} must not
 * clobber the scripted color with the viewsheet's shared/global dimension color.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSFrameVisitorTest {
   @Test
   void scriptedColorSurvivesGlobalColorSync() {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "State" }, { "NJ" }, { "NY" }
      });

      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setField("State");
      frame.init(data);

      JavaScriptEngine.pushExecScriptable(new ScriptScope() {
         @Override
         public Object getMember(String name) {
            return null;
         }

         @Override
         public boolean hasMember(String name) {
            return false;
         }

         @Override
         public void putMember(String name, Object value) {
         }

         @Override
         public Object[] getMemberKeys() {
            return new Object[0];
         }
      });

      try {
         frame.setColor("NJ", Color.RED);
      }
      finally {
         JavaScriptEngine.popExecScriptable();
      }

      assertTrue(frame.isScripted("NJ"), "setColor on the script thread should mark NJ scripted");

      CategoricalColorFrameContext.getContext().setDimensionColors(Map.of("NJ", Color.BLUE));

      try {
         VSFrameVisitor.syncCategoricalFrame(frame, data);
      }
      finally {
         CategoricalColorFrameContext.getContext().setDimensionColors(Collections.emptyMap());
      }

      assertEquals(Color.RED, frame.getColor("NJ"),
                   "script-assigned color must survive the global-color sync pass");
   }
}
