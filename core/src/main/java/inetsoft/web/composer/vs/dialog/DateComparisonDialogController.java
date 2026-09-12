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
package inetsoft.web.composer.vs.dialog;

import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.composer.model.vs.DateComparisonDialogModel;
import inetsoft.web.composer.model.vs.DateComparisonPaneModel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
public class DateComparisonDialogController {
   public DateComparisonDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                         VisualFrameModelFactoryService vFactoryService,
                                         DateComparisonDialogServiceProxy dialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vFactoryService = vFactoryService;
      this.dialogServiceProxy = dialogServiceProxy;
   }

   @GetMapping("/api/composer/vs/date-comparison-model/{objectId}/**")
   public DateComparisonPaneModel getDateComparison(@PathVariable("objectId") String objectId,
                                                    @RemainingPath String runtimeId,
                                                    Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogServiceProxy.getDateComparison(runtimeId, objectId, principal);
   }

   @Undoable
   @MessageMapping("composer/vs/date-comparison-dialog-model/{assemblyName}")
   public void setDateComparison(@DestinationVariable("assemblyName") String assemblyName,
                                 @RequestBody DateComparisonDialogModel model,
                                 @LinkUri String linkUri,
                                 Principal principal,
                                 CommandDispatcher dispatcher)
      throws Exception
   {
      DateComparisonInfo comparisonInfo = model.getDateComparisonPaneModel() == null ? null :
         model.getDateComparisonPaneModel().toDateComparisonInfo();
      VisualFrameWrapper wrapper = comparisonInfo.getDcColorFrameWrapper();

      if(wrapper != null) {
         VisualFrameWrapper nwrapper = vFactoryService.updateVisualFrameWrapper(
            wrapper, model.getDateComparisonPaneModel().getVisualFrameModel());
         ((CategoricalColorFrameWrapper) nwrapper).setShareColors(false);
         comparisonInfo.setDcColorFrameWrapper(nwrapper);
      }

      dialogServiceProxy.setDateComparison(runtimeViewsheetRef.getRuntimeId(), assemblyName, comparisonInfo, model.getShareFromAssembly(), linkUri,
         principal, dispatcher);
   }

   @Undoable
   @MessageMapping("composer/vs/date-comparison-dialog-model/clear/{assemblyName}")
   public void clearDateComparison(@DestinationVariable("assemblyName") String assemblyName,
                                   @LinkUri String linkUri,
                                   Principal principal,
                                   CommandDispatcher dispatcher)
      throws Exception
   {
      dialogServiceProxy.clearDateComparison(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                             linkUri, principal, dispatcher);
   }

   @GetMapping("/api/composer/vs/date-comparison-dialog-model/{objectId}/**")
   public DateComparisonDialogModel getShare(@PathVariable("objectId") String objectId,
                                             @RemainingPath String runtimeId,
                                             Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogServiceProxy.getShare(runtimeId, objectId, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VisualFrameModelFactoryService vFactoryService;
   private final DateComparisonDialogServiceProxy dialogServiceProxy;
}
