/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.coord;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.geometry.GraphGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.ElementVO;

import java.util.*;

/**
 * This class is used for removing tile coord that has no valid data.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
class TileCleaner {
   /**
    * @param xscale xscale on the facet coord.
    * @param yscale yscale on the facet coord.
    * @param tiles inner tile coords in rows and columns.
    */
   public TileCleaner(Scale xscale, Scale yscale, TileCoord[][] tiles, EGraph graph,
                      GGraph ggraph)
   {
      this.xscale = xscale;
      this.yscale = yscale;
      this.tiles = tiles;
      this.ggraph = ggraph;

      String[] measures = getMeasures(graph, ggraph.getCoordinate().getDataSet());
      validTiles = Arrays.stream(tiles)
         .map(row -> Arrays.stream(row)
              .map(tile -> Arrays.stream(tile.getCoordinates())
                   .anyMatch(c -> c.getDataSet().containsValue(measures)))
              .toArray(Boolean[]::new))
         .toArray(Boolean[][]::new);
   }

   /**
    * Remove row or column if entire row or columns contains no valid value.
    */
   public TileCoord[][] clean() {
      if(tiles.length == 0) {
         return tiles;
      }

      for(int i = tiles.length - 1; i >= 0 && tiles.length > 1; i--) {
         if(!Arrays.stream(validTiles[i]).anyMatch(v -> v)) {
            removeRow(i);
         }
      }

      for(int i = tiles[0].length - 1; i >= 0 && tiles[0].length > 1; i--) {
         boolean valid = false;

         for(int k = 0; k < validTiles.length; k++) {
            if(validTiles[k][i]) {
               valid = true;
               break;
            }
         }

         if(!valid) {
            removeCol(i);
         }
      }

      // if the bottom row is removed, the bottom axis labels will be missing.
      // reset axis label visibility according to the new coords. (51768)
      TileCoord.setHint(tiles, -1);
      return tiles;
   }

   // extract all measures from graph
   private String[] getMeasures(EGraph graph, DataSet data) {
      Set<String> measures = new HashSet<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         for(String var : graph.getElement(i).getVars()) {
            measures.add(var);

            // all_var not added for radar/treemap/relation. see GraphGenerator.createElement().
            if(!var.startsWith(ElementVO.ALL_PREFIX)) {
               String allCol = ElementVO.ALL_PREFIX + var;

               if(data.indexOfHeader(allCol) >= 0) {
                  measures.add(allCol);
               }
            }
         }
      }

      return measures.toArray(new String[measures.size()]);
   }

   // remove a row from the matrix
   private void removeRow(int idx) {
      removeValue(yscale, idx);

      if(idx == 0 && tiles.length > 1) {
         for(int i = 0; i < tiles[0].length; i++) {
            tiles[1][i].copyAxisVisibility(tiles[0][i]);
         }
      }

      Arrays.stream(tiles[idx]).forEach(t -> removeTile(t));

      TileCoord[][] tiles2 = new TileCoord[tiles.length - 1][];
      System.arraycopy(tiles, 0, tiles2, 0, idx);
      System.arraycopy(tiles, idx + 1, tiles2, idx, tiles2.length - idx);
      this.tiles = tiles2;
   }

   // remove a column from the matrix
   private void removeCol(int idx) {
      removeValue(xscale, idx);

      if(idx == 0) {
         for(int i = 0; i < tiles.length; i++) {
            if(tiles[i].length > 1) {
               tiles[i][1].copyAxisVisibility(tiles[i][0]);
            }
         }
      }

      TileCoord[][] tiles2 = new TileCoord[tiles.length][tiles[0].length - 1];

      for(int i = 0; i < tiles.length; i++) {
         System.arraycopy(tiles[i], 0, tiles2[i], 0, idx);
         System.arraycopy(tiles[i], idx + 1, tiles2[i], idx, tiles2[i].length - idx);
         removeTile(tiles[i][idx]);
      }

      this.tiles = tiles2;
   }

   // remove a value from a scale
   private void removeValue(Scale scale, int idx) {
      if(scale instanceof CategoricalScale) {
         CategoricalScale scale2 = (CategoricalScale) scale;
         Object[] vals = scale2.getValues();
         Object[] vals2 = new Object[vals.length - 1];

         System.arraycopy(vals, 0, vals2, 0, idx);
         System.arraycopy(vals, idx + 1, vals2, idx, vals2.length - idx);
         scale2.init(vals2);
      }
   }

   // remove tile associated geometry
   private void removeTile(TileCoord tile) {
      for(int i = ggraph.getGeometryCount() - 1; i >= 0; i--) {
         Geometry gobj = ggraph.getGeometry(i);

         if(gobj instanceof GraphGeometry) {
            Coordinate gcoord = ((GraphGeometry) gobj).getCoordinate();

            if(containsCoord(tile, gcoord)) {
               ggraph.removeGeometry(i);
            }
         }
      }
   }

   private boolean containsCoord(TileCoord tile, Coordinate coord) {
      for(Coordinate child : tile.getCoordinates()) {
         if(child == coord) {
            return true;
         }

         if(child instanceof FacetCoord) {
            FacetCoord facet = (FacetCoord) child;

            if(facet.getOuterCoordinate() == coord) {
               return true;
            }

            for(TileCoord[] row : facet.getTileCoords()) {
               for(TileCoord sub : row) {
                  if(containsCoord(sub, coord)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private Boolean[][] validTiles;
   private TileCoord[][] tiles;
   private Scale xscale, yscale;
   private GGraph ggraph;
}
