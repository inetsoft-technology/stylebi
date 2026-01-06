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
import inetsoft.web.composer.ws.event.WSLayoutGraphEvent;
import inetsoft.web.composer.ws.service.WorksheetLayoutService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that provides a message endpoint for performing a hierarchical layout on
 * the graph thumbnails.
 */
@Controller
public class LayoutGraphController extends WorksheetController {
   public LayoutGraphController(WorksheetLayoutService worksheetLayoutService) {
      this.worksheetLayoutService = worksheetLayoutService;
   }

   @Undoable
   @LoadingMask(true)
   @MessageMapping("/composer/worksheet/layout-graph")
   public void layoutGraph(@Payload WSLayoutGraphEvent event, Principal principal,
                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeWorksheet worksheet = getRuntimeWorksheet(principal);
      commandDispatcher.sendCommand(worksheetLayoutService.layoutGraph(worksheet.getWorksheet(), event));
   }

   private final WorksheetLayoutService worksheetLayoutService;
}
