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
import inetsoft.web.composer.vs.objects.event.ResizeVSObjectEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.service.WizardVSObjectService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class WizardObjectResizeService {

   public WizardObjectResizeService(ViewsheetService engine, WizardVSObjectService wizardVSObjectService) {
      this.engine = engine;
      this.wizardVSObjectService = wizardVSObjectService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void resizeObject(@ClusterProxyKey String vID, ResizeVSObjectEvent event, @LinkUri String linkUri,
                            Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vID, principal);

      if(rvs == null) {
         return null;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         this.wizardVSObjectService.resizeVSObject(rvs, event, linkUri, principal, commandDispatcher);
      }
      finally {
         box.unlockRead();
      }

      return null;
   }

   private final ViewsheetService engine;
   private final WizardVSObjectService wizardVSObjectService;
}
