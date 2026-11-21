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
package inetsoft.graph.coord;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.geometry.GraphGeometry;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.ElementVO;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Removal of facet rows and columns that hold no data.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class TileCleanerTest {
   private static final boolean T = true;
   private static final boolean F = false;

   private static DataSet cellData(boolean valid, List<String> record) {
      DataSet ds = mock(DataSet.class);
      when(ds.containsValue(any(String[].class))).thenAnswer(inv -> {
         if(record != null) {
            record.clear();

            for(Object o : inv.getArguments()) {
               record.add(String.valueOf(o));
            }
         }

         return valid;
      });
      return ds;
   }

   private static CategoricalScale cats(String prefix, int n) {
      CategoricalScale s = new CategoricalScale(prefix);
      Object[] vals = new Object[n];

      for(int i = 0; i < n; i++) {
         vals[i] = prefix + i;
      }

      s.init(vals);
      return s;
   }

   /** Grid of single-child tiles plus the graph the cleaner works on. */
   private static class Fix {
      final Scale xs;
      final Scale ys;
      final TileCoord[][] tiles;
      final TreemapCoord[][] child;
      final EGraph egraph = new EGraph();
      final RectCoord outer = new RectCoord();
      final GGraph ggraph;
      final List<String> record = new ArrayList<>();

      Fix(boolean[][] valid, Scale xs, Scale ys) {
         this(valid, xs, ys, new String[0][], new Object[][] { { "Cat", "m" }, { "A", 1 } });
      }

      Fix(boolean[][] valid, Scale xs, Scale ys, String[][] vars, Object[][] rows) {
         this.xs = xs;
         this.ys = ys;
         DefaultDataSet data = new DefaultDataSet(rows);
         outer.setDataSet(data);

         for(String[] vs : vars) {
            for(String v : vs) {
               egraph.addElement(new IntervalElement("Cat", v));
               egraph.setScale(v, new LinearScale(v));
            }
         }

         egraph.setScale("Cat", new CategoricalScale("Cat"));

         egraph.setCoordinate(outer);
         ggraph = egraph.createGGraph(outer, data);
         tiles = new TileCoord[valid.length][valid[0].length];
         child = new TreemapCoord[valid.length][valid[0].length];

         for(int r = 0; r < valid.length; r++) {
            for(int c = 0; c < valid[r].length; c++) {
               child[r][c] = new TreemapCoord();
               child[r][c].setDataSet(cellData(valid[r][c], record));
               tiles[r][c] = TileCoord.vertical(child[r][c]);
               addGeometry(child[r][c]);
            }
         }
      }

      void addGeometry(Coordinate coord) {
         ggraph.addGeometry(new GraphGeometry(ggraph, outer, coord, new double[] { 0 }));
      }

      TileCoord[][] clean() {
         return new TileCleaner(xs, ys, tiles, egraph, ggraph).clean();
      }

      List<Coordinate> remaining() {
         List<Coordinate> list = new ArrayList<>();

         for(int i = 0; i < ggraph.getGeometryCount(); i++) {
            Geometry g = ggraph.getGeometry(i);

            if(g instanceof GraphGeometry) {
               list.add(((GraphGeometry) g).getCoordinate());
            }
         }

         return list;
      }
   }

   private static List<Object> values(Scale s) {
      return Arrays.asList(s.getValues());
   }

   private static void assertGrid(TileCoord[][] actual, TileCoord[][] expected) {
      assertEquals(expected.length, actual.length, "rows");

      for(int r = 0; r < expected.length; r++) {
         assertEquals(expected[r].length, actual[r].length, "columns of row " + r);

         for(int c = 0; c < expected[r].length; c++) {
            assertSame(expected[r][c], actual[r][c], "tile " + r + "," + c);
         }
      }
   }

   private static TileCoord[][] grid(TileCoord[]... rows) {
      return rows;
   }

   @Test
   @DisplayName("nothing is removed when every tile holds data")
   void nothingEmpty() {
      Fix f = new Fix(new boolean[][] { { T, T }, { T, T } }, cats("x", 2), cats("y", 2));
      TileCoord[][] orig = new TileCoord[][] { f.tiles[0].clone(), f.tiles[1].clone() };
      assertGrid(f.clean(), orig);
      assertEquals(List.of("x0", "x1"), values(f.xs));
      assertEquals(List.of("y0", "y1"), values(f.ys));
      assertEquals(4, f.ggraph.getGeometryCount());
   }

   @Test
   @DisplayName("an empty bottom row is removed with its scale value and geometries")
   void emptyBottomRow() {
      Fix f = new Fix(new boolean[][] { { F, F }, { T, T } }, cats("x", 2), cats("y", 2));
      TileCoord[] keep = f.tiles[1];
      assertGrid(f.clean(), grid(keep));
      assertEquals(List.of("y1"), values(f.ys));
      assertEquals(List.of(f.child[1][0], f.child[1][1]), f.remaining());
   }

   @Test
   @DisplayName("an empty top row is removed")
   void emptyTopRow() {
      Fix f = new Fix(new boolean[][] { { T, T }, { F, F } }, cats("x", 2), cats("y", 2));
      TileCoord[] keep = f.tiles[0];
      assertGrid(f.clean(), grid(keep));
      assertEquals(List.of("y0"), values(f.ys));
      assertEquals(List.of(f.child[0][0], f.child[0][1]), f.remaining());
   }

   @Test
   @DisplayName("an empty middle row is removed and the others keep their order")
   void emptyMiddleRow() {
      Fix f = new Fix(new boolean[][] { { T, T }, { F, F }, { T, T } }, cats("x", 2),
                      cats("y", 3));
      TileCoord[] r0 = f.tiles[0];
      TileCoord[] r2 = f.tiles[2];
      assertGrid(f.clean(), grid(r0, r2));
      assertEquals(List.of("y0", "y2"), values(f.ys));
      assertEquals(List.of(f.child[0][0], f.child[0][1], f.child[2][0], f.child[2][1]),
                   f.remaining());
   }

   @Test
   @DisplayName("an empty first column is removed in every row")
   void emptyFirstColumn() {
      Fix f = new Fix(new boolean[][] { { F, T }, { F, T } }, cats("x", 2), cats("y", 2));
      TileCoord a = f.tiles[0][1];
      TileCoord b = f.tiles[1][1];
      assertGrid(f.clean(), grid(new TileCoord[] { a }, new TileCoord[] { b }));
      assertEquals(List.of("x1"), values(f.xs));
      assertEquals(List.of(f.child[0][1], f.child[1][1]), f.remaining());
   }

   @Test
   @DisplayName("an empty middle column is removed and the others keep their order")
   void emptyMiddleColumn() {
      Fix f = new Fix(new boolean[][] { { T, F, T }, { T, F, T } }, cats("x", 3), cats("y", 2));
      TileCoord[] r0 = { f.tiles[0][0], f.tiles[0][2] };
      TileCoord[] r1 = { f.tiles[1][0], f.tiles[1][2] };
      assertGrid(f.clean(), grid(r0, r1));
      assertEquals(List.of("x0", "x2"), values(f.xs));
   }

   @Test
   @DisplayName("a row and a column that each hold one valid tile are kept")
   void partlyValidKept() {
      Fix f = new Fix(new boolean[][] { { T, F }, { F, T } }, cats("x", 2), cats("y", 2));
      TileCoord[][] orig = new TileCoord[][] { f.tiles[0].clone(), f.tiles[1].clone() };
      assertGrid(f.clean(), orig);
      assertEquals(4, f.ggraph.getGeometryCount());
   }

   @Test
   @DisplayName("an empty row and an empty column are removed together")
   void rowAndColumnAtOnce() {
      Fix f = new Fix(new boolean[][] { { F, F }, { F, T } }, cats("x", 2), cats("y", 2));
      TileCoord keep = f.tiles[1][1];
      assertGrid(f.clean(), grid(new TileCoord[] { keep }));
      assertEquals(List.of("y1"), values(f.ys));
      assertEquals(List.of("x1"), values(f.xs));
      assertEquals(List.of(f.child[1][1]), f.remaining());
   }

   @Test
   @DisplayName("the last row and the last column are never removed")
   void lastRowAndColumnStay() {
      Fix one = new Fix(new boolean[][] { { F } }, cats("x", 1), cats("y", 1));
      TileCoord t = one.tiles[0][0];
      assertGrid(one.clean(), grid(new TileCoord[] { t }));
      assertEquals(List.of("y0"), values(one.ys));
      assertEquals(List.of("x0"), values(one.xs));

      Fix col = new Fix(new boolean[][] { { F }, { F } }, cats("x", 1), cats("y", 2));
      TileCoord first = col.tiles[0][0];
      assertGrid(col.clean(), grid(new TileCoord[] { first }));
      assertEquals(List.of("y0"), values(col.ys));

      Fix row = new Fix(new boolean[][] { { F, F } }, cats("x", 2), cats("y", 1));
      TileCoord first2 = row.tiles[0][0];
      assertGrid(row.clean(), grid(new TileCoord[] { first2 }));
      assertEquals(List.of("x0"), values(row.xs));
   }

   @Test
   @DisplayName("a grid without any data keeps only its first tile")
   void allInvalidGrid() {
      Fix f = new Fix(new boolean[][] { { F, F }, { F, F } }, cats("x", 2), cats("y", 2));
      TileCoord first = f.tiles[0][0];
      assertGrid(f.clean(), grid(new TileCoord[] { first }));
   }

   @Test
   @DisplayName("a null scale value is a real value: the row is kept, a non-null one is removed")
   void nullRowValueKept() {
      CategoricalScale ys = new CategoricalScale("y");
      ys.init("y0", null);
      Fix f = new Fix(new boolean[][] { { T }, { F } }, cats("x", 1), ys);
      assertEquals(2, f.clean().length);

      Fix control = new Fix(new boolean[][] { { T }, { F } }, cats("x", 1), cats("y", 2));
      assertEquals(1, control.clean().length);
   }

   @Test
   @DisplayName("a null column scale value is a real value: the column is kept")
   void nullColumnValueKept() {
      CategoricalScale xs = new CategoricalScale("x");
      xs.init("x0", null);
      Fix f = new Fix(new boolean[][] { { T, F } }, xs, cats("y", 1));
      assertEquals(2, f.clean()[0].length);

      Fix control = new Fix(new boolean[][] { { T, F } }, cats("x", 2), cats("y", 1));
      assertEquals(1, control.clean()[0].length);
   }

   @Test
   @DisplayName("pinned: a row of a non-categorical scale is removed and the scale is untouched")
   void nonCategoricalScale() {
      LinearScale ys = new LinearScale("y");
      ys.setMin(0);
      ys.setMax(10);
      Fix f = new Fix(new boolean[][] { { F }, { T } }, cats("x", 1), ys);
      TileCoord keep = f.tiles[1][0];
      assertGrid(assertDoesNotThrow(f::clean), grid(new TileCoord[] { keep }));
      assertEquals(0, ys.getMin(), 0);
      assertEquals(10, ys.getMax(), 0);
   }

   @Test
   @DisplayName("without a row scale the empty column is still removed")
   void noYScale() {
      Fix f = new Fix(new boolean[][] { { T, F, T } }, cats("x", 3), null);
      TileCoord[] keep = { f.tiles[0][0], f.tiles[0][2] };
      assertGrid(f.clean(), grid(keep));
      assertEquals(List.of("x0", "x2"), values(f.xs));
   }

   @Test
   @DisplayName("a tile counts as valid when any of its children holds data")
   void tileWithSeveralChildren() {
      Fix f = new Fix(new boolean[][] { { T, T } }, cats("x", 2), cats("y", 1));
      TreemapCoord a1 = new TreemapCoord();
      TreemapCoord a2 = new TreemapCoord();
      TreemapCoord b1 = new TreemapCoord();
      TreemapCoord b2 = new TreemapCoord();
      a1.setDataSet(cellData(F, null));
      a2.setDataSet(cellData(T, null));
      b1.setDataSet(cellData(F, null));
      b2.setDataSet(cellData(F, null));
      TileCoord t0 = TileCoord.vertical(a1, a2);
      TileCoord t1 = TileCoord.vertical(b1, b2);
      TileCoord[][] tiles = { { t0, t1 } };

      TileCoord[][] result = new TileCleaner(f.xs, f.ys, tiles, f.egraph, f.ggraph).clean();
      assertGrid(result, grid(new TileCoord[] { t0 }));
      assertEquals(List.of("x0"), values(f.xs));
   }

   @Test
   @DisplayName("a tile whose second child holds data is kept while a tile without data goes")
   void validChildAfterInvalidOneDecidesTile() {
      Fix f = new Fix(new boolean[][] { { T, T } }, cats("x", 2), cats("y", 1));
      TreemapCoord a1 = new TreemapCoord();
      TreemapCoord b1 = new TreemapCoord();
      TreemapCoord b2 = new TreemapCoord();
      a1.setDataSet(cellData(F, null));
      b1.setDataSet(cellData(F, null));
      b2.setDataSet(cellData(T, null));
      TileCoord t0 = TileCoord.vertical(a1);
      TileCoord t1 = TileCoord.vertical(b1, b2);
      TileCoord[][] tiles = { { t0, t1 } };

      TileCoord[][] result = new TileCleaner(f.xs, f.ys, tiles, f.egraph, f.ggraph).clean();
      assertGrid(result, grid(new TileCoord[] { t1 }));
      assertEquals(List.of("x1"), values(f.xs));
   }

   @Test
   @DisplayName("the measures of all elements and an existing all-twin reach the data set")
   void measuresPassedToDataSet() {
      Object[][] rows = { { "Cat", "m1", "m2", ElementVO.ALL_PREFIX + "m1" }, { "A", 1, 2, 3 } };
      Fix f = new Fix(new boolean[][] { { T } }, cats("x", 1), cats("y", 1),
                      new String[][] { { "m1", "m2" } }, rows);
      f.clean();
      assertEquals(new TreeSet<>(List.of("m1", "m2", ElementVO.ALL_PREFIX + "m1")),
                   new TreeSet<>(f.record));
   }

   @Test
   @DisplayName("only the geometries of the removed row go, the others keep their order")
   void onlyRemovedGeometriesGo() {
      Fix f = new Fix(new boolean[][] { { T, T }, { F, F }, { T, T } }, cats("x", 2),
                      cats("y", 3));
      f.clean();
      assertEquals(List.of(f.child[0][0], f.child[0][1], f.child[2][0], f.child[2][1]),
                   f.remaining());
   }

   @Test
   @DisplayName("removing a tile with a nested facet removes the geometries of the facet too")
   void nestedFacetTile() {
      Fix f = new Fix(new boolean[][] { { F }, { T } }, cats("x", 1), cats("y", 2));
      f.ggraph.removeGeometry(0); // the tile of row 0 is replaced by a facet below

      TreemapCoord inner = new TreemapCoord();
      inner.setDataSet(cellData(F, null));
      RectCoord facetOuter = new RectCoord();
      FacetCoord facet = new FacetCoord(facetOuter, new Coordinate[] { inner }, true);
      facet.setDataSet(cellData(F, null));
      facet.setTileCoords(new TileCoord[][] { { TileCoord.vertical(inner) } });
      TileCoord nested = TileCoord.vertical(facet);
      f.tiles[0][0] = nested;
      f.addGeometry(facetOuter);
      f.addGeometry(inner);
      // order of geometries is irrelevant for the removal

      TileCoord keep = f.tiles[1][0];
      assertGrid(f.clean(), grid(new TileCoord[] { keep }));
      assertEquals(List.of(f.child[1][0]), f.remaining());
   }

   private static RectCoord rectChild(boolean valid) {
      CategoricalScale xs = new CategoricalScale("Cat");
      xs.init("A", "B", "C");
      LinearScale ys = new LinearScale("m");
      ys.setMin(0);
      ys.setMax(100);
      RectCoord rc = new RectCoord(xs, ys);
      rc.setDataSet(cellData(valid, null));
      return rc;
   }

   @Test
   @DisplayName("after the bottom row is removed the surviving tile shows its bottom axis (#51768)")
   void bottomAxisAfterBottomRowRemoved() {
      Fix f = new Fix(new boolean[][] { { F }, { T } }, cats("x", 1), cats("y", 2));
      RectCoord low = rectChild(false);
      RectCoord high = rectChild(true);
      TileCoord[][] tiles = { { TileCoord.vertical(low) }, { TileCoord.vertical(high) } };
      TileCoord.setHint(tiles, ICoordinate.ALL_MOST);
      assertFalse(tiles[1][0].isAxisLabelVisible(ICoordinate.BOTTOM_AXIS), "precondition");
      TileCoord survivor = tiles[1][0];

      TileCoord[][] result = new TileCleaner(f.xs, f.ys, tiles, f.egraph, f.ggraph).clean();
      assertGrid(result, grid(new TileCoord[] { survivor }));
      assertTrue(survivor.isAxisLabelVisible(ICoordinate.BOTTOM_AXIS), "bottom label");
      assertTrue(survivor.isAxisTickVisible(ICoordinate.BOTTOM_AXIS), "bottom tick");
   }

   @Test
   @DisplayName("after the left column is removed the surviving tile shows its left axis")
   void leftAxisAfterLeftColumnRemoved() {
      Fix f = new Fix(new boolean[][] { { F, T } }, cats("x", 2), cats("y", 1));
      RectCoord left = rectChild(false);
      RectCoord right = rectChild(true);
      TileCoord[][] tiles = { { TileCoord.vertical(left), TileCoord.vertical(right) } };
      TileCoord.setHint(tiles, ICoordinate.ALL_MOST);
      assertFalse(tiles[0][1].isAxisLabelVisible(ICoordinate.LEFT_AXIS), "precondition");
      TileCoord survivor = tiles[0][1];

      TileCoord[][] result = new TileCleaner(f.xs, f.ys, tiles, f.egraph, f.ggraph).clean();
      assertGrid(result, grid(new TileCoord[] { survivor }));
      assertTrue(survivor.isAxisLabelVisible(ICoordinate.LEFT_AXIS), "left label");
      assertTrue(survivor.isAxisTickVisible(ICoordinate.LEFT_AXIS), "left tick");
   }

   @Test
   @DisplayName("after the top row is removed the surviving tile shows its top axis ticks")
   void topTickAfterTopRowRemoved() {
      Fix f = new Fix(new boolean[][] { { T }, { F } }, cats("x", 1), cats("y", 2));
      RectCoord low = rectChild(true);
      RectCoord high = rectChild(false);
      TileCoord[][] tiles = { { TileCoord.vertical(low) }, { TileCoord.vertical(high) } };
      TileCoord.setHint(tiles, ICoordinate.ALL_MOST);
      assertFalse(tiles[0][0].isAxisTickVisible(ICoordinate.TOP_AXIS), "precondition");
      TileCoord survivor = tiles[0][0];

      TileCoord[][] result = new TileCleaner(f.xs, f.ys, tiles, f.egraph, f.ggraph).clean();
      assertGrid(result, grid(new TileCoord[] { survivor }));
      assertTrue(survivor.isAxisTickVisible(ICoordinate.TOP_AXIS), "top tick");
   }

   @Test
   @DisplayName("after the right column is removed the surviving tile shows its right axis ticks")
   void rightTickAfterRightColumnRemoved() {
      Fix f = new Fix(new boolean[][] { { T, F } }, cats("x", 2), cats("y", 1));
      RectCoord left = rectChild(true);
      RectCoord right = rectChild(false);
      TileCoord[][] tiles = { { TileCoord.vertical(left), TileCoord.vertical(right) } };
      TileCoord.setHint(tiles, ICoordinate.ALL_MOST);
      assertFalse(tiles[0][0].isAxisTickVisible(ICoordinate.RIGHT_AXIS), "precondition");
      TileCoord survivor = tiles[0][0];

      TileCoord[][] result = new TileCleaner(f.xs, f.ys, tiles, f.egraph, f.ggraph).clean();
      assertGrid(result, grid(new TileCoord[] { survivor }));
      assertTrue(survivor.isAxisTickVisible(ICoordinate.RIGHT_AXIS), "right tick");
   }

   @Test
   @DisplayName("an empty tile array is returned as is")
   void noRows() {
      Fix f = new Fix(new boolean[][] { { T } }, cats("x", 1), cats("y", 1));
      TileCoord[][] none = new TileCoord[0][];
      assertSame(none, new TileCleaner(f.xs, f.ys, none, f.egraph, f.ggraph).clean());
   }
}
