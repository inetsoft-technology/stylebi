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
import {
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostBinding,
   Input,
   OnChanges,
   OnDestroy,
   Output,
   SimpleChanges
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { FeatureFlagsService } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { Notification } from "../../../../common/data/notification";
import { Point } from "../../../../common/data/point";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { WSGroupingAssembly } from "../../../data/ws/ws-grouping-assembly";
import { WSAssemblyThumbnail } from "./ws-assembly-thumbnail";

@Component({
   selector: "grouping-thumbnail",
   templateUrl: "grouping-thumbnail.component.html",
   styleUrls: ["thumbnail-base.scss", "../jsplumb/jsplumb-shared.scss"]
})
export class GroupingThumbnail extends WSAssemblyThumbnail
   implements OnChanges, AfterViewInit, OnDestroy
{
   @Input() grouping: WSGroupingAssembly;
   @Input() worksheet: Worksheet;
   @Output() onRemove: EventEmitter<WSAssembly> = new EventEmitter<WSAssembly>();
   @Output() onDestroy: EventEmitter<WSAssembly> = new EventEmitter<WSAssembly>();
   @Output() onCut: EventEmitter<WSAssembly> = new EventEmitter<WSAssembly>();
   @Output() onCopy: EventEmitter<WSAssembly> = new EventEmitter<WSAssembly>();
   @Output() onRefreshAssembly: EventEmitter<[string, any]> = new EventEmitter<[string, any]>();
   @Output() onRegisterAssembly: EventEmitter<[WSAssembly, string]> =
      new EventEmitter<[WSAssembly, string]>();
   @Output() onDragPasteAssemblies: EventEmitter<Point> = new EventEmitter<Point>();
   @Output() onMoveAssemblies: EventEmitter<Point> = new EventEmitter<Point>();
   @Output() onSetDraggable: EventEmitter<[any, any]> = new EventEmitter<[any, any]>();
   @Output() onStartEditName = new EventEmitter<void>();
   @Output() onEditName = new EventEmitter<string>();
   @Output() onNotify = new EventEmitter<Notification>();
   @Output() onToggleAutoUpdate: EventEmitter<void> = new EventEmitter<void>();
   @Output() onSelectDependent = new EventEmitter<void>();

   constructor(protected ngbModal: NgbModal,
               protected modelService: ModelService,
               protected modalService: DialogService,
               protected thumbnail: ElementRef,
               protected featureFlagsService: FeatureFlagsService)
   {
      super();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("grouping")) {
         super.onChange(changes["grouping"]);
      }
   }

   ngAfterViewInit() {
      super.ngAfterViewInit();
   }

   ngOnDestroy() {
      super.ngOnDestroy();
   }

   @HostBinding("style.top.px")
   get top(): number {
      return this.grouping.top;
   }

   @HostBinding("style.left.px")
   get left(): number {
      return this.grouping.left;
   }

   @HostBinding("style.width.px")
   get styleWidth(): number {
      return this.width;
   }

   @HostBinding("attr.data-ws-assembly-name")
   get dataWSAssemblyName(): string {
      return this.grouping.name;
   }

   toggleAutoUpdate() {
      this.onToggleAutoUpdate.emit();
   }
}
