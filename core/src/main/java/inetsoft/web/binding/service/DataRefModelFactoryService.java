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
package inetsoft.web.binding.service;

import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.drm.DataRefModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DataRefModelFactoryService {
   @Autowired
   public DataRefModelFactoryService(List<DataRefModelFactory<?, ?>> factories) {
      factories.forEach((factory) -> registerFactory(factory.getDataRefClass(), factory));
   }

   /**
    * Registers a dataRef factory instance.
    *
    * @param cls the dataRef class supported by the factory.
    * @param factory  the factory.
    */
   private void registerFactory(Class<?> cls, DataRefModelFactory<?, ?> factory) {
      factories.put(cls, factory);
   }

   /**
    * Creates a DTO model for the specified DataRef.
    *
    * @param dataRef the dataRef interface.
    *
    * @return the DTO model.
    */
   @SuppressWarnings("unchecked")
   public <R extends DataRef> DataRefModel createDataRefModel(R dataRef) {
      if(dataRef == null) {
         return null;
      }

      DataRefModelFactory<R, DataRefModel> factory = null;

      for(Class cls = dataRef.getClass(); factory == null && DataRef.class.isAssignableFrom(cls);
          cls = cls.getSuperclass())
      {
         factory = factories.get(cls);
      }

      if(factory == null) {
         LOG.warn("No model factory registered for dataRef "
            + dataRef.getClass().getName() + ", please create this model factory");

         return null;
      }

      return factory.createDataRefModel(dataRef);
   }

   private final Map<Class<?>, DataRefModelFactory> factories = new HashMap<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(DataRefModelFactoryService.class);
}
