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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { GeoProvider } from "../../../common/data/geo-provider";
import { ChartGeoRef } from "../../data/chart/chart-geo-ref";

@Component({
   selector: "edit-geographic-dialog",
   templateUrl: "edit-geographic-dialog.component.html",
})
export class EditGeographicDialog implements OnInit {
   @Input()
   get provider(): GeoProvider {
      return this._provider;
   }

   set provider(provider: GeoProvider) {
      this._provider = provider;
      this._mapType = provider.getBindingModel().mapType;
   }

   @Input() refName: string;
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   private _provider: GeoProvider;
   _mapType: string;
   loadData: boolean = false;

   ngOnInit(): void {
   }

   updateLoadData(evt: string) {
      this.loadData = true;
   }

   get chartGeoModel(): ChartGeoRef {
      return this.provider.getChartGeoModel();
   }

   isGeoRef(): boolean {
      return this.chartGeoModel && this.chartGeoModel.classType == "geo";
   }

   okClicked(evt: MouseEvent): void {
      evt.stopPropagation();
      this.onCommit.emit("ok");
   }

   cancelClicked(evt: MouseEvent): void {
      evt.stopPropagation();

      if(this._mapType !== this._provider.getBindingModel().mapType && !this.isGeoRef()) {
         this._provider.changeMapType(this.refName, this._mapType, null).subscribe(() => {
            this.onCancel.emit("cancel");
         });
      }
      else {
         this.onCancel.emit("cancel");
      }
   }
}
