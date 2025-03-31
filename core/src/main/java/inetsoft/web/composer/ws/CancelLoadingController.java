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
package inetsoft.web.composer.ws;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.XFactory;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class CancelLoadingController extends WorksheetController {
   public CancelLoadingController(CancelLoadingServiceProxy cancelLoadingService) {
      this.cancelLoadingService = cancelLoadingService;
   }

   /**
    * From 12.2 LoadingMetaDataEvent.
    */
   @MessageMapping("/composer/worksheet/cancel-loading")
   public void cancelLoading(
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      String runtimeId = getRuntimeId();
      cancelLoadingService.cancelLoading(runtimeId, principal, commandDispatcher);
   }

   private final CancelLoadingServiceProxy cancelLoadingService;
}
