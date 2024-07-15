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
import {
   AfterViewInit, ChangeDetectorRef, Component, ElementRef, HostBinding,
   Input, OnDestroy, OnInit
} from "@angular/core";
import { Subscription } from "rxjs";
import { AssetUtil } from "../../../../../binding/util/asset-util";
import {
   PHYSICAL_VIEW_TYPE_COLUMN
} from "../../../../../composer/gui/ws/jsplumb/jsplumb-graph-schema.config";
import { GraphColumnInfo } from "../../../model/datasources/database/physical-model/graph/graph-column-info";
import { JoinThumbnailService } from "./join-thumbnail.service";

@Component({
   selector: "edit-join-table-column",
   templateUrl: "edit-join-table-column.component.html",
   styleUrls: ["edit-join-table-column.component.scss"]
})
export class EditJoinTableColumnComponent implements OnInit, AfterViewInit, OnDestroy {
   @Input() column: GraphColumnInfo;
   private jsp: JSPlumb.JSPlumbInstance;
   private connSub: Subscription;
   private focusSub: Subscription;
   private compatibility: boolean; // when drag to connect style
   private highlighted: boolean; // when mouse over connection.

   constructor(private readonly hostRef: ElementRef,
               private readonly thumbnailService: JoinThumbnailService,
               private readonly cd: ChangeDetectorRef)
   {
      this.jsp = thumbnailService.jsPlumbInstance;

      this.connSub = this.thumbnailService.getConnectingColumnSubject()
         .subscribe((column) => {
            if(column == null) {
               this.compatibility = undefined;
            }
            else if(column != this.column &&
               column.table === this.column.table)
            {
               this.compatibility = false;
            }
            else {
               this.compatibility = AssetUtil.isMergeable(
                  this.column.type, column.type);
            }

            this.cd.markForCheck();
         });

      this.focusSub = this.thumbnailService.focusColumnPairSubject
         .subscribe((columns) => {
            if(columns == undefined) {
               this.highlighted = undefined;
            }
            else {
               this.highlighted = columns.indexOf(this.column) !== -1;
            }

            this.cd.markForCheck();
      });
   }

   ngOnInit(): void {
   }

   ngAfterViewInit(): void {
      this.makeSourceTarget();
      this.thumbnailService.registerColumn(this.column, this.hostRef.nativeElement.id);
   }

   ngOnDestroy(): void {
      this.connSub.unsubscribe();
      this.focusSub.unsubscribe();
      this.thumbnailService.unregisterColumn(this.column, this.hostRef.nativeElement.id);
      this.unmakeSourceTarget();
   }

   @HostBinding("class.schema-column-compatible")
   get compatible(): boolean {
      return this.compatibility === true;
   }

   @HostBinding("class.schema-column-incompatible")
   get incompatible(): boolean {
      return this.compatibility === false;
   }

   @HostBinding("class.schema-column-highlight")
   get highlight(): boolean {
      return this.highlighted === true;
   }

   @HostBinding("class.schema-column-ignore")
   get ignore(): boolean {
      return this.highlighted === false;
   }

   private makeSourceTarget(): void {
      this.jsp.makeSource(this.hostRef.nativeElement, {connectionType: PHYSICAL_VIEW_TYPE_COLUMN});
      this.jsp.makeTarget(this.hostRef.nativeElement);
   }

   private unmakeSourceTarget(): void {
      this.jsp.unmakeSource(this.hostRef.nativeElement);
      this.jsp.unmakeTarget(this.hostRef.nativeElement);
   }
}
