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
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.web.binding.model.graph.aesthetic.VisualFrameModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class VisualFrameModelFactoryService {
   @Autowired
   public VisualFrameModelFactoryService(List<VisualFrameModelFactory<?, ?>> factories) {
      factories.forEach(
         (factory) -> registerFactory(factory.getVisualFrameWrapperClass(), factory));
   }

   /**
    * Registers a model factory instance.
    *
    * @param cls the visualframe wrapper class supported by the factory.
    * @param factory  the factory.
    */
   private void registerFactory(Class<?> cls, VisualFrameModelFactory<?, ?> factory) {
      factories.put(cls, factory);
   }

   /**
    * Creates a DTO model for the specified visualframe wrapper.
    *
    * @param wrapper the visualframe wrapper.
    *
    * @return the DTO model.
    */
   @SuppressWarnings("unchecked")
   public <V extends VisualFrameWrapper, F extends VisualFrameModel> F
      createVisualFrameModel(V wrapper)
   {
      VisualFrameModelFactory<V, F> factory = getFactory(wrapper);
      return factory.createVisualFrameModel(wrapper);
   }

   /**
    * Update a VisualFrameWrapper by the specified model.
    *
    * @param wrapper the VisualFrameWrapper need to update.
    * @param model the specified model
    * @return the after updated VisualFrameWrapper.
    */
   @SuppressWarnings("unchecked")
   public <V extends VisualFrameWrapper, F extends VisualFrameModel> V
      updateVisualFrameWrapper(V wrapper, F model)
   {
      if(model == null) {
         return null;
      }

      VisualFrameWrapper nwrapper = getVisualFrameWrapper(wrapper, model);
      VisualFrameModelFactory<V, F> factory = getFactory(nwrapper);

      return factory.updateVisualFrameWrapper((V) nwrapper, model);
   }

   @SuppressWarnings("rawtypes")
   private VisualFrameModelFactory getFactory(VisualFrameWrapper wrapper) {
      VisualFrameModelFactory factory = factories.get(wrapper.getClass());

      if(factory == null) {
         throw new IllegalArgumentException(
            "No model factory registered for visual frame wrapper " +
            wrapper.getClass().getName());
      }

      return factory;
   }

   /**
    * When the wrapper is null or is not based on the specified frame,
    * we should create a new instance of wrapper based on this VisualFrameModel.
    */
   @SuppressWarnings("unchecked")
   private VisualFrameWrapper getVisualFrameWrapper(VisualFrameWrapper wrapper,
                                                    VisualFrameModel model)
   {
      if(model == null) {
         return null;
      }

      VisualFrame frame = model.createVisualFrame();

      if(frame == null) {
         return null;
      }

      try {
         VisualFrameWrapper nwrapper = VisualFrameWrapper.wrap(frame);

        if(wrapper == null || shouldRefresh(nwrapper, wrapper)) {
           wrapper = nwrapper;
        }
      }
      catch(Exception e) {
         LOG.debug(e.getMessage(), e);
      }

      return wrapper;
   }

   /**
    * Return if should use the new wrapper to replace the old wrapper.
    */
   private boolean shouldRefresh(VisualFrameWrapper nwrapper,
      VisualFrameWrapper owrapper)
   {
      // 1. frame type changed.
      // 2. fixed bug #19230 that need wrapper is new object,
      // Or colormap can't clear and delete on categorical color dialog.
      return nwrapper != null && owrapper != null &&
         !nwrapper.getClass().getName().equals(owrapper.getClass().getName()) ||
         nwrapper instanceof CategoricalColorFrameWrapper;
   }

   private final Map<Class<?>, VisualFrameModelFactory<?, ?>> factories = new HashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(VisualFrameModelFactoryService.class);
}
