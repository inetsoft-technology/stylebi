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
import { Component, NgZone } from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ContextProvider } from "../../context-provider.service";
import { VSSlidingScaleModel } from "../../model/output/vs-sliding-scale-model";
import { AbstractVSObject } from "../abstract-vsobject.component";
import { DataTipService } from "../data-tip/data-tip.service";

@Component({
   selector: "vs-sliding-scale",
   templateUrl: "vs-sliding-scale.component.html",
   styleUrls: ["vs-sliding-scale.component.scss"]
})

/**
 * @deprecated
 */
export class VSSlidingScale extends AbstractVSObject<VSSlidingScaleModel> {
   constructor(protected viewsheetClient: ViewsheetClientService,
               zone: NgZone,
               protected context: ContextProvider,
               protected dataTipService: DataTipService)
   {
      super(viewsheetClient, zone, context, dataTipService);
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
