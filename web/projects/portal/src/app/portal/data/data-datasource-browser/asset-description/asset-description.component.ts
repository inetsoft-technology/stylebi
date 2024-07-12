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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { AssetItem } from "../../model/datasources/database/asset-item";
import { LogicalModelBrowserInfo } from "../../model/datasources/database/physical-model/logical-model/logical-model-browser-info";

@Component({
  selector: "asset-description",
  templateUrl: "./asset-description.component.html",
  styleUrls: ["./asset-description.component.scss"]
})
export class AssetDescriptionComponent {
  @Input() selectedFile: AssetItem;
  @Input() isWorksheet: boolean = false;
  @Output() onClose: EventEmitter<void> = new EventEmitter();

  getBasedView(): string {
    if(this.selectedFile?.type === "logical_model") {
      return (<LogicalModelBrowserInfo> this.selectedFile).physicalModel;
    }

    return "";
  }

  close(): void {
    this.onClose.emit();
  }
}