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

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.composition.SheetLibraryEngine;
import inetsoft.analytic.composition.SheetLibraryService;
import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.AnalyticEngine;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.storage.BlobStorageManager;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.BookmarkLockManager;
import inetsoft.uql.viewsheet.ViewsheetLifecycleMessageChannel;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Plugins;
import inetsoft.util.SingletonManager;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.cluster.ServerClusterClient;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.*;

import java.rmi.RemoteException;

/**
 * Spring configuration for the core engine beans.
 */
@Configuration
public class EngineConfiguration {

   /**
    * Analytic assistant — front-end to the analytic repository for non-distributed callers.
    */
   @Bean
   @Lazy
   public AnalyticAssistant analyticAssistant(@Lazy AnalyticRepository analyticRepository) {
      return new AnalyticAssistant(analyticRepository);
   }

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

   /**
    * Worksheet composition engine. Depends on AnalyticRepository for its asset store.
    */
   @Bean("worksheetService")
   @Lazy
   @Primary
   public WorksheetService worksheetService(@Lazy AnalyticRepository analyticRepository) {
      try {
         return new WorksheetEngine(analyticRepository.unwrap(AssetRepository.class));
      }
      catch(RemoteException e) {
         throw new BeanCreationException("worksheetService", "Failed to create WorksheetEngine", e);
      }
   }

   /** Library engine — base class for worksheet composition. No external dependencies. */
   @Bean
   @Lazy
   public SheetLibraryService sheetLibraryService() {
      return new SheetLibraryEngine();
   }

   /** Pub/sub channel for viewsheet lifecycle events (open/close/execute). */
   @Bean
   @Lazy
   public ViewsheetLifecycleMessageChannel viewsheetLifecycleMessageChannel() {
      return new ViewsheetLifecycleMessageChannel();
   }

   /** Client stub used to submit tasks to the ScheduleServer. */
   @Bean
   @Lazy
   public ScheduleClient scheduleClient() {
      return new ScheduleClient();
   }

   /**
    * The distributed cluster. In the normal server startup path this wraps the instance
    * pre-created by {@code BaseInetsoftApplication.start()} (before Spring runs). The
    * {@code InetsoftConfig} parameter ensures the config bean is resolved first.
    * {@code destroyMethod=""} prevents Spring from auto-calling {@code close()} — lifecycle
    * is managed by {@code SingletonManager.reset()} in {@code BaseInetsoftApplication.shutdownInetsoft()}.
    */
   @Bean(destroyMethod = "")
   public Cluster cluster(InetsoftConfig config) {
      return SingletonManager.getInstance(Cluster.class);
   }

   /** Cluster client for server-to-server communication. */
   @Bean
   public ServerClusterClient serverClusterClient() {
      return new ServerClusterClient(false);
   }

   /** Plugin manager — loads and manages installed plugins from blob storage. */
   @Bean
   @Lazy
   public Plugins plugins(@Lazy BlobStorageManager blobStorageManager) {
      return new Plugins(blobStorageManager.getInstance("plugins", true));
   }

   /** Session ID counter service. */
   @Bean
   @Lazy
   public XSessionService xSessionService() {
      return new XSessionService();
   }

   /** JDBC/tabular driver registry. */
   @Bean
   @Lazy
   public Drivers drivers() {
      return new Drivers();
   }

   /** Data source registry — manages configured data source definitions. */
   @Bean
   @Lazy
   public DataSourceRegistry dataSourceRegistry() throws Exception {
      return new DataSourceRegistry();
   }

   /** License manager — validates installed license keys and enforces limits. */
   @Bean
   @Lazy
   public LicenseManager licenseManager() {
      return new LicenseManager();
   }

   /** Bookmark lock manager — tracks distributed bookmark edit locks. */
   @Bean
   @Lazy
   public BookmarkLockManager bookmarkLockManager() {
      return new BookmarkLockManager();
   }

   /** Indexed storage — blob-backed asset store used by RepletEngine and DataCycleManager. */
   @Bean
   @Lazy
   public IndexedStorage indexedStorage() {
      return new BlobIndexedStorage();
   }

   /**
    * Security provider — PROTOTYPE scoped so each injection site gets the current provider
    * via the scoped proxy. This replaces SecurityProviderFactory.
    */
   @Bean
   @Lazy
   @Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE, proxyMode = ScopedProxyMode.TARGET_CLASS)
   public SecurityProvider securityProvider(@Lazy SecurityEngine engine) {
      return engine.getSecurityProvider();
   }
}
