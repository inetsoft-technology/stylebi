/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.test;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.DistributedTableCacheStore;
import inetsoft.report.internal.DesignSession;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.*;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.BlobStorageManager;
import inetsoft.uql.XDataService;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.BookmarkLockManager;
import inetsoft.uql.viewsheet.ViewsheetLifecycleMessageChannel;
import inetsoft.util.*;
import inetsoft.util.log.LogManager;
import inetsoft.util.swap.XSwapper;
import inetsoft.web.binding.drm.*;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.service.DataRefModelFactory;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.service.LicenseService;
import inetsoft.web.viewsheet.controller.*;
import inetsoft.web.viewsheet.controller.table.*;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.model.annotation.VSAnnotationModel;
import inetsoft.web.viewsheet.model.calendar.VSCalendarModel;
import inetsoft.web.viewsheet.model.chart.VSChartModel;
import inetsoft.web.viewsheet.model.table.*;
import inetsoft.web.viewsheet.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.GenericMessage;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Spring test configuration providing all viewsheet controller and service beans needed by
 * integration tests that open and interact with runtime viewsheets.
 */
@Configuration
public class IntegrationTestConfiguration {
   @Bean("viewsheetEngine")
   public ViewsheetService viewsheetService(AnalyticRepository engine, Cluster cluster) throws Exception {
      return new ViewsheetEngine(engine.unwrap(AssetRepository.class), mock(ViewsheetLifecycleMessageChannel.class), cluster);
   }

   @Bean("worksheetService")
   @Primary
   public WorksheetService worksheetService(AnalyticRepository engine, Cluster cluster) throws Exception {
      return new WorksheetEngine(engine.unwrap(AssetRepository.class), cluster);
   }

   @Bean
   public RuntimeViewsheetRefService runtimeViewsheetRefService(ViewsheetService viewsheetService) {
      return new RuntimeViewsheetRefService(viewsheetService);
   }

   @Bean
   public RuntimeViewsheetRef runtimeViewsheetRef(Cluster cluster, WorksheetService worksheetService, RuntimeViewsheetRefService runtimeViewsheetRefService) throws Exception {
      // RuntimeViewsheetRef constructor needs MessageContextHolder to be set
      GenericMessage<String> message = new GenericMessage<>("test");
      MessageAttributes messageAttributes = new MessageAttributes(message);
      MessageContextHolder.setMessageAttributes(messageAttributes);

      try {
         // Override get/set to use a simple field rather than the message-scoped MessageContextHolder
         return new RuntimeViewsheetRef(new RuntimeViewsheetRefServiceProxy(cluster, worksheetService, runtimeViewsheetRefService)) {
            private String runtimeId;

            @Override
            public String getRuntimeId() {
               return runtimeId;
            }

            @Override
            public void setRuntimeId(String runtimeId) {
               this.runtimeId = runtimeId;
            }
         };
      }
      finally {
         MessageContextHolder.setMessageAttributes(null);
      }
   }

   @Bean
   public RuntimeViewsheetManager runtimeViewsheetManager(ViewsheetService viewsheetService) {
      return new RuntimeViewsheetManager(viewsheetService, Cluster.getInstance());
   }

   @Bean
   public VSObjectModelFactoryService objectModelFactoryService() {
      List<VSObjectModelFactory<?, ?>> modelFactories = Arrays.asList(
         new VSCalcTableModel.VSCalcTableModelFactory(),
         new VSCheckBoxModel.VSCheckBoxModelFactory(),
         new VSComboBoxModel.VSComboBoxModelFactory(),
         new VSCylinderModel.VSCylinderModelFactory(),
         new VSEmbeddedTableModel.VSEmbeddedTableModelFactory(),
         new VSGaugeModel.VSGaugeModelFactory(),
         new VSImageModel.VSImageModelFactory(),
         new VSLineModel.VSLineModelFactory(),
         new VSOvalModel.VSOvalModelFactory(),
         new VSRadioButtonModel.VSRadioButtonModelFactory(),
         new VSRangeSliderModel.VSRangeSliderModelFactory(),
         new VSRectangleModel.VSRectangleModelFactory(),
         new VSSelectionContainerModel.VSSelectionContainerModelFactory(),
         new VSSelectionListModel.VSSelectionListModelFactory(),
         new VSSelectionTreeModel.VSSelectionTreeModelFactory(),
         new VSSliderModel.VSSliderModelFactory(),
         new VSSlidingScaleModel.VSThermometerModelFactory(),
         new VSSpinnerModel.VSSpinnerModelFactory(),
         new VSSubmitModel.VSSubmitModelFactory(),
         new VSTableModel.VSTableModelFactory(),
         new VSTableModel.VSTableModelFactory(),
         new VSTextInputModel.VSTextInputModelFactory(),
         new VSTextModel.VSTextModelFactory(),
         new VSThermometerModel.VSThermometerModelFactory(),
         new VSViewsheetModel.VSViewsheetModelFactory(),
         new VSAnnotationModel.VSAnnotationModelFactory(),
         new VSCalendarModel.VSCalendarModelFactory(),
         new VSChartModel.VSChartModelFactory()
      );
      return new VSObjectModelFactoryService(modelFactories);
   }

