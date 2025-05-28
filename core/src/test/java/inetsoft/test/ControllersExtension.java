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
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Arrays;
import java.util.List;

public class ControllersExtension extends MockMessageExtension {
   @Override
   public void beforeEach(ExtensionContext context) {
      mockMessage(this::createControllers);
   }

   @Override
   public void afterEach(ExtensionContext context) {
      viewsheetService = null;
      vsLifecycleService = null;
      runtimeViewsheetManager = null;
      objectModelFactoryService = null;
      coreLifecycleService = null;
      viewsheetController = null;
      objectTreeService = null;
      securityEngine = null;
      objectService = null;
      bookmarkService = null;
      dataRefModelFactoryService = null;
      assetRepository = null;
      openViewsheetController = null;
      selectionService = null;
   }

   private void createControllers() {
      viewsheetService = ViewsheetEngine.getViewsheetEngine();
      worksheetService = WorksheetEngine.getWorksheetService();

      runtimeViewsheetRef = new RuntimeViewsheetRef(viewsheetService) {
         @Override
         public String getRuntimeId() {
            return ControllersExtension.this.runtimeId;
         }

         @Override
         public void setRuntimeId(String runtimeId) {
            ControllersExtension.this.runtimeId = runtimeId;
         }
      };

      runtimeViewsheetManager = new RuntimeViewsheetManager(viewsheetService, worksheetService);
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
      objectModelFactoryService = new VSObjectModelFactoryService(modelFactories);
      VSLayoutService vsLayoutService = new VSLayoutService(objectModelFactoryService);
      ParameterService parameterService = new ParameterService(viewsheetService);
      coreLifecycleService =
         new CoreLifecycleService(objectModelFactoryService, viewsheetService,
                                  vsLayoutService, parameterService);
      BaseTableLoadDataServiceProxy tableLoadDataServiceProxy = new BaseTableLoadDataServiceProxy();
      assetRepository = (AssetRepository) SUtil.getRepletRepository();
      objectTreeService = new VSObjectTreeService(objectModelFactoryService);
      securityEngine = SecurityEngine.getSecurity();
      sharedFilterService = new SharedFilterService(getMessagingTemplate(), viewsheetService);

      objectService = new VSObjectService(coreLifecycleService, viewsheetService, securityEngine,
                                          sharedFilterService);
      bookmarkService = new VSBookmarkService(objectService, viewsheetService, securityEngine);
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
      dataRefModelFactoryService = new DataRefModelFactoryService(dataRefModelFactories);
      vsCompositionService = new VSCompositionService();
      vsLifecycleService = new VSLifecycleService(
         viewsheetService, assetRepository, coreLifecycleService, bookmarkService,
         dataRefModelFactoryService, vsCompositionService, parameterService);
      viewsheetController = new ViewsheetController(runtimeViewsheetRef, new ViewsheetControllerServiceProxy());
      licenseService = new LicenseService();
      openViewsheetController = new OpenViewsheetController(
         runtimeViewsheetRef, runtimeViewsheetManager, vsLifecycleService, licenseService,
         new OpenViewsheetServiceProxy(), viewsheetService);
      baseTableLoadDataController =
         new BaseTableLoadDataController(runtimeViewsheetRef, tableLoadDataServiceProxy);
      selectionService = new VSSelectionService(coreLifecycleService, viewsheetService,
                                                maxModeAssemblyService, sharedFilterService);
      selectionServiceProxy = new VSSelectionServiceProxy();
      maxModeAssemblyService = new MaxModeAssemblyService(viewsheetService, coreLifecycleService);
   }

   @Override
   public String getRuntimeId() {
      return runtimeId;
   }

   public RuntimeViewsheetRef getRuntimeViewsheetRef() {
      return runtimeViewsheetRef;
   }

   public ViewsheetService getViewsheetService() {
      return viewsheetService;
   }

   public RuntimeViewsheetManager getRuntimeViewsheetManager() {
      return runtimeViewsheetManager;
   }

   public VSObjectModelFactoryService getObjectModelFactoryService() {
      return objectModelFactoryService;
   }

   public CoreLifecycleService getCoreLifecycleService() {
      return coreLifecycleService;
   }

   public ViewsheetController getViewsheetController() {
      return viewsheetController;
   }

   public VSObjectTreeService getObjectTreeService() {
      return objectTreeService;
   }

   public SecurityEngine getSecurityEngine() {
      return securityEngine;
   }

   public VSObjectService getObjectService() {
      return objectService;
   }

   public VSBookmarkService getBookmarkService() {
      return bookmarkService;
   }

   public DataRefModelFactoryService getDataRefModelFactoryService() {
      return dataRefModelFactoryService;
   }

   public AssetRepository getAssetRepository() {
      return assetRepository;
   }

   public OpenViewsheetController getOpenViewsheetController() {
      return openViewsheetController;
   }

   public BaseTableLoadDataController getBaseTableLoadDataController() {
      return baseTableLoadDataController;
   }

   public VSSelectionService getVSSelectionService() {
      return selectionService;
   }

   public VSSelectionServiceProxy getVSSelectionServiceProxy() {
      return selectionServiceProxy;
   }

   public SharedFilterService getSharedFilterService() {
      return sharedFilterService;
   }

   public VSCompositionService getVsCompositionService() {
      return vsCompositionService;
   }

   public ParameterService getParameterService() {
      return parameterService;
   }

   private String runtimeId;
   private RuntimeViewsheetRef runtimeViewsheetRef;
   private ViewsheetService viewsheetService;
   private WorksheetService worksheetService;
   private VSLifecycleService vsLifecycleService;
   private RuntimeViewsheetManager runtimeViewsheetManager;
   private VSObjectModelFactoryService objectModelFactoryService;
   private CoreLifecycleService coreLifecycleService;
   private BaseTableLoadDataServiceProxy tableLoadDataServiceProxy;
   private ViewsheetController viewsheetController;
   private VSObjectTreeService objectTreeService;
   private SecurityEngine securityEngine;
   private VSObjectService objectService;
   private VSBookmarkService bookmarkService;
   private DataRefModelFactoryService dataRefModelFactoryService;
   private AssetRepository assetRepository;
   private OpenViewsheetController openViewsheetController;
   private BaseTableLoadDataController baseTableLoadDataController;
   private LicenseService licenseService;
   private VSSelectionService selectionService;
   private VSSelectionServiceProxy selectionServiceProxy;
   private MaxModeAssemblyService maxModeAssemblyService;
   private SharedFilterService sharedFilterService;
   private VSCompositionService vsCompositionService;
   private ParameterService parameterService;
}
