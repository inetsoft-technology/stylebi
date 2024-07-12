/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Injectable } from "@angular/core";
import { MoveVSObjectEvent } from "./objects/event/move-vs-object-event";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ResizeVSObjectEvent } from "./objects/event/resize-vs-object-event";
import { MultiVsObjectEvent } from "./objects/event/multi-vs-object-event";
import { VSObjectEvent } from "../../../vsobjects/event/vs-object-event";

const VIEWSHEET_MULTIMOVE_URI = "/events/composer/viewsheet/objects/multimove";
const VIEWSHEET_MULTIRESIZE_URI = "/events/composer/viewsheet/objects/multiresize";
const VIEWSHEET_WIZARD_MULTIMOVE_URI = "/events/composer/vswizard/objects/multimove";
const VIEWSHEET_WIZARD_MULTIRESIZE_URI = "/events/composer/vswizard/objects/multiresize";

@Injectable()
export class EventQueueService {
   private eventQueue: VSObjectEvent[] = [];
   private timer: any;

   private addEvent(clientService: ViewsheetClientService,
                    event: VSObjectEvent, url: string): void {
      clearTimeout(this.timer);
      this.eventQueue.push(event);
      this.timer = setTimeout(() => {
         let multiEvent: MultiVsObjectEvent =
            new MultiVsObjectEvent(this.eventQueue);
         clientService.sendEvent(url, multiEvent);
         this.eventQueue = [];
      }, 200);
   }

   public addMoveEvent(clientService: ViewsheetClientService,
                       moveEvent: MoveVSObjectEvent): void {
      this.addEvent(clientService, moveEvent, VIEWSHEET_MULTIMOVE_URI);
   }

   public addResizeEvent(clientService: ViewsheetClientService,
                         resizeEvent: ResizeVSObjectEvent): void {
      this.addEvent(clientService, resizeEvent, VIEWSHEET_MULTIRESIZE_URI);
   }

   public addWizardMoveEvent(clientService: ViewsheetClientService,
                             moveEvent: MoveVSObjectEvent): void
   {
      this.addEvent(clientService, moveEvent, VIEWSHEET_WIZARD_MULTIMOVE_URI);
   }

   public addWizardResizeEvent(clientService: ViewsheetClientService,
                               resizeEvent: ResizeVSObjectEvent): void
   {
      this.addEvent(clientService, resizeEvent, VIEWSHEET_WIZARD_MULTIRESIZE_URI);
   }
}
