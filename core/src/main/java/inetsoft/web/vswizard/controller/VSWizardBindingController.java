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
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.web.binding.command.SetGrayedOutFieldsCommand;
import inetsoft.web.binding.command.VSTrapCommand;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSTreeHandler;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.SourceChangeMessage;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.HandleWizardExceptions;
import inetsoft.web.vswizard.Recommend;
import inetsoft.web.vswizard.command.*;
import inetsoft.web.vswizard.event.*;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.*;
import inetsoft.web.vswizard.model.recommender.*;
import inetsoft.web.vswizard.recommender.VSRecommendationFactory;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.*;

/**
 * vs wizard binding tree controller.
 */
@Controller
public class VSWizardBindingController {

   @Autowired
   public VSWizardBindingController(VSWizardBindingServiceProxy vsWizardBindingServiceProxy,
                                    RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.vsWizardBindingServiceProxy = vsWizardBindingServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @LoadingMask
   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/tree")
   public void getBindingTree(@Payload GetBindingTreeEvent event,
                              CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      vsWizardBindingServiceProxy.getBindingTree(id, event, dispatcher, principal);
   }

   @LoadingMask
   @Recommend
   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/tree/node-changed")
   public void bindingTreeNodeChanged(@Payload RefreshBindingFieldsEvent event,
                                      CommandDispatcher dispatcher, Principal principal,
                                      @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      vsWizardBindingServiceProxy.bindingTreeNodeChanged(id, event, dispatcher, principal, linkUri);
   }

   @Recommend
   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/tree/refresh-fields")
   public void refreshBindingRefs(@Payload RefreshBindingFieldsEvent event,
                                  CommandDispatcher dispatcher, Principal principal,
                                  @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      vsWizardBindingServiceProxy.refreshBindingRefs(id, event, dispatcher, principal, linkUri);
   }

   @Recommend
   @MessageMapping("/vswizard/binding/refresh")
   @HandleWizardExceptions
   public void refreshBindingInfo(@Payload UpdateVsWizardBindingEvent event,
                                  CommandDispatcher dispatcher, Principal principal,
                                  @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      vsWizardBindingServiceProxy.refreshBindingInfo(id, event, dispatcher, principal, linkUri);
   }

   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/update-columns")
   public void updateColumns(@Payload UpdateColumnsEvent event,
                             CommandDispatcher dispatcher, Principal principal,
                             @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      vsWizardBindingServiceProxy.updateColumns(id, event, dispatcher, principal, linkUri);
   }

   private final VSWizardBindingServiceProxy vsWizardBindingServiceProxy;
   private final RuntimeViewsheetRef runtimeViewsheetRef;


   private static final Logger LOGGER = LoggerFactory.getLogger(VSWizardBindingController.class);
}
