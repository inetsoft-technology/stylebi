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
package inetsoft.web.vswizard.service;

import inetsoft.web.vswizard.recommender.object.VSChartDefaultRecommendationFactory;
import inetsoft.web.vswizard.recommender.object.VSChartRecommendationFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VSChartRecommendationFactoryService {
   @Autowired
   public VSChartRecommendationFactoryService(List<VSChartRecommendationFactory> factories) {
      factories.forEach((factory) -> registerFactory(factory.getStrategyName(), factory));
   }

   /**
    * Registers a recommendation factory instance.
    *
    * @param strategy the recommend strategy supported by the factory.
    * @param factory  the factory.
    */
   private void registerFactory(String strategy, VSChartRecommendationFactory factory) {
      factories.put(strategy, factory);
   }

   /**
    * Get the VSChartRecommendationFactory for current strategy.
    */
   public VSChartRecommendationFactory getFactory() {
      VSChartRecommendationFactory factory = factories.get(getCurrentStrategy());
      Objects.requireNonNull(
         factory,
         () -> "No factory registered for current recommend strategy: " + getCurrentStrategy());

      return factory;
   }

   /**
    * Get the current recommendation strategy.
    */
   private String getCurrentStrategy() {
      return VSChartDefaultRecommendationFactory.STRATEGY_NAME;
   }

   private final Map<String, VSChartRecommendationFactory> factories = new HashMap<>();
}
