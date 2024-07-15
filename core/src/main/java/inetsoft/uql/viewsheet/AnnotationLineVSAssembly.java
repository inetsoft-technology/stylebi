/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;

/**
 * AnnotationLineVSAssembly represents one annotation line assembly contained
 * in a <tt>Viewsheet</tt>.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class AnnotationLineVSAssembly extends LineVSAssembly {
   /**
    * Constructor.
    */
   public AnnotationLineVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public AnnotationLineVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.ANNOTATION_LINE_ASSET;
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new AnnotationLineVSAssemblyInfo();
   }

   /**
    * Update the anchor positions.
    */
   @Override
   public boolean updateAnchor(Viewsheet vs) {
      LineVSAssemblyInfo info = (LineVSAssemblyInfo) getInfo();
      boolean changed = false;
      Point pt;
      box = getRelatedBox(vs);
      Rectangle lineRect = getLineBounds(vs, info);
      Rectangle boxRect = getRectBounds(vs, info);

      if(info.getStartAnchorID() != null && Tool.equals(box, info.getStartAnchorID()) &&
         lineRect != null && boxRect != null && !lineRect.intersects(boxRect))
      {
         pt = getAnchorPos(vs, info, info.getStartAnchorID(), info.getStartAnchorPos());
      }
      else {
         // make the start point matches the anchor point that is closest
         // mid point to the end point of the line
         pt = getClosestAnchorPos(vs);

         if(pt != null) {
            info.setStartAnchorID(anchor);
            info.setStartAnchorPos(anchorPos);
         }
      }

      // anchor element deleted
      if(pt == null) {
         info.setStartAnchorID(null);
      }
      else if(!info.getStartPos().equals(pt)) {
         setStartPos(pt);
         changed = true;
      }

      return changed;
   }

   /**
    * Get the line assembly pixel bounds.
    */
   private Rectangle getLineBounds(Viewsheet vs, LineVSAssemblyInfo info) {
      final Point startPixelPt = getAnchorPos(vs, info, info.getStartAnchorID(),
                                              info.getStartAnchorPos(), false);

      if(startPixelPt == null) {
         return null;
      }

      final Point lineLayoutPosition = info.getLayoutPosition();
      final Point linePixelPosition = vs.getPixelPosition(info);
      final Point linePt = lineLayoutPosition != null ? lineLayoutPosition : linePixelPosition;
      final Point endPt = info.getEndPos();
      final Point endPixelPt = new Point(linePt.x + endPt.x, linePt.y + endPt.y);
      final Point newPixelPt =
         new Point(startPixelPt.x < endPixelPt.x ? startPixelPt.x : endPixelPt.x,
                   startPixelPt.y < endPixelPt.y ? startPixelPt.y : endPixelPt.y);
      final int width = Math.max(1, Math.abs(startPixelPt.x - endPixelPt.x));
      final int height = Math.max(1, Math.abs(startPixelPt.y - endPixelPt.y));

      return new Rectangle(newPixelPt.x, newPixelPt.y, width, height);
   }

   /**
    * Get the rectangle assembly pixel bounds.
    */
   private Rectangle getRectBounds(Viewsheet vs, LineVSAssemblyInfo info) {
      final VSAssembly boxAssembly = (VSAssembly) vs.getAssembly(box);

      if(boxAssembly == null || !Tool.equals(box, info.getStartAnchorID())) {
         return null;
      }

      final VSAssemblyInfo rectangleInfo = (VSAssemblyInfo) boxAssembly.getInfo();

      // use scaled rectangle position
      final Point boxLayoutPosition = rectangleInfo.getLayoutPosition();
      final Point boxPixelPosition = vs.getPixelPositionInViewsheet(rectangleInfo);
      final Point boxPt = boxLayoutPosition != null ? boxLayoutPosition : boxPixelPosition;

      // use pixel rectangle size
      final Dimension boxSize = vs.getPixelSize(rectangleInfo);
      return new Rectangle(boxPt.x, boxPt.y, boxSize.width, boxSize.height);
   }

   /**
    * Get the closest mid anchor point by the end point.
    */
   private Point getClosestAnchorPos(Viewsheet vs) {
      final Point[][] pos = prepareAnchorPos(vs);

      if(pos == null) {
         return null;
      }

      final LineVSAssemblyInfo info = (LineVSAssemblyInfo) getInfo();
      final Point lineLayoutPosition = info.getLayoutPosition();
      final Point linePixelPosition = vs.getPixelPosition(info);
      final Point linePt = lineLayoutPosition != null ? lineLayoutPosition : linePixelPosition;
      final Point endPt = info.getEndPos();
      final int x = linePt.x + endPt.x;
      final int y = linePt.y + endPt.y;
      int col;
      int row;

      if(y < pos[0][0].y) {
         row = 0;
         col = 1;
      }
      else if(y > pos[2][0].y) {
         row = 2;
         col = 1;
      }
      else if(x < pos[0][0].x) {
         row = 1;
         col = 0;
      }
      else {
         row = 1;
         col = 2;
      }

      anchorPos = getAnchor(row, col);
      Point point = pos[row][col];

      return new Point(point.x - linePt.x, point.y - linePt.y);
   }

   /**
    * Prepare the eight anchors position.
    */
   private Point[][] prepareAnchorPos(Viewsheet vs) {
      if(box == null) {
         return null;
      }

      LineVSAssemblyInfo info = (LineVSAssemblyInfo) getInfo();
      Point[][] pos = new Point[3][3];

      for(int i = 0; i < 3; i++) {
         for(int j = 0; j < 3; j++) {
            pos[i][j] = getAnchorPos(vs, info, box, getAnchor(i, j), false);
         }
      }

      anchor = box;
      return pos;
   }

   /**
    * Get the related rectangle assembly which in same annotation.
    */
   private String getRelatedBox(Viewsheet vs) {
      return Arrays.stream(vs.getAssemblies())
         .filter(AnnotationVSAssembly.class::isInstance)
         .map(Assembly::getInfo)
         .map(AnnotationVSAssemblyInfo.class::cast)
         .filter((info) -> Tool.equals(getName(), info.getLine()))
         .map(AnnotationVSAssemblyInfo::getRectangle)
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   /**
    * Get related anchor position by the temporary row and col index.
    */
   private int getAnchor(int row, int col) {
      if(row == 0 && col == 0) {
         return LineVSAssemblyInfo.NORTH_WEST;
      }
      else if(row == 0 && col == 1) {
         return LineVSAssemblyInfo.NORTH;
      }
      else if(row == 0 && col == 2) {
         return LineVSAssemblyInfo.NORTH_EAST;
      }
      else if(row == 1 && col == 0) {
         return LineVSAssemblyInfo.WEST;
      }
      else if(row == 1 && col == 2) {
         return LineVSAssemblyInfo.EAST;
      }
      else if(row == 2 && col == 0) {
         return LineVSAssemblyInfo.SOUTH_WEST;
      }
      else if(row == 2 && col == 1) {
         return LineVSAssemblyInfo.SOUTH;
      }
      else if(row == 2 && col == 2) {
         return LineVSAssemblyInfo.SOUTH_EAST;
      }

      return -1;
   }

   private String box;
   private String anchor;
   private int anchorPos;
}