   @Bean
   public VSLayoutService vsLayoutService(VSObjectModelFactoryService objectModelFactoryService) {
      return new VSLayoutService(objectModelFactoryService);
   }

   @Bean
   public ParameterService parameterService(ViewsheetService viewsheetService) {
      return new ParameterService(viewsheetService);
   }

   @Bean
   public VSCompositionService vsCompositionService() {
      return new VSCompositionService();
   }

   @Bean
   public DataRefModelFactoryService dataRefModelFactoryService() {
      List<DataRefModelFactory<?, ?>> dataRefModelFactories = Arrays.asList(
         new AggregateRefModel.AggregateRefModelFactory(),
         new AliasDataRefModel.AliasDataRefModelFactory(),
         new AttributeRefModel.AttributeRefModelFactory(),
         new BaseFieldModel.BaseFieldModelFactory(),
         new CalculateRefModel.CalculateRefModelFactory(),
         new ColumnRefModel.ColumnRefModelFactory(),
         new FormRefModel.FormRefModelFactory(),
         new FormulaFieldModel.FormulaFieldModelFactory(),
         new BAggregateRefModel.VSAggregateRefModelFactory(),
         new BDimensionRefModel.VSDimensionRefModelFactory(),
         new DateRangeRefModel.DateRangeRefModelFactory(),
         new ExpressionRefModel.ExpressionRefModelFactory(),
         new GroupRefModel.GroupRefModelFactory(),
         new NumericRangeRefModel.NumericRangeRefModelFactory()
      );
      return new DataRefModelFactoryService(dataRefModelFactories);
   }

   @Bean
   public CoreLifecycleService coreLifecycleService(
      VSObjectModelFactoryService objectModelFactoryService,
      ViewsheetService viewsheetService,
      VSLayoutService vsLayoutService,
      ParameterService parameterService,
      VSCompositionService vsCompositionService,
      DataRefModelFactoryService dataRefModelFactoryService,
      RuntimeViewsheetRef runtimeViewsheetRef,
      SecurityEngine securityEngine,
      Cluster cluster,
      DataSourceRegistry dataSourceRegistry,
      LicenseManager licenseManager,
      ApplicationEventPublisher eventPublisher)
   {
      return new CoreLifecycleService(
         objectModelFactoryService, viewsheetService, vsLayoutService, parameterService,
         vsCompositionService, dataRefModelFactoryService, runtimeViewsheetRef, eventPublisher,
//         event -> {
//            if(event instanceof ProcessBookmarkEvent pbe) {
//               VSBookmarkService bookmarkService = bookmarkServiceProvider.getIfAvailable();
//
//               if(bookmarkService != null) {
//                  bookmarkService.onApplicationEvent(pbe);
//               }
//            }
//         },
         licenseManager, securityEngine, cluster, dataSourceRegistry);
   }

   @Bean
   public LibManagerProvider libManagerProvider(Cluster cluster, BlobStorageManager blobStorageManager, SecurityEngine securityEngine) {
      return new LibManagerProvider(cluster, blobStorageManager, securityEngine);
   }

