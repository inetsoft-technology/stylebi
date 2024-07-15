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
package inetsoft.web.viewsheet.service;

import inetsoft.report.StyleConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.command.AnnotationChangedCommand;
import inetsoft.web.viewsheet.event.annotation.AddAnnotationEvent;
import inetsoft.web.viewsheet.model.HtmlContentModel;
import org.apache.commons.math3.linear.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

@Service
public class VSAnnotationService {
   @Autowired
   public VSAnnotationService(VSObjectService service)
   {
      this.service = service;
   }

   /**
    * Set default values for Viewsheet level annotations.
    *
    * @param rectangleInfo the annotation rectangle info on which to set the defaults
    */
   public final void initDefaultViewsheetFormat(AnnotationRectangleVSAssemblyInfo rectangleInfo) {
      rectangleInfo.initDefaultFormat();
      rectangleInfo.getFormat().getUserDefinedFormat().setRoundCornerValue(12);
      rectangleInfo.getFormat().getDefaultFormat().setBackgroundValue("0xfef7e0");
      rectangleInfo.getFormat().getDefaultFormat().setForegroundValue("0xddb38e");
   }

   /**
    * Set default values for assembly level annotations.
    */
   public final void initDefaultAssemblyFormat(AnnotationRectangleVSAssemblyInfo rectangleInfo,
                                               AnnotationLineVSAssemblyInfo lineInfo)
   {
      rectangleInfo.initDefaultFormat();
      lineInfo.initDefaultFormat();
   }

   /**
    * Set default values for data level annotations.
    */
   public final void initDefaultDataFormat(AnnotationRectangleVSAssemblyInfo rectangleInfo,
                                           AnnotationLineVSAssemblyInfo lineInfo)
   {
      rectangleInfo.initDefaultFormat();
      lineInfo.initDefaultFormat();
      lineInfo.setEndArrowStyleValue(StyleConstants.NONE);
   }

   /**
    * Update the annotation content. The content is cleaned and added to the given
    * annotation rectangle
    *
    * @param assembly the annotation rectangle
    * @param content  the potentially unsafe content
    */
   public final void updateAnnotationContent(final AnnotationRectangleVSAssembly assembly,
                                             final String content)
   {
      if(content != null) {
         final String cleanContent = cleanAnnotationContent(content);
         final AnnotationRectangleVSAssemblyInfo rinfo =
            (AnnotationRectangleVSAssemblyInfo) assembly.getVSAssemblyInfo();
         rinfo.setContent(cleanContent);
      }
   }

   /**
    * Refresh an annotation. Creates an AddVSObjectCommand for the annotation and its
    * rectangle, lays out the viewsheet, then refreshes the annotation's parent assembly
    *
    * @param rvs                 the runtime viewsheet that contains the given assemblies
    * @param annotation          the annotation assembly
    * @param annotationRectangle the annotation's corresponding rectangle
    * @param parentAssembly      the assembly that contains the annotation (null if viewsheet level)
    * @param linkUri             the link URI
    * @param dispatcher          the command dispatcher
    */
   public final void refreshAnnotation(final RuntimeViewsheet rvs,
                                       final AnnotationVSAssembly annotation,
                                       final AnnotationRectangleVSAssembly annotationRectangle,
                                       final VSAssembly parentAssembly,
                                       final String linkUri,
                                       final CommandDispatcher dispatcher) throws Exception
   {
      service.addDeleteVSObject(rvs, annotation, dispatcher);
      service.addDeleteVSObject(rvs, annotationRectangle, dispatcher);
      service.layoutViewsheet(rvs, linkUri, dispatcher);
      service.refreshVSAssembly(rvs, parentAssembly, dispatcher);
      dispatcher.sendCommand(AnnotationChangedCommand.of(true));
   }

