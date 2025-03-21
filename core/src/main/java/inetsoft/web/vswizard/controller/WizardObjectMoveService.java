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
import inetsoft.web.composer.vs.objects.event.MoveVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.MultiMoveVsObjectEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.service.WizardVSObjectService;
import jakarta.resource.spi.work.Work;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class WizardObjectMoveService {

   public WizardObjectMoveService(WizardVSObjectService wizardVSObjectService,
                                  ViewsheetService engine) {
      this.wizardVSObjectService = wizardVSObjectService;
      this.engine = engine;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void moveObject(@ClusterProxyKey String vId, MoveVSObjectEvent event, String linkUri,
                          Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vId, principal);

      if(rvs == null) {
         return null;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockWrite();

      try {
         this.wizardVSObjectService.moveVSObject(rvs, event, linkUri, principal, commandDispatcher);
      }
      finally {
         box.unlockWrite();
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void moveObjects(@ClusterProxyKey String vId, MultiMoveVsObjectEvent multiEvent,
                           Principal principal, CommandDispatcher dispatcher,String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vId, principal);

      if(rvs == null) {
         return null;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockWrite();

      try {
         this.wizardVSObjectService.moveVSObjects(rvs, multiEvent, linkUri, principal, dispatcher);
      }
      finally {
         box.unlockWrite();
      }

      return null;
   }

   private final WizardVSObjectService wizardVSObjectService;
   private final ViewsheetService engine;
}
