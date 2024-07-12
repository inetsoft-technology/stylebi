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

import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.web.binding.model.graph.aesthetic.VisualFrameModel;

/**
 * Abstract model factory for classes that handle the creation of DTO models
 * for viewsheet aesthetic visual frame wrapper.
 *
 * @param <V> the source frame wrapper type.
 * @param <F> the target model type.
 */
public abstract class VisualFrameModelFactory
   <V extends VisualFrameWrapper, F extends VisualFrameModel>
{
   /**
    * Gets the frame wrapper class supported by this factory.
    *
    * @return the frame wrapper class.
    */
   public abstract Class<V> getVisualFrameWrapperClass();

   /**
    * Creates a new model instance for the specified visual frame.
    *
    * @param wrapper the visual frame wrapper.
    *
    * @return a new model.
    */
   public abstract F createVisualFrameModel(V wrapper);

   /**
    * Update a VisualFrameWrapper by the specified model.
    *
    * @param wrapper the VisualFrameWrapper need to update.
    * @param model the specified model
    * @return the after updated VisualFrameWrapper.
    */
   public V updateVisualFrameWrapper0(V wrapper, F model) {
      return wrapper;
   }

   /**
    * This method will create a new instance of VisualFrame in subclass.
    */
   protected abstract VisualFrame getVisualFrame();

   /**
    * Update a VisualFrameWrapper by the specified model.
    *
    * @param wrapper the VisualFrameWrapper need to update.
    * @param model the specified model
    * @return the after updated VisualFrameWrapper.
    */
   public V updateVisualFrameWrapper(V wrapper, F model) {
      if(model == null || wrapper == null) {
         return null;
      }

      wrapper.getVisualFrame().setField(model.getField());
      updateVisualFrameWrapper0(wrapper, model);

      return wrapper;
   }
}
