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
import inetsoft.web.binding.model.graph.aesthetic.*;
import org.springframework.stereotype.Component;

public abstract class LineFrameModelFactory<V extends LineFrameWrapper,
   F extends LineFrameModel> extends VisualFrameModelFactory<V, F>
{
   @Component
   public static final class StaticLineFrameModelFactory
      extends LineFrameModelFactory<StaticLineFrameWrapper, StaticLineModel>
   {
      @Override
      public Class<StaticLineFrameWrapper> getVisualFrameWrapperClass() {
         return StaticLineFrameWrapper.class;
      }

      @Override
      public StaticLineModel createVisualFrameModel(StaticLineFrameWrapper wrapper) {
         return new StaticLineModel(wrapper);
      }

      @Override
      public StaticLineFrameWrapper updateVisualFrameWrapper0(
         StaticLineFrameWrapper nwrapper, StaticLineModel model)
      {
         if(nwrapper.getLine() != model.getLine()) {
            nwrapper.setLine(model.getLine());
         }

         return nwrapper;
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new StaticLineFrame();
      }
   }

   @Component
   public static final class CategoricalLineFrameModelFactory
      extends LineFrameModelFactory<CategoricalLineFrameWrapper, CategoricalLineModel>
   {
      @Override
      public Class<CategoricalLineFrameWrapper> getVisualFrameWrapperClass() {
         return CategoricalLineFrameWrapper.class;
      }

      @Override
      public CategoricalLineModel createVisualFrameModel(
         CategoricalLineFrameWrapper wrapper)
      {
         return new CategoricalLineModel(wrapper);
      }

      @Override
      public CategoricalLineFrameWrapper updateVisualFrameWrapper0(
         CategoricalLineFrameWrapper nwrapper, CategoricalLineModel model)
      {
         int[] lines = model.getLines();

         if(lines == null || lines.length == 0) {
            return nwrapper;
         }

         for(int i = 0; i < lines.length; i++) {
            if(nwrapper.getLine(i) != lines[i]) {
               nwrapper.setLine(i, lines[i]);
            }
         }

         nwrapper.setChanged(model.isChanged());

         return nwrapper;
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new CategoricalLineFrame();
      }
   }

   @Component
   public static final class LinearLineFrameModelFactory
      extends LineFrameModelFactory<LinearLineFrameWrapper, LinearLineModel>
   {
      @Override
      public Class<LinearLineFrameWrapper> getVisualFrameWrapperClass() {
         return LinearLineFrameWrapper.class;
      }

      @Override
      public LinearLineModel createVisualFrameModel(LinearLineFrameWrapper wrapper) {
         return new LinearLineModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new LinearLineFrame();
      }
   }
}
