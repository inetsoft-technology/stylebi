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
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   HostBinding,
   HostListener,
   Input,
   OnDestroy,
   OnInit,
} from "@angular/core";
import { Subscription } from "rxjs";
import { ColumnRef } from "../../../../../binding/data/column-ref";
import { AssetUtil } from "../../../../../binding/util/asset-util";
import { AssemblyActionGroup } from "../../../../../common/action/assembly-action-group";
import { ActionsContextmenuComponent } from "../../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AbstractTableAssembly } from "../../../../data/ws/abstract-table-assembly";
import { TableColumnPair } from "../../../../data/ws/table-column-pair";
import { TYPE_COLUMN } from "../../jsplumb/jsplumb-graph-schema.config";
import { SchemaThumbnailService } from "./schema-thumbnail.service";
import { NumericRangeRef } from "../../../../../common/data/numeric-range-ref";
import { DateRangeRef } from "../../../../../common/data/date-range-ref";

@Component({
   selector: "schema-column",
   templateUrl: "schema-column.component.html",
   styleUrls: ["schema-column.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class SchemaColumnComponent implements OnInit, AfterViewInit, OnDestroy {
   @Input() schemaTable: AbstractTableAssembly;
   @Input() column: ColumnRef;
   @Input() twoStepJoinColumn: TableColumnPair | null;
   private jsp: JSPlumb.JSPlumbInstance;
   private connSub: Subscription;
   private focusSub: Subscription;
   private compatibility: boolean;
   private highlighted: boolean;
   private tableColumn: TableColumnPair;
   private columnHeader: string;

   constructor(private readonly hostRef: ElementRef,
               private readonly schemaThumbnailService: SchemaThumbnailService,
               private readonly cd: ChangeDetectorRef,
               private readonly fixedDropdownService: FixedDropdownService)
   {
      this.jsp = schemaThumbnailService.jsPlumbInstance;
   }

   ngOnInit(): void {
      this.tableColumn = {column: this.column, table: this.schemaTable.name};
      this.columnHeader = this.schemaTable.colInfos
         .find(colInfo => colInfo.name == this.column.name).header;

      this.connSub = this.schemaThumbnailService.getConnectingColumnSubject()
         .subscribe((tableColumn) => {
            if(tableColumn == null) {
               this.compatibility = undefined;
            }
            else if(tableColumn != this.tableColumn &&
               tableColumn.table === this.tableColumn.table)
            {
               this.compatibility = false;
            }
            else {
               this.compatibility = AssetUtil.isMergeable(
                  this.tableColumn.column.dataType, tableColumn.column.dataType);
            }

            this.cd.markForCheck();
         });
      this.focusSub = this.schemaThumbnailService.focusColumnPairSubject.subscribe((columns) => {
         if(columns == undefined) {
            this.highlighted = undefined;
         }
         else {
            this.highlighted = columns.indexOf(this.column) !== -1;
         }

         this.cd.markForCheck();
      });
   }

   ngAfterViewInit(): void {
      this.makeSourceTarget();
      this.schemaThumbnailService.registerColumn(this.tableColumn, this.hostRef.nativeElement.id);
   }

   ngOnDestroy(): void {
      this.connSub.unsubscribe();
      this.focusSub.unsubscribe();
      this.schemaThumbnailService.unregisterColumn(this.tableColumn, this.hostRef.nativeElement.id);
      this.unmakeSourceTarget();
   }

   @HostListener("contextmenu", ["$event"])
   showContextmenu(event: MouseEvent): void {
      event.preventDefault();
      event.stopPropagation();

      const options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true,
         autoClose: true,
         closeOnOutsideClick: true,
         closeOnWindowResize: true
      };

      const component: ActionsContextmenuComponent =
         this.fixedDropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      component.sourceEvent = event;
      component.actions = this.createActions();
   }

   @HostListener("mousedown", ["$event"])
   mousedown(event: MouseEvent): void {
      if(this.twoStepJoinColumn != null && event.button === 0) {
         this.schemaThumbnailService.addNewJoinCondition(this.twoStepJoinColumn, this.tableColumn);
         this.schemaThumbnailService.stopTwoStepJoin();
      }
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

   getColumnHeader(): string {
      return this.column.alias ? this.column.alias :
         this.column.dataRefModel.attribute ? this.column.dataRefModel.attribute : this.columnHeader;
   }

   getTooltip(): string {
      return ColumnRef.getTooltip(this.column);
   }

   private makeSourceTarget(): void {
      this.jsp.makeSource(this.hostRef.nativeElement, {connectionType: TYPE_COLUMN});
      this.jsp.makeTarget(this.hostRef.nativeElement);
   }

   private unmakeSourceTarget(): void {
      this.jsp.unmakeSource(this.hostRef.nativeElement);
      this.jsp.unmakeTarget(this.hostRef.nativeElement);
   }

   private createActions(): AssemblyActionGroup[] {
      return [
         new AssemblyActionGroup([
            {
               id: () => "worksheet schema-column join-with",
               label: () => "_#(js:Join With)",
               icon: () => null,
               enabled: () => true,
               visible: () => true,
               action: () => this.schemaThumbnailService.startTwoStepJoin(this.tableColumn)
            },
         ])
      ];
   }
}
