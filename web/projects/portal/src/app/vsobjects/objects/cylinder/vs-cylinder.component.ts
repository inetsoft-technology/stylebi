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
import { Component, EventEmitter, NgZone, Output } from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ContextProvider } from "../../context-provider.service";
import { VSCylinderModel } from "../../model/output/vs-cylinder-model";
import { AbstractVSObject } from "../abstract-vsobject.component";
import { DataTipService } from "../data-tip/data-tip.service";

@Component({
   selector: "vs-cylinder",
   templateUrl: "vs-cylinder.component.html",
   styleUrls: ["vs-cylinder.component.scss"]
})
/**
 * @deprecated since version 12.1
 */
export class VSCylinder extends AbstractVSObject<VSCylinderModel> {
   @Output() cylinderClicked = new EventEmitter();

   constructor(protected viewsheetClient: ViewsheetClientService,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               zone: NgZone)
   {
      super(viewsheetClient, zone, context, dataTipService);
   }

   onClick(event: MouseEvent) {
      this.cylinderClicked.emit(this.model.absoluteName);
   }

   getSrc(): string {
      return this.vsInfo.linkUri + "getAssemblyImage" +
         "/" + Tool.byteEncode(this.viewsheetClient.runtimeId) +
         "/" + Tool.byteEncode(this.model.absoluteName) +
         "/" + this.model.objectFormat.width +
         "/" + this.model.objectFormat.height +
         "?" + this.model.genTime;
   }
}
