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
package inetsoft.web.viewsheet.model.annotation;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.model.*;
import org.springframework.stereotype.Component;

import java.awt.*;

public class VSAnnotationModel extends VSObjectModel<AnnotationVSAssembly> {
   public VSAnnotationModel(AnnotationVSAssembly assembly,
                            VSRectangleModel annotationRectangleModel,
                            VSLineModel annotationLineModel,
                            HtmlContentModel contentModel,
                            int row,
                            int col,
                            boolean hidden,
                            int annotationType,
                            RuntimeViewsheet rvs)
   {
      super(assembly, rvs);
      this.annotationRectangleModel = annotationRectangleModel;
      this.annotationLineModel = annotationLineModel;
      this.contentModel = contentModel;
      this.row = row;
      this.col = col;
      this.hidden = hidden;
      this.annotationType = annotationType;
   }

   public static VSAnnotationModel create(AnnotationVSAssembly assembly) {
      final AnnotationVSAssemblyInfo info = (AnnotationVSAssemblyInfo) assembly.getInfo();
      final Viewsheet viewsheet = info.getViewsheet();
      final Assembly rectangle = viewsheet.getAssembly(info.getRectangle());
      final Assembly line = viewsheet.getAssembly(info.getLine());
      final int row = info.getRow();
      final int col = info.getCol();
      final boolean hidden = !viewsheet.getAnnotationsVisible();
      final int annotationType = info.getType();
      final boolean assemblyAnnotation = annotationType == AnnotationVSAssemblyInfo.ASSEMBLY;
      VSRectangleModel rectangleModel = null;
      VSLineModel lineModel = null;
      HtmlContentModel content = null;

      if(rectangle instanceof AnnotationRectangleVSAssembly) {
         final String name = assembly.getName();
         final Assembly baseAssembly = AnnotationVSUtil.getBaseAssembly(viewsheet, name);

         // restrict the rectangle to the viewsheet
         if(baseAssembly != null && assemblyAnnotation) {
            final Point rectanglePosition = rectangle.getPixelOffset();
            final Point pixelOffset = baseAssembly.getPixelOffset();
            rectanglePosition.y = Math.max(rectanglePosition.y, -pixelOffset.y);
            rectanglePosition.x = Math.max(rectanglePosition.x, -pixelOffset.x);
            rectangle.setPixelOffset(rectanglePosition);
         }

         // create rectangle model
         final AnnotationRectangleVSAssembly annotationRectangle =
            (AnnotationRectangleVSAssembly) rectangle;
         rectangleModel = new VSRectangleModel(annotationRectangle, null);
         AnnotationRectangleVSAssemblyInfo rectangleInfo =
            (AnnotationRectangleVSAssemblyInfo) annotationRectangle.getVSAssemblyInfo();
         content = HtmlContentModel.create(rectangleInfo.getContent());
      }

      if(line instanceof AnnotationLineVSAssembly) {
         // update anchor here since the rectangle position may have changed
         ((AnnotationLineVSAssembly) line).updateAnchor(viewsheet);
         lineModel = new VSLineModel((AnnotationLineVSAssembly) line, null);
      }

      return new VSAnnotationModel(assembly, rectangleModel, lineModel,
                                   content, row, col, hidden, annotationType, null);
   }

   public VSRectangleModel getAnnotationRectangleModel() {
      return annotationRectangleModel;
   }

   public VSLineModel getAnnotationLineModel() {
      return annotationLineModel;
   }

   public HtmlContentModel getContentModel() {
      return contentModel;
   }

   public int getRow() {
      return row;
   }

   public int getCol() {
      return col;
   }

   public boolean isHidden() {
      return hidden;
   }

   public int getAnnotationType() {
      return annotationType;
   }

   private final VSRectangleModel annotationRectangleModel;
   private final VSLineModel annotationLineModel;
   private final HtmlContentModel contentModel;
   private final int row;
   private final int col;

   // If the viewsheet has annotation status hidden (separate from visible)
   private final boolean hidden;

   // {viewsheet, assembly, data}
   private final int annotationType;

   @Component
   public static final class VSAnnotationModelFactory
      extends VSObjectModelFactory<AnnotationVSAssembly, VSAnnotationModel>
   {
      public VSAnnotationModelFactory() {
         super(AnnotationVSAssembly.class);
      }

      @Override
      public VSAnnotationModel createModel(AnnotationVSAssembly assembly, RuntimeViewsheet rvs) {
         Viewsheet viewsheet = assembly.getViewsheet();
         String name = assembly.getAbsoluteName();
         Assembly baseAssembly = AnnotationVSUtil.getBaseAssembly(viewsheet, name);

         // If there's a base assembly then ignore this call. The model will be
         // created when the base assembly is created
         if(baseAssembly != null) {
            return null;
         }

         return VSAnnotationModel.create(assembly);
      }
   }
}
