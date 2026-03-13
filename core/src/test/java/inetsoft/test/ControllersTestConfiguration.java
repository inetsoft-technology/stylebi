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

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetRepository;
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
import inetsoft.web.viewsheet.controller.table.BaseTableLoadDataController;
import inetsoft.web.viewsheet.controller.table.BaseTableLoadDataServiceProxy;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.model.annotation.VSAnnotationModel;
import inetsoft.web.viewsheet.model.calendar.VSCalendarModel;
import inetsoft.web.viewsheet.model.chart.VSChartModel;
import inetsoft.web.viewsheet.model.table.*;
import inetsoft.web.viewsheet.service.*;
import inetsoft.sree.internal.cluster.Cluster;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.GenericMessage;

import java.util.Arrays;
import java.util.List;

/**
 * Spring test configuration providing all viewsheet controller and service beans needed by
 * integration tests that open and interact with runtime viewsheets.
 */
@Configuration
public class ControllersTestConfiguration {
   @Bean
   public ViewsheetService viewsheetService() {
      return ViewsheetEngine.getViewsheetEngine();
   }

   @Bean
   public WorksheetService worksheetService() {
      return WorksheetEngine.getWorksheetService();
   }

   @Bean
   public RuntimeViewsheetRef runtimeViewsheetRef() {
      // RuntimeViewsheetRef constructor needs MessageContextHolder to be set
      GenericMessage<String> message = new GenericMessage<>("test");
      MessageAttributes messageAttributes = new MessageAttributes(message);
      MessageContextHolder.setMessageAttributes(messageAttributes);

      try {
         // Override get/set to use a simple field rather than the message-scoped MessageContextHolder
         return new RuntimeViewsheetRef(new RuntimeViewsheetRefServiceProxy()) {
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
      ObjectProvider<VSBookmarkService> bookmarkServiceProvider)
   {
      return new CoreLifecycleService(
         objectModelFactoryService, viewsheetService, vsLayoutService, parameterService,
         vsCompositionService, dataRefModelFactoryService, runtimeViewsheetRef,
         event -> {
            if(event instanceof ProcessBookmarkEvent pbe) {
               VSBookmarkService bookmarkService = bookmarkServiceProvider.getIfAvailable();

               if(bookmarkService != null) {
                  bookmarkService.onApplicationEvent(pbe);
               }
            }
         },
         null, null, null);
   }

   @Bean
   public AssetRepository assetRepository() {
      return (AssetRepository) SUtil.getRepletRepository();
   }

   @Bean
   public VSObjectTreeService objectTreeService(VSObjectModelFactoryService objectModelFactoryService) {
      return new VSObjectTreeService(objectModelFactoryService);
   }

   @Bean
   public SecurityEngine securityEngine() {
      return SecurityEngine.getSecurity();
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
                                            CoreLifecycleService coreLifecycleService)
   {
      return new VSBookmarkService(objectService, viewsheetService, securityEngine,
                                   coreLifecycleService, null, null);
   }

   @Bean
   public VSLifecycleService vsLifecycleService(ViewsheetService viewsheetService,
                                                 AssetRepository assetRepository,
                                                 CoreLifecycleService coreLifecycleService,
                                                 ParameterService parameterService)
   {
      return new VSLifecycleService(viewsheetService, assetRepository, coreLifecycleService,
                                    parameterService, new VSLifecycleControllerServiceProxy(), null);
   }

   @Bean
   public ViewsheetController viewsheetController(RuntimeViewsheetRef runtimeViewsheetRef) {
      return new ViewsheetController(runtimeViewsheetRef, new ViewsheetControllerServiceProxy());
   }

   @Bean
   public LicenseService licenseService() {
      return new LicenseService(null);
   }

   @Bean
   public OpenViewsheetController openViewsheetController(RuntimeViewsheetRef runtimeViewsheetRef,
                                                           RuntimeViewsheetManager runtimeViewsheetManager,
                                                           VSLifecycleService vsLifecycleService,
                                                           LicenseService licenseService,
                                                           ViewsheetService viewsheetService)
   {
      return new OpenViewsheetController(runtimeViewsheetRef, runtimeViewsheetManager,
                                         vsLifecycleService, licenseService,
                                         new OpenViewsheetServiceProxy(), viewsheetService, null);
   }

   @Bean
   public BaseTableLoadDataController baseTableLoadDataController(RuntimeViewsheetRef runtimeViewsheetRef) {
      return new BaseTableLoadDataController(runtimeViewsheetRef,
                                             new BaseTableLoadDataServiceProxy());
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
   public VSSelectionServiceProxy selectionServiceProxy() {
      return new VSSelectionServiceProxy();
   }
}
