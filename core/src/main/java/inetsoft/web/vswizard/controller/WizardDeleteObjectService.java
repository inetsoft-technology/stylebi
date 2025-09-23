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
import inetsoft.web.composer.vs.objects.event.RemoveVSObjectsEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.service.WizardVSObjectService;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class WizardDeleteObjectService {

   public WizardDeleteObjectService(ViewsheetService engine,
                                    WizardVSObjectService wizardVsObjectService)
   {
      this.engine = engine;
      this.wizardVsObjectService = wizardVsObjectService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void removeObject(@ClusterProxyKey String vsId, RemoveVSObjectsEvent event, String linkUri,
                            Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);

      if(rvs == null) {
         return null;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockWrite();

      try {
         this.wizardVsObjectService.deleteVSObject(rvs, event, linkUri, principal, commandDispatcher);
      }
      finally {
         box.unlockWrite();
         engine.flushRuntimeSheet(vsId);
      }

      return null;
   }

   private final ViewsheetService engine;
   private final WizardVSObjectService wizardVsObjectService;
}
