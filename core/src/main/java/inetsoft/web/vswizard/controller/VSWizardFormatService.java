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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.event.SetWizardBindingFormatEvent;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSWizardFormatService {

   public VSWizardFormatService(ViewsheetService viewsheetService,
                                VSWizardBindingHandler bindingHandler,
                                VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.viewsheetService = viewsheetService;
      this.bindingHandler = bindingHandler;
      this.temporaryInfoService = temporaryInfoService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateFormat(@ClusterProxyKey String id, SetWizardBindingFormatEvent event,
                            CommandDispatcher dispatcher, Principal principal, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         VSTemporaryInfo tinfo = temporaryInfoService.getVSTemporaryInfo(rvs);
         FormatInfoModel model = event.getModel();
         VSFormat fmt = tinfo.getUserFormat(event.getField());

         if(fmt == null) {
            tinfo.setFormat(event.getField(), fmt = new VSFormat());
         }

         String modelFormat = FormatInfoModel.getDurationFormat(model.getFormat(),
                                                                model.isDurationPadZeros());
         String formatSpec = model.getFormatSpec();
         String dateSpec = model.getDateSpec();
         FormatInfoModel oldFormat = bindingHandler.getBindingFormatModel(fmt);
         String oldModelFormat = FormatInfoModel.getDurationFormat(oldFormat.getFormat(),
                                                                   oldFormat.isDurationPadZeros());

         if(!Tool.equals(modelFormat, oldModelFormat) ||
            !Tool.equals(formatSpec, oldFormat.getFormatSpec()) ||
            !Tool.equals(dateSpec, oldFormat.getDateSpec()))
         {
            if(modelFormat != null && modelFormat.equals(XConstants.COMMA_FORMAT)) {
               modelFormat = XConstants.DECIMAL_FORMAT;
               formatSpec = "#,##0";
            }
            else if(XConstants.DATE_FORMAT.equals(modelFormat) && !"Custom".equals(dateSpec)) {
               formatSpec = dateSpec;
            }

            fmt.setFormatExtentValue(formatSpec);
            fmt.setFormatValue(modelFormat);
            VSFormat donutChartCalcFmt = tinfo.getUserFormat("Total@" + event.getField());

            if(donutChartCalcFmt != null) {
               donutChartCalcFmt.setFormatExtentValue(formatSpec);
               donutChartCalcFmt.setFormatValue(modelFormat);
            }

            Viewsheet vs = rvs.getViewsheet();
            VSAssembly tempAssembly = WizardRecommenderUtil.getTempAssembly(vs);
            bindingHandler.updatePrimaryAssembly(rvs, linkUri, tempAssembly, false, false, dispatcher);
         }
      }
      finally {
         box.unlockRead();
      }

      return null;
   }

   private final ViewsheetService viewsheetService;
   private final VSWizardBindingHandler bindingHandler;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
