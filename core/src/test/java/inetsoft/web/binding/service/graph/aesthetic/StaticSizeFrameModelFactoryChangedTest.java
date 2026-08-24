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

import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;
import inetsoft.web.binding.model.graph.aesthetic.StaticSizeModel;
import inetsoft.web.wiz.binding.VisualFrameAliases;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finding 15 live-repro gap: {@code set_visual_frame({channel: "size", type: "static", size: N})}
 * reported {@code ok} but a read-back returned a chart-type default (15 on a bar chart) instead
 * of {@code N} -- and only on the {@code size} channel; the same static write on shape/line/
 * texture persisted normally in the same live session.
 *
 * <p>{@code StaticSizeFrameModelFactory.updateVisualFrameWrapper0} is the only static frame
 * factory that gates its write on {@code model.isChanged()}; when false it not only skips
 * applying the size but actively resets the USER-tier composite value, so a later chart-type
 * default repopulation (e.g. {@code ChangeChartProcessor.fixSizeFrameValues}) wins. {@code
 * VisualFrameAliases.staticSize()} built a {@code StaticSizeModel} but never marked it changed,
 * so every write made through the plugin's {@code set_visual_frame} path hit that gate as if
 * nothing had been asked for.
 */
@Tag("core")
class StaticSizeFrameModelFactoryChangedTest {
   @Test
   void aStaticSizeWriteBuiltThroughVisualFrameAliasesSurvivesTheFactoryUpdate() {
      // Built exactly the way the plugin's write path builds it -- through the same alias
      // vocabulary set_visual_frame uses, not a hand-rolled model.
      StaticSizeModel model = assertInstanceOf(StaticSizeModel.class,
         VisualFrameAliases.create("size", Map.of("type", "static", "size", 25)));

      StaticSizeFrameWrapper wrapper = new StaticSizeFrameWrapper();
      // Mimics a chart-type default already sitting in the DEFAULT tier -- exactly what
      // ChangeChartProcessor.fixSizeFrameValues repopulates (15 for a bar chart, 30 for a pie
      // chart) once the USER tier has been reset.
      ((StaticSizeFrame) wrapper.getVisualFrame()).setSize(15, CompositeValue.Type.DEFAULT);

      SizeFrameModelFactory.StaticSizeFrameModelFactory factory =
         new SizeFrameModelFactory.StaticSizeFrameModelFactory();

      factory.updateVisualFrameWrapper(wrapper, model);

      assertEquals(25d, wrapper.getSize(),
         "a deliberate static size write must survive the factory update instead of falling "
            + "back to the chart-type default");
   }
}