   @Bean
   public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster, ApplicationEventPublisher eventPublisher) {
      return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
   }

   @Bean
   public Drivers drivers(Plugins plugins) {
      return new Drivers(plugins);
   }

   @Bean
   public Config config(Plugins plugins) {
      return new Config(plugins);
   }

   @Bean
   public IndexedStorage indexedStorage(BlobStorageManager blobStorageManager) {
      return new BlobIndexedStorage(blobStorageManager);
   }

   @Bean
   public DataSourceRegistry dataSourceRegistry(IndexedStorage indexedStorage, Config config, Cluster cluster) throws Exception {
      return new DataSourceRegistry(indexedStorage, config, cluster);
   }

   @Bean
   public XRepository xRepository(Cluster cluster, Config config, DataSourceRegistry dataSourceRegistry) {
      return new XEngine(cluster, config, dataSourceRegistry);
   }

   @Bean
   public EmbeddedTableStorage embeddedTableStorage(BlobStorageManager blobStorageManager) {
      return new EmbeddedTableStorage(blobStorageManager);
   }

   @Bean
   public DeployManagerService deployManagerService(SecurityEngine securityEngine, DataSourceRegistry dataSourceRegistry, LibManagerProvider libManagerProvider, XRepository xRepository, FileSystemService fileSystemService, DataSpace dataSpace, EmbeddedTableStorage embeddedTableStorage) {
      return new DeployManagerService(securityEngine, mock(DependencyHandler.class), dataSourceRegistry, mock(DashboardRegistryManager.class), libManagerProvider, mock(DashboardManager.class), xRepository, fileSystemService, dataSpace, embeddedTableStorage);
   }

   @Bean
   public DesignSession designSession(XDataService dataService, XSessionService sessionService, DataSourceRegistry dataSourceRegistry) throws Exception {
      return new DesignSession(dataService, sessionService, dataSourceRegistry);
   }

   @Bean
   public AnalyticRepository analyticRepository(DeployManagerService deployManagerService, DesignSession designSession, LibManagerProvider libManagerProvider, Cluster cluster) {
      AnalyticEngine engine = new AnalyticEngine(deployManagerService, designSession, libManagerProvider, mock(DataCycleManager.class), cluster);
      AssetUtil.setAssetRepository(false, engine);
      engine.init();
      return engine;
   }

   @Bean
   public VSObjectTreeService objectTreeService(VSObjectModelFactoryService objectModelFactoryService) {
      return new VSObjectTreeService(objectModelFactoryService);
   }

   @Bean
   public SharedFilterService sharedFilterService(ViewsheetService viewsheetService) {
      SimpMessagingTemplate messagingTemplate = new SimpMessagingTemplate(new MessageChannel() {
         @Override
         public boolean send(Message<?> message) {
            return true;
         }

         @Override
         public boolean send(Message<?> message, long timeout) {
            return true;
         }
      });
      return new SharedFilterService(messagingTemplate, viewsheetService);
   }

   @Bean
   public VSObjectService objectService(CoreLifecycleService coreLifecycleService,
                                        ViewsheetService viewsheetService,
                                        SecurityEngine securityEngine,
                                        SharedFilterService sharedFilterService)
   {
      return new VSObjectService(coreLifecycleService, viewsheetService, securityEngine,
                                 sharedFilterService);
   }

   @Bean
   public VSBookmarkService bookmarkService(VSObjectService objectService,
                                            ViewsheetService viewsheetService,
                                            SecurityEngine securityEngine,
                                            Cluster cluster)
   {
      return new VSBookmarkService(objectService, viewsheetService, securityEngine,
                                   null, cluster, null);
   }

   @Bean
   public XSessionService xSessionService() {
      return new XSessionService();
   }

   @Bean
   public VSLifecycleControllerService vsLifecycleControllerService(ViewsheetService viewsheetService, CoreLifecycleService coreLifecycleService, VSBookmarkService vsBookmarkService) {
      return new VSLifecycleControllerService(viewsheetService, coreLifecycleService, vsBookmarkService);
   }

   @Bean
   public VSLifecycleService vsLifecycleService(ViewsheetService viewsheetService,
                                                AnalyticRepository assetRepository,
                                                CoreLifecycleService coreLifecycleService,
                                                ParameterService parameterService,
                                                LogManager logManager,
                                                XSessionService sessionService,
                                                Cluster cluster,
                                                WorksheetService worksheetService,
                                                VSLifecycleControllerService vsLifecycleControllerService,
                                                RuntimeViewsheetRefService runtimeViewsheetRefService)
   {
      return new VSLifecycleService(viewsheetService, assetRepository.unwrap(AssetRepository.class), coreLifecycleService,
                                    parameterService, new VSLifecycleControllerServiceProxy(cluster, worksheetService, vsLifecycleControllerService),
                                    logManager, sessionService, cluster, worksheetService, runtimeViewsheetRefService);
   }

   @Bean
   public ViewsheetControllerService viewsheetControllerService(RuntimeViewsheetManager runtimeViewsheetManager, ViewsheetService viewsheetService) {
      return new ViewsheetControllerService(runtimeViewsheetManager, viewsheetService);
   }

   @Bean
   public ViewsheetController viewsheetController(RuntimeViewsheetRef runtimeViewsheetRef, Cluster cluster, WorksheetService worksheetService, ViewsheetControllerService viewsheetControllerService) {
      return new ViewsheetController(runtimeViewsheetRef, new ViewsheetControllerServiceProxy(cluster, worksheetService, viewsheetControllerService));
   }

   @Bean
   public LicenseService licenseService(LicenseManager licenseManager) {
      return new LicenseService(licenseManager);
   }

   @Bean
   public OpenViewsheetService openViewsheetService(ViewsheetService viewsheetService, VSObjectTreeService vsObjectTreeService) {
      return new OpenViewsheetService(viewsheetService, vsObjectTreeService);
   }

   @Bean
   public OpenViewsheetController openViewsheetController(RuntimeViewsheetRef runtimeViewsheetRef,
                                                          RuntimeViewsheetManager runtimeViewsheetManager,
                                                          VSLifecycleService vsLifecycleService,
                                                          LicenseService licenseService,
                                                          ViewsheetService viewsheetService,
                                                          SecurityEngine securityEngine,
                                                          Cluster cluster,
                                                          WorksheetService worksheetService,
                                                          OpenViewsheetService openViewsheetService)
   {
      return new OpenViewsheetController(runtimeViewsheetRef, runtimeViewsheetManager,
                                         vsLifecycleService, licenseService,
                                         new OpenViewsheetServiceProxy(cluster, worksheetService, openViewsheetService), viewsheetService, securityEngine);
   }

   @Bean
   public BaseTableLoadDataService baseTableLoadDataService(CoreLifecycleService coreLifecycleService, ViewsheetService viewsheetService) {
      return new BaseTableLoadDataService(coreLifecycleService, viewsheetService);
   }

   @Bean
   public BaseTableLoadDataController baseTableLoadDataController(RuntimeViewsheetRef runtimeViewsheetRef, Cluster cluster, WorksheetService worksheetService, BaseTableLoadDataService baseTableLoadDataService) {
      return new BaseTableLoadDataController(runtimeViewsheetRef,
                                             new BaseTableLoadDataServiceProxy(cluster, worksheetService, baseTableLoadDataService));
   }

   @Bean
   public MaxModeAssemblyService maxModeAssemblyService(ViewsheetService viewsheetService,
                                                        CoreLifecycleService coreLifecycleService)
   {
      return new MaxModeAssemblyService(viewsheetService, coreLifecycleService);
   }

   @Bean
   public VSSelectionService selectionService(CoreLifecycleService coreLifecycleService,
                                              ViewsheetService viewsheetService,
                                              MaxModeAssemblyService maxModeAssemblyService,
                                              SharedFilterService sharedFilterService)
   {
      return new VSSelectionService(coreLifecycleService, viewsheetService, maxModeAssemblyService,
                                    sharedFilterService);
   }

   @Bean
   public VSSelectionServiceProxy selectionServiceProxy(Cluster cluster, WorksheetService worksheetService, VSSelectionService vsSelectionService) {
      return new VSSelectionServiceProxy(cluster, worksheetService, vsSelectionService);
   }

   @Bean
   @ConditionalOnMissingBean
   public XSwapper getXSwapper() {
      return new XSwapper();
   }

   @Bean
   @ConditionalOnMissingBean
   public DataCacheSweeper getDataCacheSweeper(XSwapper swapper) {
      return new DataCacheSweeper(swapper);
   }

   @Bean
   public ScheduleManager scheduleManager(SecurityEngine securityEngine, Cluster cluster) {
      return new ScheduleManager(securityEngine, cluster, mock(ScheduleClient.class), mock(DependencyHandler.class));
   }

   @Bean
   public ScheduleStatusDao scheduleStatusDao() {
      return mock(ScheduleStatusDao.class);
   }

   @Bean
   public AssetDataCache assetDataCache(DataSourceRegistry dataSourceRegistry) {
      return new AssetDataCache(dataSourceRegistry);
   }

   @Bean
   public MVManager mvManager(Cluster cluster) {
      return new MVManager(cluster);
   }

   @Bean
   public BookmarkLockManager bookmarkLockManager() {
      return new BookmarkLockManager();
   }

   @Bean
   public DistributedTableCacheStore distributedTableCacheStore(Cluster cluster, BlobStorageManager blobStorageManager) {
      return new DistributedTableCacheStore(cluster, blobStorageManager);
   }

   @Bean
   public AnalyticAssistant analyticAssistant(AnalyticRepository analyticRepository) {
      return new AnalyticAssistant(analyticRepository);
   }
}
