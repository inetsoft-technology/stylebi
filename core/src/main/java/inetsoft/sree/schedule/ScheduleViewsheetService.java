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

package inetsoft.sree.schedule;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.sree.RepletRequest;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.*;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.model.annotation.VSAnnotationModel;
import inetsoft.web.viewsheet.model.calendar.VSCalendarModel;
import inetsoft.web.viewsheet.model.chart.VSChartModel;
import inetsoft.web.viewsheet.model.table.*;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;

@SingletonManager.Singleton
public class ScheduleViewsheetService {
   /**
    * Get the schedule viewsheet service.
    */
   public static ScheduleViewsheetService getInstance() {
      return SingletonManager.getInstance(ScheduleViewsheetService.class);
   }

   public ScheduleViewsheetService() {
      this.engine = ViewsheetEngine.getViewsheetEngine();
      this.coreLifecycleService = createCoreLifecycleService();
   }

   /**
    * Only for use within the scheduler
    */
   private CoreLifecycleService createCoreLifecycleService() {
      List<VSObjectModelFactory<?, ?>> modelFactories = Arrays.asList(
         new VSAnnotationModel.VSAnnotationModelFactory(),
         new VSCalcTableModel.VSCalcTableModelFactory(),
         new VSCalendarModel.VSCalendarModelFactory(),
         new VSChartModel.VSChartModelFactory(),
         new VSCheckBoxModel.VSCheckBoxModelFactory(),
         new VSComboBoxModel.VSComboBoxModelFactory(),
         new VSCrosstabModel.VSCrosstabModelFactory(),
         new VSCylinderModel.VSCylinderModelFactory(),
         new VSEmbeddedTableModel.VSEmbeddedTableModelFactory(),
         new VSGaugeModel.VSGaugeModelFactory(),
         new VSGroupContainerModel.VSGaugeModelFactory(),
         new VSImageModel.VSImageModelFactory(),
         new VSLineModel.VSLineModelFactory(),
         new VSOvalModel.VSOvalModelFactory(),
         new VSPageBreakModel.VSPageBreakModelFactory(),
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
         new VSTabModel.VSTabModelFactory(),
         new VSTextInputModel.VSTextInputModelFactory(),
         new VSTextModel.VSTextModelFactory(),
         new VSThermometerModel.VSThermometerModelFactory(),
         new VSViewsheetModel.VSViewsheetModelFactory()
      );
      VSObjectModelFactoryService objectModelFactoryService =
         new VSObjectModelFactoryService(modelFactories);
      VSLayoutService vsLayoutService = new VSLayoutService(objectModelFactoryService);
      ParameterService parameterService = new ParameterService(engine);
      return new CoreLifecycleService(objectModelFactoryService, engine, vsLayoutService,
                                      parameterService);
   }

   public String openViewsheet(AssetEntry entry, RepletRequest repletRequest, Principal principal)
      throws Exception
   {
      OpenViewsheetEvent openViewsheetEvent = new OpenViewsheetEvent();
      openViewsheetEvent.setViewer(true);
      openViewsheetEvent.setEntryId(entry.toIdentifier());
      openViewsheetEvent.setUserAgent(null);

      VariableTable vt = buildParameters(repletRequest);
      String execSessionId =
         XSessionService.createSessionID(XSessionService.EXPORE_VIEW, entry.getName());

      return CommandDispatcher.withDummyDispatcher(principal, d -> coreLifecycleService.openViewsheet(
         engine, openViewsheetEvent, principal, null, null, entry, d, null,
         null, true, openViewsheetEvent.getDrillFrom(), vt,
         openViewsheetEvent.getFullScreenId(), execSessionId));
   }

   public void closeViewsheet(String runtimeId, Principal principal) {
      if(runtimeId != null) {
         try {
            engine.closeViewsheet(runtimeId, principal);
         }
         catch(Exception ex) {
            LOG.error("Failed to close viewsheet: " + runtimeId, ex);
         }
      }
   }

   /**
    * Build parameters by {@link RepletRequest}
    */
   private VariableTable buildParameters(RepletRequest repletRequest) {
      ItemMap params = new ItemMap();
      Enumeration<?> names = repletRequest.getParameterNames();

      while(names.hasMoreElements()) {
         String paramName = (String) names.nextElement();
         Object obj = repletRequest.getParameter(paramName) == null ?
            null : repletRequest.getParameter(paramName);
         String value = Tool.encodeParameter(obj);
         params.putItem(paramName, value);
      }

      return VSEventUtil.decodeParameters(params);
   }

   private final ViewsheetService engine;
   private final CoreLifecycleService coreLifecycleService;
   private final static Logger LOG = LoggerFactory.getLogger(ScheduleViewsheetService.class);
}