   /**
    * Transform the annotation rectangle.
    * The position is summed with the given rectangle's x and y values.
    * The size is summed with the rectangle's given width and height values.
    *
    * @param assembly the annotation rectangle to transform
    * @param newBounds the new position of the rectangle
    */
   public final boolean transformRectangle(final AnnotationRectangleVSAssembly assembly,
                                           final Rectangle newBounds,
                                           final AffineTransform scaleTransform)
   {
      final VSAssemblyInfo assemblyInfo = assembly.getVSAssemblyInfo();
      final Viewsheet vs = assembly.getViewsheet();
      final boolean scaleToScreen = vs != null && vs.getViewsheetInfo().isScaleToScreen();

      // modify the x and y positions
      Point2D src = new Point2D.Double(newBounds.getX(), newBounds.getY());
      Point2D dst = new Point2D.Double();
      scaleTransform.transform(src, dst);
      int x = (int) Math.floor(dst.getX());
      int y = (int) Math.floor(dst.getY());

      // modify the width and height
//      src = new Point2D.Double(newBounds.getMaxX(), newBounds.getMaxY());
//      scaleTransform.transform(src, dst);
//      int w = ((int) Math.round(dst.getX())) - x;
//      int h = ((int) Math.round(dst.getY())) - y;

      assembly.setPixelOffset(new Point(x, y));

      if(assemblyInfo.getLayoutPosition() != null || scaleToScreen) {
         assemblyInfo.setScaledPosition(new Point(newBounds.x, newBounds.y));
      }

      // resize the rectangle
      final Dimension newSize = new Dimension(newBounds.width, newBounds.height);
      assembly.setPixelSize(newSize);
      return (assembly.getPixelSize().getHeight() != newSize.getHeight()
              || assembly.getPixelSize().getWidth() != newSize.getWidth());
   }

   public AffineTransform getInverseScaleTransform(Viewsheet viewsheet, VSAssembly parentAssembly) {
      if(parentAssembly == null) {
         return new AffineTransform();
      }

      VSAssemblyInfo info = parentAssembly.getVSAssemblyInfo();

      Dimension baseSize = info.getLayoutSize(false);
      Dimension scaledSize = info.getLayoutSize(true);

      if(baseSize != scaledSize) { // identity is correct in this context
         Point basePosition = info.getLayoutPosition(false);
         Point scaledPosition = info.getLayoutPosition(true);

         if(baseSize == null) {
            baseSize = info.getPixelSize();
         }

         if(basePosition == null) {
            basePosition = viewsheet.getPixelPosition(info);
         }

         /*
           Solve for the affine transform matrix for solving for M:

           M * S = B

           where

             M is the affine transformation matrix
             S is a matrix containing 3 points from the scaled bounding rectangle
             B is a matrix containing 3 points from the base bounding rectangle

             [Sx1 Sx2 Sx3]   [Bx1 Bx2 Bx3]
           M [Sy1 Sy2 Sy3] = [By1 By2 By3]
             [  1   1   1]

           this is solved by multiplying B by the inverse of S.
          */

         double sx1 = scaledPosition.getX();
         double sy1 = scaledPosition.getY();
         double sx2 = scaledPosition.getX();
         double sy2 = scaledPosition.getY() + scaledSize.getHeight();
         double sx3 = scaledPosition.getX() + scaledSize.getWidth();
         double sy3 = scaledPosition.getY();

         double bx1 = basePosition.getX();
         double by1 = basePosition.getY();
         double bx2 = basePosition.getX();
         double by2 = basePosition.getY() + baseSize.getHeight();
         double bx3 = basePosition.getX() + baseSize.getWidth();
         double by3 = basePosition.getY();

         RealMatrix scaledMatrix = new Array2DRowRealMatrix(new double[][] {
            { sx1, sx2, sx3 },
            { sy1, sy2, sy3 },
            {  1D,  1D,  1D }
         });

         RealMatrix baseMatrix = new Array2DRowRealMatrix(new double[][] {
            { bx1, bx2, bx3 },
            { by1, by2, by3 }
         });

         RealMatrix tx =
            baseMatrix.multiply(new LUDecomposition(scaledMatrix).getSolver().getInverse());
         return new AffineTransform(
            tx.getEntry(0, 0), tx.getEntry(1, 0), tx.getEntry(0, 1),
            tx.getEntry(1, 1), tx.getEntry(0, 2), tx.getEntry(1, 2));
      }

      // identity
      return new AffineTransform();
   }

   /**
    * Get an adjusted point for AnnotationRectangle's position.
    * This method is used to solve the problem that annotations cannot be displayed
    * when scale to screen is set.See bug #49787.
    *
    * @param event        the event with properties of the annotation to add
    * @param rvs          the current viewsheet
    * @param psize        the annotation rectangle preferred size
    * @param assemblyType the type of annotation to add: viewsheet, assembly, or data
    *
    * @return the point is used for annotation rectangle position
    */
   public Point getAdjustedPoint(AddAnnotationEvent event, RuntimeViewsheet rvs,
                                 Dimension psize, int assemblyType)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetInfo viewsheetInfo = vs.getViewsheetInfo();
      String parent = event.getParent();
      VSAssembly parentAssembly = vs.getAssembly(parent);
      int x = Math.max(event.getX(), 1) + 70;
      int y = Math.max(event.getY(), 1);

