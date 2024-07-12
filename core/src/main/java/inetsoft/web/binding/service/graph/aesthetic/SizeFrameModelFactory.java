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
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.web.binding.model.graph.aesthetic.*;
import org.springframework.stereotype.Component;

public abstract class SizeFrameModelFactory<V extends SizeFrameWrapper,
   F extends SizeFrameModel> extends VisualFrameModelFactory<V, F>
{
   @Override
   public V updateVisualFrameWrapper0(V nwrapper, F model) {
      if(nwrapper == null || !model.isChanged()) {
         return null;
      }

      if(nwrapper.getLargest() != model.getLargest()) {
         nwrapper.setLargest(model.getLargest());
      }

      if(nwrapper.getSmallest() != model.getSmallest()) {
         nwrapper.setSmallest(model.getSmallest());
      }

      nwrapper.setChanged(model.isChanged());

      return nwrapper;
   }

   @Component
   public static final class StaticSizeFrameModelFactory
      extends SizeFrameModelFactory<StaticSizeFrameWrapper, StaticSizeModel>
   {
      @Override
      public Class<StaticSizeFrameWrapper> getVisualFrameWrapperClass() {
         return StaticSizeFrameWrapper.class;
      }

      @Override
      public StaticSizeModel createVisualFrameModel(StaticSizeFrameWrapper wrapper) {
         return new StaticSizeModel(wrapper);
      }

      @Override
      public StaticSizeFrameWrapper updateVisualFrameWrapper0(
         StaticSizeFrameWrapper nwrapper, StaticSizeModel model)
      {
         if(model.isChanged() && nwrapper.getSize() != model.getSize()) {
            nwrapper.setSize(model.getSize());
         }

         nwrapper.setChanged(model.isChanged());

         // clear user value when auto is checked
         if(!model.isChanged()) {
            if(nwrapper.getVisualFrame() instanceof StaticSizeFrame) {
               ((StaticSizeFrame) nwrapper.getVisualFrame())
                  .resetCompositeValues(CompositeValue.Type.USER);
            }
         }

         return super.updateVisualFrameWrapper0(nwrapper, model);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new StaticSizeFrame();
      }
   }

   @Component
   public static final class CategoricalSizeFrameModelFactory
      extends SizeFrameModelFactory<CategoricalSizeFrameWrapper, CategoricalSizeModel>
   {
      @Override
      public Class<CategoricalSizeFrameWrapper> getVisualFrameWrapperClass() {
         return CategoricalSizeFrameWrapper.class;
      }

      @Override
      public CategoricalSizeModel createVisualFrameModel(
         CategoricalSizeFrameWrapper wrapper)
      {
         return new CategoricalSizeModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new CategoricalSizeFrame();
      }
   }

   @Component
   public static final class LinearSizeFrameModelFactory
      extends SizeFrameModelFactory<LinearSizeFrameWrapper, LinearSizeModel>
   {
      @Override
      public Class<LinearSizeFrameWrapper> getVisualFrameWrapperClass() {
         return LinearSizeFrameWrapper.class;
      }

      @Override
      public LinearSizeModel createVisualFrameModel(LinearSizeFrameWrapper wrapper) {
         return new LinearSizeModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new LinearSizeFrame();
      }
   }
}
