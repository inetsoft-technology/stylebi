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
import { Injectable } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import {
   ChartAestheticTransfer,
   ChartAestheticDropTarget
} from "../../binding/data/chart/chart-transfer";
import { DragEvent } from "../data/drag-event";
import { DataTransfer, DropTarget, TableTransfer, BindingDropTarget } from "../data/dnd-transfer";
import { ComponentTool } from "../util/component-tool";

@Injectable()
export abstract class DndService {
   _transfer: any = {};
   // Absolute name of the vs-table the drag started from
   dragTableSource: string;

   constructor(protected modalService: NgbModal) {
   }

   getTransfer(): any {
      return this._transfer;
   }

   initTransfer(event: any): void {
      let text: string = event.dataTransfer.getData("text");

      if(!text) {
         return;
      }

      const data: any = JSON.parse(text);
      this._transfer.column = data.column;
      this._transfer.tableName = data.source;
      this._transfer.dragSource = data.dragSource;
   }

   public processOnDrop(event: DragEvent, dtarget: DropTarget, option?: DndDropOption): any {
      const data: any = JSON.parse(event.dataTransfer.getData("text"));

      if(!data) {
         return;
      }

      if(option) {
         if(option.level == OptionLevel.PRE && option.type == OptionType.CONFIRM && option.data) {
            ComponentTool.showConfirmDialog(this.modalService, option.data.title,
               option.data.message, {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
               .then((result) => {
                  if(result == "yes") {
                     return this.processOnDrop0(data, dtarget);
                  }
               });
         }
      }
      else {
         return this.processOnDrop0(data, dtarget);
      }
   }

   private processOnDrop0(data: any, dtarget: DropTarget): any {
      let entriesValue: any = data.column;
      let source: string = data.source;
      let prefix: string = data.prefix;
      let type: string = data.type;

      if(entriesValue && entriesValue != "") {
         if(dtarget == null) {
            return;
         }

         if(!prefix) {
            const colPath: string = entriesValue[0].path;
            const pathLevels = !!colPath ? colPath.split("/") : [];
            prefix = pathLevels.length > 0 ? pathLevels[0] : prefix;
         }

         let sourceInfo: any = {
            source: source,
            prefix: prefix,
            type: type
         };

         this.processDndFromTree(<AssetEntry[]> entriesValue, dtarget, sourceInfo);
      }
      else {
         let transfer: DataTransfer = data.dragSource;

         if(dtarget == null) {
            if(data.dragLastFiled) {
               return {msg: "_#(js:viewer.noSelectedColError)", type: "danger"};
            }

            this.processDndToTree(transfer);
            return;
         }

         this.processDnd(transfer, dtarget);
      }

      this.clearInfo();
      return null;
   }

   /**
    * Use default drag image in V12.3 instead of custom one.
    */
   public setDragStartStyle(event: any, label: string) {
      this.initTransfer(event);
      event.dataTransfer.effectAllowed  = "move";
   }

   public setDragOverStyle(event: any, accept: boolean) {
      event.dataTransfer.dropEffect = accept ? "move" : "none";
   }

   public dragLeave() {
      this.clearInfo();
   }

   clearInfo() {
      this._transfer = {};
   }

   public isCalcAggregate(): boolean {
      let entriesValue: any = this._transfer.column;

      if(entriesValue != null) {
         let assets: AssetEntry[] = entriesValue;

         for(let i: number = 0; i < assets.length; i++) {
            let asset: AssetEntry = assets[i];

            if(asset.properties.isCalc == "true"
               && asset.properties.basedOnDetail == "false")
            {
               return true;
            }
         }
      }

      return false;
   }

   public containsCalc(entriesValue?: any): boolean {
      if(!entriesValue) {
         entriesValue = this._transfer.column;
      }

      if(entriesValue != null) {
         let assets: AssetEntry[] = entriesValue;

         for(let i: number = 0; i < assets.length; i++) {
            let asset: AssetEntry = assets[i];

            if(asset.properties.isCalc == "true") {
               return true;
            }
         }
      }

      return false;
   }

   public isAllEmbeddedColumn(event: any): boolean {
      const data: any = JSON.parse(event.dataTransfer.getData("text"));
      let entriesValue: AssetEntry[] = <AssetEntry[]> data.column;
      let isAllEmbedded: boolean = true;

      if(!!entriesValue) {
         entriesValue.forEach((entry: AssetEntry) => {
            if(entry.properties["embedded"] == "false") {
               isAllEmbedded = false;

               return;
            }
         });
      }

      return isAllEmbedded;
   }

   isIngoredDnd(transfer: DataTransfer, dropTarget: DropTarget): boolean {
      if(!transfer || !dropTarget) {
         return true;
      }
      else if(transfer.classType == "ChartAestheticTransfer" &&
              dropTarget.classType == "ChartAestheticDropTarget")
      {
         return (<ChartAestheticTransfer> transfer).dragType ==
            (<ChartAestheticDropTarget> dropTarget).dropType &&
            (<ChartAestheticTransfer> transfer).targetField ==
            (<ChartAestheticDropTarget> dropTarget).targetField;
      }
      else if(transfer.classType === "TableTransfer" &&
              dropTarget.classType === "BindingDropTarget")
      {
         let tabTransfer: TableTransfer = <TableTransfer> transfer;
         let bindingTarget: BindingDropTarget = <BindingDropTarget> dropTarget;

         return tabTransfer.assembly == bindingTarget.assembly &&
            tabTransfer.dragType == bindingTarget.dropType &&
            tabTransfer.dragIndex == bindingTarget.dropIndex;
      }

      return false;
   }

   showSourceChangedDialog(): Promise<any> {
      const msg = "_#(js:viewer.viewsheet.chart.sourceChanged)";
      return ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg);
   }

   abstract processDndFromTree(entries: AssetEntry[], dropTarget: DropTarget,
      sourceInfo: any): void;
   abstract processDndToTree(transfer: DataTransfer): void;
   abstract processDnd(transfer: DataTransfer, dropTarget: DropTarget): void;
}

export interface DndDropOption {
   level: OptionLevel;
   type: OptionType;
   data: any;
}

export interface ConfirmOptionData {
   title: string;
   message: string;
}

export enum OptionLevel {
   PRE
}

export enum OptionType {
   CONFIRM
}
