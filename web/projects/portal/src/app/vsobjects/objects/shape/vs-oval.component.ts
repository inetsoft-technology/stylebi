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
import { Component, Input, NgZone, OnChanges, SimpleChanges } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ContextProvider } from "../../context-provider.service";
import { VSOvalModel } from "../../model/vs-oval-model";
import { VSShape } from "./vs-shape";
import { DataTipService } from "../data-tip/data-tip.service";

@Component({
   selector: "vs-oval",
   templateUrl: "vs-oval.component.html",
   styleUrls: ["vs-oval.component.scss"]
})
export class VSOval extends VSShape<VSOvalModel> implements OnChanges {
   @Input() selected: boolean = false;
   public ovalFilterId: string;
   public ovalMaskId: string;

   constructor(protected viewsheetClient: ViewsheetClientService,
               protected modalService: NgbModal,
               zone: NgZone,
               contextProvider: ContextProvider,
               protected dataTipService: DataTipService)
   {
      super(viewsheetClient, modalService, zone, contextProvider, dataTipService);
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         this.updateLineStyle();
         this.ovalFilterId = "ovalblur" + this.validateID(
            this.viewsheetClient.runtimeId + this.getAssemblyName());
         this.ovalMaskId = "ovalmask" + this.validateID(
            this.viewsheetClient.runtimeId + this.getAssemblyName());
      }
   }

   validateID(id: string): string {
      return id.replace(/ /g, "_");
   }
}
