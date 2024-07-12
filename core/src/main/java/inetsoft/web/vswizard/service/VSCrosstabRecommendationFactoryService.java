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
package inetsoft.web.vswizard.service;

import inetsoft.web.vswizard.recommender.object.VSCrosstabDefaultRecommendationFactory;
import inetsoft.web.vswizard.recommender.object.VSCrosstabRecommendationFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VSCrosstabRecommendationFactoryService {
   @Autowired
   public VSCrosstabRecommendationFactoryService(List<VSCrosstabRecommendationFactory> factories) {
      factories.forEach((factory) -> registerFactory(factory.getStrategyName(), factory));
   }

   /**
    * Registers a recommendation factory instance.
    *
    * @param strategy the recommend strategy supported by the factory.
    * @param factory  the factory.
    */
   private void registerFactory(String strategy, VSCrosstabRecommendationFactory factory) {
      factories.put(strategy, factory);
   }

   /**
    * Get the VSTableRecommendationFactory for current strategy.
    */
   public VSCrosstabRecommendationFactory getFactory() {
      VSCrosstabRecommendationFactory factory = factories.get(getCurrentStrategy());
      Objects.requireNonNull(
         factory,
         () -> "No factory registered for current recommend strategy: " + getCurrentStrategy());

      return factory;
   }

   /**
    * Get the current recommendation strategy.
    */
   private String getCurrentStrategy() {
      return VSCrosstabDefaultRecommendationFactory.STRATEGY_NAME;
   }

   private final Map<String, VSCrosstabRecommendationFactory> factories = new HashMap<>();
}
