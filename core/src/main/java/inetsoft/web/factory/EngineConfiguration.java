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
import inetsoft.analytic.composition.*;
import inetsoft.mv.MVManager;
import inetsoft.mv.MVWorksheetStorage;
import inetsoft.mv.data.MVStorage;
import inetsoft.mv.fs.internal.BlockFileStorage;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.DistributedTableCacheStore;
import inetsoft.report.internal.*;
import inetsoft.report.internal.license.*;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.*;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.uql.XDataService;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.BookmarkLockManager;
import inetsoft.uql.viewsheet.ViewsheetLifecycleMessageChannel;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.util.*;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.SecretsConfig;
import inetsoft.web.cluster.ServerClusterClient;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.lang.Nullable;

import java.rmi.RemoteException;
import java.util.ServiceLoader;

/**
 * Spring configuration for the core engine beans.
 */
@Configuration
public class EngineConfiguration {

   @Bean
   @Lazy
   public LibManagerProvider getLibManagerProvider(@Lazy Cluster cluster, @Lazy BlobStorageManager blobStorageManager, @Lazy SecurityEngine securityEngine) {
      return new LibManagerProvider(cluster, blobStorageManager, securityEngine);
   }

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
   public AnalyticRepository analyticRepository(@Lazy DeployManagerService deployManagerService,
                                                @Lazy DesignSession designSession,
                                                @Lazy LibManagerProvider libManagerProvider,
                                                @Lazy DataCycleManager dataCycleManager,
                                                @Lazy Cluster cluster)
   {
      AnalyticEngine engine = new AnalyticEngine(deployManagerService, designSession, libManagerProvider, dataCycleManager, cluster);
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
   public XRepository xRepository(@Lazy Cluster cluster, @Lazy Config config, @Lazy DataSourceRegistry dataSourceRegistry) {
      return new XEngine(cluster, config, dataSourceRegistry);
   }

   @Bean
   @Lazy
   public ColumnCache columnCache(@Lazy DataSourceRegistry dataSourceRegistry) {
      return new ColumnCache(dataSourceRegistry);
   }

   /**
    * The viewsheet composition engine. Wraps AnalyticRepository.
    */
   @Bean("viewsheetEngine")
   @Lazy
   public ViewsheetService viewsheetService(@Lazy AnalyticRepository analyticRepository, @Lazy ViewsheetLifecycleMessageChannel lifecycleMessageService, @Lazy Cluster cluster) {
      try {
         return new ViewsheetEngine(analyticRepository.unwrap(AssetRepository.class), lifecycleMessageService, cluster);
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
   public WorksheetService worksheetService(@Lazy AnalyticRepository analyticRepository, @Lazy Cluster cluster) {
      try {
         return new WorksheetEngine(analyticRepository.unwrap(AssetRepository.class), cluster);
      }
      catch(RemoteException e) {
         throw new BeanCreationException("worksheetService", "Failed to create WorksheetEngine", e);
      }
   }

   /**
    * Library engine — base class for worksheet composition. No external dependencies.
    */
   @Bean
   @Lazy
   public SheetLibraryService sheetLibraryService() {
      return new SheetLibraryEngine();
   }

   /**
    * Pub/sub channel for viewsheet lifecycle events (open/close/execute).
    */
   @Bean
   @Lazy
   public ViewsheetLifecycleMessageChannel viewsheetLifecycleMessageChannel() {
      return new ViewsheetLifecycleMessageChannel();
   }

   /**
    * Client stub used to submit tasks to the ScheduleServer.
    */
   @Bean
   @Lazy
   public ScheduleClient scheduleClient(InetsoftConfig config, @Lazy @Nullable ScheduleServer scheduleServer, @Lazy Cluster cluster) {
      if(config.getCloudRunner() == null) {
         return new ScheduleClient(cluster);
      }
      else {
         return new CloudRunnerServerScheduleClient(scheduleServer, cluster);
      }
   }

   /**
    * The distributed cluster. In the normal server startup path this wraps the instance
    * pre-created by {@code BaseInetsoftApplication.start()} (before Spring runs). The
    * {@code InetsoftConfig} parameter ensures the config bean is resolved first.
    * {@code destroyMethod=""} prevents Spring from auto-calling {@code close()} — lifecycle
    * is managed by {@code BaseInetsoftApplication.shutdownInetsoft()}.
    */
   @Bean(destroyMethod = "")
   public Cluster cluster(InetsoftConfig config) {
      // Return the instance pre-created by BaseInetsoftApplication.start() before Spring ran.
      return Cluster.getInstance();
   }

   /**
    * Cluster client for server-to-server communication.
    */
   @Bean
   public ServerClusterClient serverClusterClient(Cluster cluster) {
      return new ServerClusterClient(false, cluster);
   }

   /**
    * Plugin manager — loads and manages installed plugins from blob storage.
    */
   @Bean
   @Lazy
   public Plugins plugins(@Lazy BlobStorageManager blobStorageManager, @Lazy Cluster cluster, ApplicationEventPublisher eventPublisher) {
      return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
   }

   /**
    * Session ID counter service.
    */
   @Bean
   @Lazy
   public XSessionService xSessionService() {
      return new XSessionService();
   }

   /**
    * JDBC/tabular driver registry.
    */
   @Bean
   @Lazy
   public Drivers drivers(@Lazy Plugins plugins) {
      return new Drivers(plugins);
   }

   /**
    * Data source registry — manages configured data source definitions.
    */
   @Bean
   @Lazy
   public DataSourceRegistry dataSourceRegistry(@Lazy IndexedStorage indexedStorage, @Lazy Config uqlConfig, @Lazy Cluster cluster) throws Exception {
      return new DataSourceRegistry(indexedStorage, uqlConfig, cluster);
   }

   /**
    * License manager — validates installed license keys and enforces limits.
    */
   @Bean
   @Lazy
   public LicenseManager licenseManager() {
      return new LicenseManager();
   }

   /**
    * Bookmark lock manager — tracks distributed bookmark edit locks.
    */
   @Bean
   @Lazy
   public BookmarkLockManager bookmarkLockManager() {
      return new BookmarkLockManager();
   }

   /**
    * Indexed storage — blob-backed asset store used by RepletEngine and DataCycleManager.
    */
   @Bean
   @Lazy
   public IndexedStorage indexedStorage(@Lazy BlobStorageManager blobStorageManager) {
      return new BlobIndexedStorage(blobStorageManager);
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

   /**
    * Device registry — persists mobile device descriptors in key-value storage.
    */
   @Bean
   @Lazy
   public DeviceRegistry deviceRegistry(@Lazy KeyValueStorageManager keyValueStorageManager) {
      return new DeviceRegistry(keyValueStorageManager);
   }

   /**
    * Asset dependency updater — schedules and runs asset dependency refresh.
    */
   @Bean
   @Lazy
   public UpdateAssetDependenciesHandler updateAssetDependenciesHandler(@Lazy Cluster cluster, @Lazy DataSourceRegistry dataSourceRegistry) {
      return new UpdateAssetDependenciesHandler(cluster, dataSourceRegistry);
   }

   /**
    * MV info client — provides materialized-view refresh timestamps to callers.
    */
   @Bean
   @Lazy
   public MVInfoClient mvInfoClient() {
      return new LocalMVInfoClient();
   }

   /**
    * Elastic license service — loaded via ServiceLoader; falls back to no-op.
    */
   @Bean
   @Lazy
   public ElasticLicenseService elasticLicenseService() {
      try {
         return ServiceLoader.load(ElasticLicenseService.class).iterator().next();
      }
      catch(Exception e) {
         return new NoopElasticLicenseService();
      }
   }

   /**
    * Hosted license service — loaded via ServiceLoader; falls back to no-op.
    */
   @Bean
   @Lazy
   public HostedLicenseService hostedLicenseService() {
      try {
         return ServiceLoader.load(HostedLicenseService.class).iterator().next();
      }
      catch(Exception e) {
         return new NoopHostedLicenseService();
      }
   }

   /**
    * Materialized-view manager — tracks MV definitions and their refresh state.
    */
   @Bean
   @Lazy
   public MVManager mvManager(@Lazy Cluster cluster) {
      return new MVManager(cluster);
   }

   /**
    * Materialized-view worksheet storage — persists MV worksheet definitions.
    */
   @Bean
   @Lazy
   public MVWorksheetStorage mvWorksheetStorage(@Lazy BlobStorageManager blobStorageManager) {
      return new MVWorksheetStorage(blobStorageManager);
   }

   /**
    * Materialized-view data storage — manages MV data files in blob storage.
    */
   @Bean
   @Lazy
   public MVStorage mvStorage(@Lazy BlobStorageManager blobStorageManager) {
      return new MVStorage(blobStorageManager);
   }

   /**
    * Distributed table cache store — blob-backed cross-node query result cache.
    */
   @Bean
   @Lazy
   public DistributedTableCacheStore distributedTableCacheStore(@Lazy Cluster cluster, @Lazy BlobStorageManager blobStorageManager) {
      return new DistributedTableCacheStore(cluster, blobStorageManager);
   }

   /**
    * In-process asset data cache — caches query results for the local node.
    */
   @Bean
   @Lazy
   public AssetDataCache assetDataCache(@Lazy DataSourceRegistry dataSourceRegistry) {
      return new AssetDataCache(dataSourceRegistry);
   }

   /**
    * UQL configuration — holds data source connection defaults and query limits.
    */
   @Bean
   @Lazy
   public Config config(@Lazy Plugins plugins) {
      return new Config(plugins);
   }

   /**
    * Embedded table storage — persists embedded table data in blob storage.
    */
   @Bean
   @Lazy
   public EmbeddedTableStorage embeddedTableStorage(@Lazy BlobStorageManager blobStorageManager) {
      return new EmbeddedTableStorage(blobStorageManager);
   }

   /**
    * Asset dependency handler — routes rename/delete dependency operations to the local impl.
    */
   @Bean
   @Lazy
   public DependencyHandler dependencyHandler() {
      return new LocalDependencyHandler();
   }

   /**
    * Block file storage — manages raw MV block files in blob storage.
    */
   @Bean
   @Lazy
   public BlockFileStorage blockFileStorage(@Lazy BlobStorageManager blobStorageManager) {
      return new BlockFileStorage(blobStorageManager);
   }

   /**
    * Session manager for query execution. Initialized at startup via {@code @PostConstruct}.
    * {@code @Primary} resolves ambiguity when {@code XSessionManager.class} is requested
    * (DesignSession also extends XSessionManager).
    */
   @Bean
   @Lazy
   @Primary
   public XSessionManager xSessionManager(@Lazy XDataService dataService, @Lazy XSessionService sessionService, @Lazy DataSourceRegistry dataSourceRegistry) throws RemoteException {
      return new XSessionManager(dataService, sessionService, dataSourceRegistry);
   }

   /**
    * Design session — XSessionManager variant used by AnalyticEngine for design-time queries.
    */
   @Bean
   @Lazy
   public DesignSession designSession(@Lazy XDataService dataService, @Lazy XSessionService sessionService, @Lazy DataSourceRegistry dataSourceRegistry) throws RemoteException {
      return new DesignSession(dataService, sessionService, dataSourceRegistry);
   }

   /**
    * Password encryption service — loaded via ServiceLoader keyed by the configured
    * secrets type (local, AWS Secrets Manager, Vault, etc.).
    */
   @Bean
   @Lazy
   public PasswordEncryption passwordEncryption(InetsoftConfig inetsoftConfig) {
      SecretsConfig secretsConfig = inetsoftConfig.getSecrets();
      String type = secretsConfig.getType();

      for(PasswordEncryptionFactory factory : ServiceLoader.load(PasswordEncryptionFactory.class)) {
         if(factory.getType().equals(type)) {
            return factory.createPasswordEncryption(secretsConfig);
         }
      }

      throw new BeanCreationException("passwordEncryption",
                                      "No PasswordEncryptionFactory found for type: " + type);
   }

}
