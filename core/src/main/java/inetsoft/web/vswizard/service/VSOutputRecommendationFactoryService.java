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
package inetsoft.web.vswizard.service;

import inetsoft.web.vswizard.recommender.object.VSOutputRecommendationFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VSOutputRecommendationFactoryService {
   @Autowired
   public VSOutputRecommendationFactoryService(List<VSOutputRecommendationFactory<?>> factories) {
      factories.forEach((factory) -> registerFactory(factory.getVSAssemblyClass(), factory));
   }

   /**
    * Registers a recommendation factory instance.
    *
    * @param strategy the recommend strategy supported by the factory.
    * @param factory  the factory.
    */
   private void registerFactory(Class<?> type, VSOutputRecommendationFactory<?> factory) {
      factories.put(type, factory);
   }

   /**
    * Get the VSChartRecommendationFactory for current strategy.
    */
   public VSOutputRecommendationFactory getFactory(Class type) {
      VSOutputRecommendationFactory factory = factories.get(type);
      Objects.requireNonNull(
         factory,
         () -> "No factory registered for current recommend strategy: " + type);

      return factory;
   }

   private final Map<Class<?>, VSOutputRecommendationFactory> factories = new HashMap<>();
}
