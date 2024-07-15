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
package inetsoft.graph.internal;

import inetsoft.graph.internal.ContourCell.Side;

import java.awt.geom.*;
import java.util.Arrays;

public class MarchingSquaresContour {
   public MarchingSquaresContour(Rectangle2D bounds) {
      this.width = bounds.getWidth();
      this.height = bounds.getHeight();
   }

   public GeneralPath[] createShapes(double[][] data, double[] levels) {
      double[][] data2 = fillData(data, levels);
      GeneralPath[] paths = new GeneralPath[levels.length];

      for(int i = 0; i < levels.length; i++) {
         ContourCell[][] contour = createCells(data2, levels[i]);
         paths[i] = createPath(contour, data2, levels[i]);
      }

      return paths;
   }

   private ContourCell[][] createCells(double[][] data, double level) {
      int rows = data.length;
      int cols = data[0].length;

      ContourCell[][] cells = new ContourCell[rows - 1][cols - 1];

      for(int r = 0; r < rows - 1; r++) {
         for(int c = 0; c < cols - 1; c++) {
            // set the flag to mark neighbor above or below threshold
            int ll = data[r][c] > level ? 0 : 1;
            int lr = data[r][c + 1] > level ? 0 : 2;
            int ur = data[r + 1][c + 1] > level ? 0 : 4;
            int ul = data[r + 1][c] > level ? 0 : 8;
            int caseIndex = ll | lr | ur | ul;
            boolean flipped = false;

            if(caseIndex == 5 || caseIndex == 10) {
               // avg of surrounding values
               double center = (data[r][c] + data[r][c + 1] +
                  data[r + 1][c + 1] + data[r + 1][c]) / 4;

               if(center < level) {
                  flipped = true;
               }
            }

            cells[r][c] = new ContourCell();
            cells[r][c].setFlipped(flipped);
            cells[r][c].setCaseIndex(caseIndex);
         }
      }

      return cells;
   }

   private GeneralPath createPath(ContourCell[][] cells, double data[][], double threshold) {
      int numRows = cells.length;
      int numCols = cells[0].length;

      int r, c;

      for(r = 0; r < numRows; r++) {
         for(c = 0; c < numCols; c++) {
            interpolateCrossing(cells, data, r, c, threshold);
         }
      }

      GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);

      for(r = 0; r < numRows; r++) {
         for(c = 0; c < numCols; c++) {
            if(cells[r][c].getCaseIndex() != 0 && cells[r][c].getCaseIndex() != 5
                && cells[r][c].getCaseIndex() != 10 && cells[r][c].getCaseIndex() != 15)
            {
               createSubpath(cells, r, c, path);
            }
         }
      }

      return path;
   }

   private void createSubpath(ContourCell[][] cells, int r, int c, GeneralPath path) {
      Side prevSide = Side.NONE;
      ContourCell start = cells[r][c];
      Point2D pt = start.getCrossingPoint(start.firstSide(prevSide));

      double xscale = width / cells[r].length;
      double yscale = height / cells.length;
      double x = c + pt.getX();
      double y = r + pt.getY();

      path.moveTo(x * xscale, y * yscale);
      pt = start.getCrossingPoint(start.nextSide(prevSide));

      double xPrev = c + pt.getX();
      double yPrev = r + pt.getY();

      prevSide = start.nextSide(prevSide);

      if(Math.abs(x - xPrev) > E && Math.abs(y - yPrev) > E) {
         path.lineTo(x * xscale, y * yscale);
      }

      switch(prevSide) {
      case BOTTOM:
         r--;
         break;
      case LEFT:
         c--;
         break;
      case RIGHT:
         c++;
         break;
      case TOP:
         r++;
      }

      start.clearIndex();

      ContourCell curCell = cells[r][c];

      while(curCell != start) {
         pt = curCell.getCrossingPoint(curCell.nextSide(prevSide));
         x = c + pt.getX();
         y = r + pt.getY();

         if(Math.abs(x - xPrev) > E && Math.abs(y - yPrev) > E) {
            path.lineTo(x * xscale, y * yscale);
         }

         xPrev = x;
         yPrev = y;
         prevSide = curCell.nextSide(prevSide);

         switch(prevSide) {
         case BOTTOM:
            r -= 1;
            break;
         case LEFT:
            c -= 1;
            break;
         case RIGHT:
            c += 1;
            break;
         case TOP:
            r += 1;
         }

         curCell.clearIndex();
         curCell = cells[r][c];
      }

      path.closePath();
   }

   private double[][] fillData(double[][] data, double[] levels) {
      int rows = data.length;
      int cols = data[0].length;

      double min = Arrays.stream(levels).min().orElse(0) - 1;
      double[][] filled = new double[rows + 2][cols + 2];

      for(int i = 0; i < cols + 2; i++) {
         filled[0][i] = min;
         filled[rows + 1][i] = min;
      }

      for(int i = 0; i < rows + 2; i++) {
         filled[i][0] = min;
         filled[i][cols + 1] = min;
      }

      for(int i = 0; i < rows; i++) {
         for(int j = 0; j < cols; j++) {
            filled[i + 1][j + 1] = data[i][j];
         }
      }

      return filled;
   }

   private void interpolateCrossing(ContourCell[][] cells, double[][] data,
                                    int r, int c, double threshold)
   {
      double a, b;

      ContourCell cell = cells[r][c];
      double ll = data[r][c];
      double lr = data[r][c + 1];
      double ul = data[r + 1][c];
      double ur = data[r + 1][c + 1];

      switch(cell.getCaseIndex()) {
      case 1: case 3: case 5: case 7: case 8:
      case 10: case 12: case 14:
         a = ll;
         b = ul;
         cell.setLeftCrossing((threshold - a) / (b - a)); // frac from LL
         break;
      default:
         break;
      }

      switch(cell.getCaseIndex()) {
      case 1: case 2: case 5: case 6: case 9:
      case 10: case 13: case 14:
         a = ll;
         b = lr;
         cell.setBottomCrossing((threshold - a) / (b - a)); // frac from LL
         break;
      default:
         break;
      }

      switch(cell.getCaseIndex()) {
      case 4: case 5: case 6: case 7: case 8:
      case 9: case 10: case 11:
         a = ul;
         b = ur;
         cell.setTopCrossing((threshold - a) / (b - a)); // frac from UL
         break;
      default:
         break;
      }

      switch(cell.getCaseIndex()) {
      case 2: case 3: case 4: case 5: case 10:
      case 11: case 12: case 13:
         a = lr;
         b = ur;
         cell.setRightCrossing((threshold - a) / (b - a)); // frac from LR
         break;
      default:
         break;
      }
   }

   private final double E = 1e-10;
   private final double width, height;
}
