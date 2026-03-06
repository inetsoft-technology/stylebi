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
package inetsoft.web.factory;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.AnalyticEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.XEngine;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.*;

import java.rmi.RemoteException;

/**
 * Spring configuration for the core engine beans.
 */
@Configuration
public class EngineConfiguration {

   /**
    * The central asset repository. Backed by AnalyticEngine → RepletEngine.
    * {@code @Lazy} defers initialization until first use, keeping it out of process-aot.
    */
   @Bean
   @Lazy
   public AnalyticRepository analyticRepository() {
      AnalyticEngine engine = new AnalyticEngine();
      AssetUtil.setAssetRepository(false, engine);
      engine.init();
      return engine;
   }

   /**
    * The data source / query engine. Declaring {@code AnalyticRepository} as a parameter
    * ensures it is initialized first (RepletEngine.init() accesses XRepository via
    * DesignSession, so the ordering prevents a half-initialized state).
    */
   @Bean
   @Lazy
   public XRepository xRepository(@Lazy AnalyticRepository analyticRepository) {
      return new XEngine();
   }

   /**
    * The viewsheet composition engine. Wraps AnalyticRepository.
    */
   @Bean("viewsheetEngine")
   @Lazy
   public ViewsheetService viewsheetService(@Lazy AnalyticRepository analyticRepository) {
      try {
         return new ViewsheetEngine(analyticRepository.unwrap(AssetRepository.class));
      }
      catch(RemoteException e) {
         throw new BeanCreationException("viewsheetService", "Failed to create ViewsheetEngine", e);
      }
   }
}
