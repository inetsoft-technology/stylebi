/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { DOCUMENT } from "@angular/common";
import {
   AfterViewInit, Component, ElementRef, HostBinding, Inject, Input, NgZone
} from "@angular/core";
import {
   DEFAULT_ANCHOR, LEFT_INVALID_ANCHOR, PHYSICAL_VIEW_TYPE_COLUMN
} from "../../../../../composer/gui/ws/jsplumb/jsplumb-graph-schema.config";
import { TableGraphModel } from "../../../model/datasources/database/physical-model/graph/table-graph-model";
import { JoinThumbnailService } from "./join-thumbnail.service";

const MIN_LEFT_CONNECTION_GAP = 40;

@Component({
   selector: "edit-join-table",
   templateUrl: "edit-join-table.component.html",
   styleUrls: ["edit-join-table.component.scss"]
})
export class EditJoinTableComponent implements AfterViewInit {
   @Input() table: TableGraphModel;

   dragging = false;

   constructor(private thumbnailService: JoinThumbnailService,
               public hostRef: ElementRef,
               private zone: NgZone,
               @Inject(DOCUMENT) private document: any)
   {
   }

   ngAfterViewInit(): void {
      let dragLeader: boolean = false;

      const checkDragLeader = (params: any) => {
         this.zone.run(() => {
            this.dragging = true;
         });

         const event: MouseEvent = params.e;

         // If params has mouse event e, then this table should be the "leader"; this assembly
         // is the one being dragged.
         if(event) {
            dragLeader = true;
         }
      };

      const dragging = (params: any) => {
         if(params.pos[0] < MIN_LEFT_CONNECTION_GAP && this.thumbnailService.getJoinEditPaneModel()
            .tables.filter(table => table != this.table)
            .every(table => table.bounds.x < MIN_LEFT_CONNECTION_GAP))
         {
            this.jsp.selectEndpoints({
               element: params.e1
            }).setAnchor(LEFT_INVALID_ANCHOR, true);
         }
         else {
            this.jsp.selectEndpoints({
               element: params.e1
            }).setAnchor(DEFAULT_ANCHOR, true);
         }

         this.jsp.repaintEverything();
      };

      const updatePos = (params: any) => {
         if(dragLeader) {
            dragLeader = false;
         }

         this.zone.run(() => {
            this.dragging = false;
            this.table.bounds.x = params.pos[0];
            this.table.bounds.y = params.pos[1];
         });
      };

      this.zone.runOutsideAngular(() =>
         this.jsp.draggable(this.hostRef.nativeElement, {
            start: checkDragLeader,
            drag: dragging,
            stop: updatePos,
            consumeStartEvent: false,
            // Limit the area where table can be dragged.
            containment: true,
            handle: ".jsplumb-draggable-handle, .jsplumb-draggable-handle *"
         }));
   }

   private get jsp(): JSPlumb.JSPlumbInstance {
      return this.thumbnailService.jsPlumbInstance;
   }

   @HostBinding("style.top.px")
   public get top(): number {
      return this.table.bounds.y;
   }

   @HostBinding("style.left.px")
   public get left(): number {
      return this.table.bounds.x;
   }
}
