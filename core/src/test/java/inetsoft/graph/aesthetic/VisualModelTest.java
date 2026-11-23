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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VisualModel: the six frames of a graph element and the value each aesthetic gets from them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class VisualModelTest {
   private static final Color RED = new Color(200, 10, 10);

   private static DefaultDataSet data() {
      return new DefaultDataSet(new Object[][] { { "g" }, { "A" }, { "B" } });
   }

   private static DefaultTextFrame text(String value) {
      DefaultTextFrame frame = new DefaultTextFrame("g");
      frame.setText("A", value);
      return frame;
   }

   private static VisualModel shapeModel(DataSet data, ShapeFrame frame) {
      return new VisualModel(data, null, null, frame, null, null, null);
   }

   @Test
   void constructorWiresEachFrameToItsGetter() {
      DefaultDataSet data = data();
      StaticColorFrame colors = new StaticColorFrame(RED);
      StaticSizeFrame sizes = new StaticSizeFrame(7);
      StaticShapeFrame shapes = new StaticShapeFrame(GShape.STAR);
      StaticTextureFrame textures = new StaticTextureFrame(GTexture.PATTERN_1);
      StaticLineFrame lines = new StaticLineFrame(GLine.DASH_LINE);
      DefaultTextFrame texts = text("txt");
      VisualModel model = new VisualModel(data, colors, sizes, shapes, textures, lines, texts);

      assertSame(colors, model.getColorFrame());
      assertSame(sizes, model.getSizeFrame());
      assertSame(shapes, model.getShapeFrame());
      assertSame(textures, model.getTextureFrame());
      assertSame(lines, model.getLineFrame());
      assertSame(texts, model.getTextFrame());
      assertSame(data, model.getDataSet());
   }

   @Test
   void eachAspectIsDelegatedToItsFrame() {
      VisualModel model = new VisualModel(
         data(), new StaticColorFrame(RED), new StaticSizeFrame(7),
         new StaticShapeFrame(GShape.STAR), new StaticTextureFrame(GTexture.PATTERN_1),
         new StaticLineFrame(GLine.DASH_LINE), text("txt"));

      assertEquals(RED, model.getColor("m", 0));
      assertEquals(7, model.getSize("m", 0), 1e-9);
      assertEquals(GShape.STAR, model.getShape("m", 0));
      assertEquals(GTexture.PATTERN_1, model.getTexture("m", 0));
      assertEquals(GLine.DASH_LINE, model.getLine("m", 0));
      assertEquals("txt", model.getText("m", 0));
   }

   @Test
   void variableAndRowArePassedToTheFrame() {
      DefaultDataSet data = data();
      CategoricalShapeFrame byMeasure = new CategoricalShapeFrame();
      byMeasure.setShape((Object) "m2", GShape.STAR);
      byMeasure.setShape((Object) "m1", GShape.SQUARE);
      CategoricalShapeFrame byValue = new CategoricalShapeFrame("g");
      byValue.setShape((Object) "A", GShape.CROSS);
      byValue.setShape((Object) "B", GShape.DIAMOND);

      assertEquals(GShape.STAR, shapeModel(data, byMeasure).getShape("m2", 0));
      assertEquals(GShape.SQUARE, shapeModel(data, byMeasure).getShape("m1", 1));
      assertEquals(GShape.DIAMOND, shapeModel(data, byValue).getShape("m1", 1));
      assertEquals(GShape.CROSS, shapeModel(data, byValue).getShape("m2", 0));
   }

   @Test
   void missingFramesGiveTheDefaults() {
      VisualModel model = new VisualModel();

      assertEquals(StaticColorFrame.DEFAULT_COLOR, model.getColor("m", 0));
      assertEquals(1, model.getSize("m", 0), 1e-9);
      assertNull(model.getShape("m", 0));
      assertNull(model.getTexture("m", 0));
      assertNull(model.getLine("m", 0));
      assertNull(model.getText("m", 0));
   }

   @Test
   void colorFrameAnsweringNullGivesTheDefaultColor() {
      StaticColorFrame nullColor = new StaticColorFrame(RED) {
         @Override
         public Color getColor(DataSet data, String col, int row) {
            return null;
         }
      };
      VisualModel model = new VisualModel(data(), nullColor, null, null, null, null, null);

      assertEquals(StaticColorFrame.DEFAULT_COLOR, model.getColor("m", 0));
   }

   @Test
   void settersReplaceTheFramesAndNullRestoresTheDefaults() {
      VisualModel model = new VisualModel();
      model.setColorFrame(new StaticColorFrame(RED));
      model.setSizeFrame(new StaticSizeFrame(7));

      assertEquals(RED, model.getColor("m", 0));
      assertEquals(7, model.getSize("m", 0), 1e-9);
      assertNull(model.getShapeFrame());
      assertNull(model.getShape("m", 0));

      model.setShapeFrame(new StaticShapeFrame(GShape.STAR));
      model.setTextureFrame(new StaticTextureFrame(GTexture.PATTERN_1));
      model.setLineFrame(new StaticLineFrame(GLine.DASH_LINE));
      model.setTextFrame(text("txt"));

      assertEquals(GShape.STAR, model.getShape("m", 0));
      assertEquals(GTexture.PATTERN_1, model.getTexture("m", 0));
      assertEquals(GLine.DASH_LINE, model.getLine("m", 0));
      assertEquals(RED, model.getColor("m", 0));

      model.setColorFrame(null);
      model.setSizeFrame(null);
      model.setShapeFrame(null);
      model.setTextureFrame(null);
      model.setLineFrame(null);
      model.setTextFrame(null);

      assertEquals(StaticColorFrame.DEFAULT_COLOR, model.getColor("m", 0));
      assertEquals(1, model.getSize("m", 0), 1e-9);
      assertNull(model.getShape("m", 0));
      assertNull(model.getTexture("m", 0));
      assertNull(model.getLine("m", 0));
      assertNull(model.getText("m", 0));
   }
}
