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

import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.HandleWizardExceptions;

import inetsoft.web.vswizard.event.ChangeVisualizationTypeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Handle change selected type of recommend.
 */
@Controller
public class VSWizardVisualizationController {

   @Autowired
   public VSWizardVisualizationController(VSWizardVisualizationServiceProxy vsWizardVisualizationServiceProxy,
                                          RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.vsWizardVisualizationServiceProxy = vsWizardVisualizationServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @MessageMapping("/vswizard/visualization/change-selectedType")
   @HandleWizardExceptions
   public void changeSelectedType(@Payload ChangeVisualizationTypeEvent event,
                                  CommandDispatcher dispatcher, Principal principal,
                                  @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();
      vsWizardVisualizationServiceProxy.changeSelectedType(id, event, dispatcher, principal, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSWizardVisualizationServiceProxy vsWizardVisualizationServiceProxy;

}
