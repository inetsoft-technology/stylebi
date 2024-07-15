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
package inetsoft.web.binding.service.graph.aesthetic;

import inetsoft.graph.aesthetic.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.graph.aesthetic.*;
import org.springframework.stereotype.Component;

public abstract class ShapeFrameModelFactory<V extends ShapeFrameWrapper,
   F extends ShapeFrameModel> extends VisualFrameModelFactory<V, F>
{
   @Component
   public static final class StaticShapeFrameModelFactory
      extends ShapeFrameModelFactory<StaticShapeFrameWrapper, StaticShapeModel>
   {
      @Override
      public Class<StaticShapeFrameWrapper> getVisualFrameWrapperClass() {
         return StaticShapeFrameWrapper.class;
      }

      @Override
      public StaticShapeModel createVisualFrameModel(StaticShapeFrameWrapper wrapper) {
         return new StaticShapeModel(wrapper);
      }

      @Override
      public StaticShapeFrameWrapper updateVisualFrameWrapper0(
         StaticShapeFrameWrapper nwrapper, StaticShapeModel model)
      {
         if(!Tool.equals(nwrapper.getShape(), model.getShape())) {
            nwrapper.setShape(model.getShape());
         }

         return nwrapper;
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new StaticShapeFrame();
      }
   }

   @Component
   public static final class CategoricalShapeFrameModelFactory
      extends ShapeFrameModelFactory<CategoricalShapeFrameWrapper, CategoricalShapeModel>
   {
      @Override
      public Class<CategoricalShapeFrameWrapper> getVisualFrameWrapperClass() {
         return CategoricalShapeFrameWrapper.class;
      }

      @Override
      public CategoricalShapeModel createVisualFrameModel(
         CategoricalShapeFrameWrapper wrapper)
      {
         return new CategoricalShapeModel(wrapper);
      }

      @Override
      public CategoricalShapeFrameWrapper updateVisualFrameWrapper0(
         CategoricalShapeFrameWrapper nwrapper, CategoricalShapeModel model)
      {
         String[] shapes = model.getShapes();

         if(shapes == null || shapes.length == 0) {
            return nwrapper;
         }

         for(int i = 0; i < shapes.length; i++) {
            if(!Tool.equals(nwrapper.getShape(i), shapes[i])) {
               nwrapper.setShape(i, shapes[i]);
            }
         }

         nwrapper.setChanged(model.isChanged());

         return nwrapper;
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new CategoricalShapeFrame();
      }
   }

   @Component
   public static final class FillShapeFrameModelFactory
      extends ShapeFrameModelFactory<FillShapeFrameWrapper, FillShapeModel>
   {
      @Override
      public Class<FillShapeFrameWrapper> getVisualFrameWrapperClass() {
         return FillShapeFrameWrapper.class;
      }

      @Override
      public FillShapeModel createVisualFrameModel(FillShapeFrameWrapper wrapper) {
         return new FillShapeModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new FillShapeFrame();
      }
   }

   @Component
   public static final class OrientationShapeFrameModelFactory
      extends ShapeFrameModelFactory<OrientationShapeFrameWrapper, OrientationShapeModel>
   {
      @Override
      public Class<OrientationShapeFrameWrapper> getVisualFrameWrapperClass() {
         return OrientationShapeFrameWrapper.class;
      }

      @Override
      public OrientationShapeModel createVisualFrameModel(
         OrientationShapeFrameWrapper wrapper)
      {
         return new OrientationShapeModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new OrientationShapeFrame();
      }
   }

   @Component
   public static final class PolygonShapeFrameModelFactory
      extends ShapeFrameModelFactory<PolygonShapeFrameWrapper, PolygonShapeModel>
   {
      @Override
      public Class<PolygonShapeFrameWrapper> getVisualFrameWrapperClass() {
         return PolygonShapeFrameWrapper.class;
      }

      @Override
      public PolygonShapeModel createVisualFrameModel(PolygonShapeFrameWrapper wrapper) {
         return new PolygonShapeModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new PolygonShapeFrame();
      }
   }

   @Component
   public static final class TriangleShapeFrameModelFactory
      extends ShapeFrameModelFactory<TriangleShapeFrameWrapper, TriangleShapeModel>
   {
      @Override
      public Class<TriangleShapeFrameWrapper> getVisualFrameWrapperClass() {
         return TriangleShapeFrameWrapper.class;
      }

      @Override
      public TriangleShapeModel createVisualFrameModel(
         TriangleShapeFrameWrapper wrapper)
      {
         return new TriangleShapeModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new TriangleShapeFrame();
      }
   }

   @Component
   public static final class OvalShapeFrameModelFactory
      extends ShapeFrameModelFactory<OvalShapeFrameWrapper, OvalShapeModel>
   {
      @Override
      public Class<OvalShapeFrameWrapper> getVisualFrameWrapperClass() {
         return OvalShapeFrameWrapper.class;
      }

      @Override
      public OvalShapeModel createVisualFrameModel(
         OvalShapeFrameWrapper wrapper)
      {
         return new OvalShapeModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new OvalShapeFrame();
      }
   }
}
