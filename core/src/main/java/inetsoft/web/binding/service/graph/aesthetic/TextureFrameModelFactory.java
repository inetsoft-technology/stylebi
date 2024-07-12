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
package inetsoft.web.binding.service.graph.aesthetic;

import inetsoft.graph.aesthetic.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.web.binding.model.graph.aesthetic.*;
import org.springframework.stereotype.Component;

public abstract class TextureFrameModelFactory<V extends TextureFrameWrapper,
   F extends TextureFrameModel> extends VisualFrameModelFactory<V, F>
{
   @Component
   public static final class StaticTextureFrameModelFactory
      extends TextureFrameModelFactory<StaticTextureFrameWrapper, StaticTextureModel>
   {
      @Override
      public Class<StaticTextureFrameWrapper> getVisualFrameWrapperClass() {
         return StaticTextureFrameWrapper.class;
      }

      @Override
      public StaticTextureModel createVisualFrameModel(
         StaticTextureFrameWrapper wrapper)
      {
         return new StaticTextureModel(wrapper);
      }

      @Override
      public StaticTextureFrameWrapper updateVisualFrameWrapper0(
         StaticTextureFrameWrapper nwrapper, StaticTextureModel model)
      {
         if(nwrapper.getTexture() != model.getTexture()) {
            nwrapper.setTexture(model.getTexture());
         }

         return nwrapper;
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new StaticTextureFrame();
      }
   }

   @Component
   public static final class CategoricalTextureFrameModelFactory
      extends TextureFrameModelFactory<CategoricalTextureFrameWrapper, CategoricalTextureModel>
   {
      @Override
      public Class<CategoricalTextureFrameWrapper> getVisualFrameWrapperClass() {
         return CategoricalTextureFrameWrapper.class;
      }

      @Override
      public CategoricalTextureModel createVisualFrameModel(
         CategoricalTextureFrameWrapper wrapper)
      {
         return new CategoricalTextureModel(wrapper);
      }

      @Override
      public CategoricalTextureFrameWrapper updateVisualFrameWrapper0(
         CategoricalTextureFrameWrapper nwrapper, CategoricalTextureModel model)
      {
         int[] textures = model.getTextures();

         if(textures == null || textures.length == 0) {
            return nwrapper;
         }

         for(int i = 0; i < textures.length; i++) {
            if(nwrapper.getTexture(i) != textures[i]) {
               nwrapper.setTexture(i, textures[i]);
            }
         }

         nwrapper.setChanged(model.isChanged());

         return nwrapper;
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new CategoricalTextureFrame();
      }
   }

   @Component
   public static final class GridTextureFrameModelFactory
      extends TextureFrameModelFactory<GridTextureFrameWrapper, GridTextureModel>
   {
      @Override
      public Class<GridTextureFrameWrapper> getVisualFrameWrapperClass() {
         return GridTextureFrameWrapper.class;
      }

      @Override
      public GridTextureModel createVisualFrameModel(GridTextureFrameWrapper wrapper) {
         return new GridTextureModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new GridTextureFrame();
      }
   }

   @Component
   public static final class OrientationTextureFrameModelFactory
      extends TextureFrameModelFactory<OrientationTextureFrameWrapper, OrientationTextureModel>
   {
      @Override
      public Class<OrientationTextureFrameWrapper> getVisualFrameWrapperClass() {
         return OrientationTextureFrameWrapper.class;
      }

      @Override
      public OrientationTextureModel createVisualFrameModel(
         OrientationTextureFrameWrapper wrapper)
      {
         return new OrientationTextureModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new OrientationTextureFrame();
      }
   }

   @Component
   public static final class LeftTiltTextureFrameModelFactory
      extends TextureFrameModelFactory<LeftTiltTextureFrameWrapper, LeftTiltTextureModel>
   {
      @Override
      public Class<LeftTiltTextureFrameWrapper> getVisualFrameWrapperClass() {
         return LeftTiltTextureFrameWrapper.class;
      }

      @Override
      public LeftTiltTextureModel createVisualFrameModel(
         LeftTiltTextureFrameWrapper wrapper)
      {
         return new LeftTiltTextureModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new LeftTiltTextureFrame();
      }
   }

   @Component
   public static final class RightTiltTextureFrameModelFactory
      extends TextureFrameModelFactory<RightTiltTextureFrameWrapper, RightTiltTextureModel>
   {
      @Override
      public Class<RightTiltTextureFrameWrapper> getVisualFrameWrapperClass() {
         return RightTiltTextureFrameWrapper.class;
      }

      @Override
      public RightTiltTextureModel createVisualFrameModel(
         RightTiltTextureFrameWrapper wrapper)
      {
         return new RightTiltTextureModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new RightTiltTextureFrame();
      }
   }
}
