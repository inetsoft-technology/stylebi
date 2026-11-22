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
package inetsoft.graph.element;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.schema.BoxPainter;
import inetsoft.graph.schema.CandlePainter;
import inetsoft.graph.schema.SchemaPainter;
import inetsoft.graph.schema.StockPainter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Schema element (box, candle and stock charts): the schema column API, frame support, content
 * equality. The geometries created per row are in {@link SchemaElementChartPathTest}.
 */
@Tag("core")
class SchemaElementTest {
   // ------------------------------------------------------------------------------- API

   @Test
   @DisplayName("a new element dodges symmetrically and is not stacked")
   void defaults() {
      SchemaElement element = new SchemaElement();

      assertEquals(GraphElement.DODGE_SYMMETRIC, element.getCollisionModifier());
      assertEquals(3, element.getCollisionModifier());
      assertFalse(element.isStack());
   }

   @Test
   @DisplayName("addVar is not allowed; use addSchema")
   void addVarRejected() {
      RuntimeException ex =
         assertThrows(RuntimeException.class, () -> new SchemaElement().addVar("x"));

      assertEquals("Use addSchema to add variables.", ex.getMessage());
   }

   @Test
   @DisplayName("addSchema adds one variable per schema and getVars lists every column")
   void addSchemaAndGetVars() {
      SchemaElement element = new SchemaElement(new CandlePainter());
      element.addSchema("h", "c", "o", "l");

      assertEquals(1, element.getVarCount());
      assertEquals("h", element.getVar(0));

      element.addSchema("a", "b");

      assertEquals(2, element.getVarCount());
      assertEquals("h", element.getVar(0));
      assertEquals("a", element.getVar(1));
      assertArrayEquals(new String[] { "h", "c", "o", "l", "a", "b" }, element.getVars());
   }

   @Test
   @DisplayName("removeVar removes a whole schema")
   void removeVar() {
      SchemaElement element = new SchemaElement(new CandlePainter());
      element.addSchema("h", "c", "o", "l");
      element.addSchema("a", "b");

      element.removeVar(0);

      assertEquals(1, element.getVarCount());
      assertArrayEquals(new String[] { "a", "b" }, element.getVars());
   }

   @Test
   @DisplayName("clearVars removes every schema")
   void clearVars() {
      SchemaElement element = new SchemaElement(new CandlePainter());
      element.addSchema("h", "c", "o", "l");
      element.addSchema("a", "b");

      element.clearVars();

      assertEquals(0, element.getVarCount());
      assertEquals(0, element.getVars().length);
   }

   @Test
   @DisplayName("frame support follows the painter: box rejects shape and texture, candle only shape")
   void supportsFrame() {
      SchemaElement box = new SchemaElement(new BoxPainter());
      SchemaElement candle = new SchemaElement(new CandlePainter());
      SchemaElement stock = new SchemaElement(new StockPainter());

      assertFalse(box.supportsFrame(mock(ShapeFrame.class)));
      assertFalse(box.supportsFrame(mock(TextureFrame.class)));
      assertTrue(box.supportsFrame(mock(ColorFrame.class)));
      assertTrue(candle.supportsFrame(mock(TextureFrame.class)));
      assertFalse(candle.supportsFrame(mock(ShapeFrame.class)));
      assertFalse(stock.supportsFrame(mock(TextureFrame.class)));
   }

   // ---------------------------------------------------------------------- equalsContent

   private static SchemaElement element(Supplier<SchemaPainter> painter, String dim, String... cols) {
      SchemaElement element = new SchemaElement(painter.get());
      element.addDim(dim);
      element.addSchema(cols);
      return element;
   }

   @Test
   @DisplayName("other columns, another painter class or another dimension are different")
   void equalsContentDiffers() {
      SchemaElement base = element(BoxPainter::new, "x", "a", "b");

      assertFalse(base.equalsContent(element(BoxPainter::new, "x", "a", "c")));
      assertFalse(base.equalsContent(element(CandlePainter::new, "x", "a", "b")));
      assertFalse(base.equalsContent(element(BoxPainter::new, "y", "a", "b")));
   }

   @Test
   @DisplayName("an element equals its clone")
   void equalsContentClone() {
      SchemaElement element = element(BoxPainter::new, "x", "a", "b");

      assertTrue(element.equalsContent(element.clone()));
   }
}
