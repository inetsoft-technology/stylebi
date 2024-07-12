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
import { HttpParams } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { SourceChangeModel } from "../../composer/data/vs/source-change-model";
import { ModelService } from "../../widget/services/model.service";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { DataTransfer, DropTarget } from "../data/dnd-transfer";
import { ViewsheetClientService } from "../viewsheet-client";
import { DndService } from "./dnd.service";
import { VSDndEvent } from "./vs-dnd-event";

export class VSDndService extends DndService {
   constructor(private modelService: ModelService,
               protected modalService: NgbModal,
               private clientService: ViewsheetClientService)
   {
      super(modalService);
   }

   // some component (like bindingpane) passes chart, but components passes vschart
   getObjectType(objType: string): string {
      objType = objType.toLowerCase();
      return objType.startsWith("vs") ? objType : "vs" + objType;
   }

   processDndFromTree(entries: AssetEntry[], dropTarget: DropTarget, sourceInfo: any): void {
      const params = new HttpParams()
         .set("runtimeId", this.clientService.runtimeId)
         .set("assembly", dropTarget.assembly)
         .set("table", sourceInfo.source);

      this.modelService.getModel("../vsassembly/binding/sourcechange", params)
         .subscribe((data: SourceChangeModel) =>
      {
         let promise: Promise<boolean> = Promise.resolve(true);
         let sourceChanged: boolean = data.changed;

         if(sourceChanged) {
           promise = promise.then(() => this.showSourceChangedDialog()
              .then((result: string) =>  {
                  let val: boolean = result === "ok";
                  sourceChanged = val;
                  return val;
            }));
         }

         promise.then((confirmed) => {
            if(confirmed) {
               let evt: VSDndEvent = new VSDndEvent(dropTarget.assembly, null, dropTarget,
                                                    entries, sourceInfo.source, false, true,
                                                    sourceChanged);

               let url = "/events/" + this.getObjectType(dropTarget.objectType) + "/dnd/addColumns";
               this.clientService.sendEvent(url, evt);
            }
         });
      });
   }

   processDndToTree(transfer: DataTransfer): void {
      if(transfer) {
         let url = "/events/" + this.getObjectType(transfer.objectType) + "/dnd/removeColumns";
         this.clientService.sendEvent(url, new VSDndEvent(transfer.assembly,
                                                          transfer, null, null));
      }
   }

   processDnd(transfer: DataTransfer, dropTarget: DropTarget): void {
      if(this.isIngoredDnd(transfer, dropTarget)) {
         return;
      }

      const objtype = this.getObjectType(dropTarget.objectType ? dropTarget.objectType
                                         : transfer.objectType);
      const assembly = dropTarget.assembly ? dropTarget.assembly : transfer.assembly;

      let url = "/events/" + objtype + "/dnd/addRemoveColumns";
      this.clientService.sendEvent(url, new VSDndEvent(assembly, transfer, dropTarget, null));
   }
}
