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

package inetsoft.web.vswizard.controller;

import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.vswizard.model.VSWizardOriginalModel;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.Serializable;
import java.security.Principal;
import java.util.Optional;

@Service
@ClusterProxy
public class VSWizardObjectToolbarService {

   public VSWizardObjectToolbarService(VSWizardTemporaryInfoService temporaryInfoService,
                                       ViewsheetService viewsheetService)
   {
      this.viewsheetService = viewsheetService;
      this.temporaryInfoService = temporaryInfoService;
   }


   /**
    * Prepares the assembly the full editor opens.
    *
    * @param id           the runtime viewsheet id.
    * @param assemblyName the name of the preview assembly shown on the client, or {@code null}
    *                     if the preview has not reached the client yet.
    * @param principal    the current user.
    *
    * @return the name and object type of the assembly the full editor opens, or {@code null}
    *         if there is none.
    */
   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public FullEditorAssembly gotoFullEditor(@ClusterProxyKey String id,
      @RequestParam(value = "assemblyName", required = false) String assemblyName,
      Principal principal)
      throws Exception
   {

      RuntimeViewsheet rvs = this.viewsheetService.getViewsheet(id, principal);

      if(rvs == null) {
         LOGGER.error("The viewsheet does not exist: {}", id);
         return null;
      }

      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return null;
      }

      FullEditorAssembly result;
      boolean changed = false;

      // Bug #78734, the name is missing if Full Editor is clicked before the preview reaches
      // the client. The read lock waits for a recommendation in progress, which adds the
      // preview assembly under the write lock, so the latest preview assembly is used.
      box.get().lockRead();

      try {
         Viewsheet vs = rvs.getViewsheet();
         Assembly assembly;

         if(assemblyName == null) {
            assembly = WizardRecommenderUtil.getTempAssembly(vs);

            if(assembly == null) {
               // no preview assembly, e.g. the chart is not bound (Bug #44232)
               return null;
            }
         }
         else {
            assembly = vs.getAssembly(assemblyName);

            if(assembly == null) {
               LOGGER.error("The assembly does not exist: {}", assemblyName);
               return null;
            }
         }

         VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
         VSWizardOriginalModel originalModel =
            vsTemporaryInfo == null ? null : vsTemporaryInfo.getOriginalModel();

         if(originalModel != null) {
            String originalName = originalModel.getOriginalName();
            Assembly originalAssembly = vs.getAssembly(originalName);

            if(originalAssembly != null &&
               assembly.getAssemblyType() == originalAssembly.getAssemblyType())
            {
               assembly.setPixelSize(originalAssembly.getPixelSize());
            }

            changed = true;
         }

         result = new FullEditorAssembly(assembly.getAbsoluteName(), getObjectType(assembly));
      }
      finally {
         box.get().unlockRead();
      }

      if(changed) {
         viewsheetService.flushRuntimeSheet(id);
      }

      return result;
   }

   /**
    * Gets the client object type of an assembly the recommender creates, or {@code null} for
    * any other assembly.
    */
   private static String getObjectType(Assembly assembly) {
      if(assembly instanceof ChartVSAssembly) {
         return "VSChart";
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         return "VSCrosstab";
      }
      else if(assembly instanceof TableVSAssembly) {
         return "VSTable";
      }
      else if(assembly instanceof GaugeVSAssembly) {
         return "VSGauge";
      }
      else if(assembly instanceof TextVSAssembly) {
         return "VSText";
      }
      else if(assembly instanceof SelectionListVSAssembly) {
         return "VSSelectionList";
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         return "VSSelectionTree";
      }
      else if(assembly instanceof TimeSliderVSAssembly) {
         return "VSRangeSlider";
      }
      else if(assembly instanceof CalendarVSAssembly) {
         return "VSCalendar";
      }

      return null;
   }

   /**
    * The assembly the full editor opens. It is returned through the cluster proxy, so it must
    * be serializable.
    */
   public record FullEditorAssembly(String absoluteName, String objectType)
      implements Serializable
   {
   }

   private final VSWizardTemporaryInfoService temporaryInfoService;
   private final ViewsheetService viewsheetService;

   private static final Logger LOGGER = LoggerFactory.getLogger(VSWizardObjectToolbarService.class);
}