      if(parentAssembly == null) {
         return new Point(x, y);
      }

      if(!(parentAssembly instanceof CrosstabVSAssembly) || !viewsheetInfo.isScaleToScreen() ||
         !(assemblyType == AnnotationVSAssemblyInfo.DATA))
      {
         return new Point(x, y);
      }

      int row = event.getRow();
      int col = event.getCol();
      int paneWidth = 0;
      int paneHeight = 0;
      int parentEndX = 0;
      int parentEndY = 0;
      int balancePaddingX = 0;
      int balancePaddingY = 0;
      Assembly[] assemblies = vs.getAssemblies();

      for(int i = 0; i < assemblies.length; i++) {
         Assembly assembly = assemblies[i];

         if(assembly instanceof AnnotationVSAssembly || assembly instanceof AnnotationLineVSAssembly ||
            assembly instanceof AnnotationRectangleVSAssembly)
         {
            continue;
         }

         if(assembly instanceof VSAssembly) {
            VSAssemblyInfo vsAssemblyInfo = ((VSAssembly) assembly).getVSAssemblyInfo();
            Point position = vsAssemblyInfo.getLayoutPosition();
            Dimension size = vsAssemblyInfo.getLayoutSize();
            int px = 0;
            int py = 0;
            int sw = 0;
            int sh = 0;

            if(position != null) {
               px = position.x;
               py = position.y;

               if(i == 0) {
                  balancePaddingX = px;
                  balancePaddingY = py;
               }

               balancePaddingX = Math.min(balancePaddingX, px);
               balancePaddingY = Math.min(balancePaddingY, py);
            }

            if(size != null) {
               sw = size.width;
               sh = size.height;
            }

            if(parentAssembly == assembly) {
               parentEndX = px + sw;
               parentEndY = py + sh;
            }

            paneWidth = Math.max(px + sw, paneWidth);
            paneHeight = Math.max(py + sh, paneHeight);
         }
      }

      VSTableLens tableLens = ((CrosstabVSAssembly) parentAssembly).getLastTableLens();

      if(tableLens == null) {
         tableLens = rvs.getViewsheetSandbox()
            .getVSTableLens(parentAssembly.getAbsoluteName(), false);
      }

      int[] cellWidth = tableLens.getColumnWidths();
      int[] cellHeight = tableLens.getRowHeights();
      int wdiff = paneWidth - parentEndX + (viewsheetInfo.isBalancePadding() ? balancePaddingX : 0);
      int hdiff = paneHeight - parentEndY + (viewsheetInfo.isBalancePadding() ? balancePaddingY : 0);
      int colWidthSum = 0;
      int rowHeightSum = 0;

      for(int i = col + 1; i < cellWidth.length; i++) {
         colWidthSum += cellWidth[i];
      }

      for(int i = row + 1; i < cellHeight.length; i++) {
         rowHeightSum += cellHeight[i];
      }

      if(colWidthSum >= psize.width + 85 - cellWidth[col] / 2 &&
         rowHeightSum >= psize.height - cellHeight[row] / 2)
      {
         return new Point(x, y);
      }

      int totalWidth = x + cellWidth[col] / 2 + colWidthSum + wdiff - 70;
      int totalHeight = y + cellHeight[row] / 2 + rowHeightSum + hdiff;
      int currentWidth = x + psize.width;
      int currentHeight = y + psize.height + 15;

      if(totalWidth < currentWidth) {
         x = totalWidth - psize.width;
      }

      if(totalHeight < currentHeight) {
         y = totalHeight - psize.height - 15;
      }

      return new Point(x > 0 ? x : 1, y > 0 ? y : 1);
   }

   /**
    * Pass the content through an HtmlContentModel for cleaning
    *
    * @param content the content to clean
    *
    * @return the cleaned content
    */
   private String cleanAnnotationContent(final String content) {
      return HtmlContentModel.create(content).getContent();
   }

   private final VSObjectService service;
}
