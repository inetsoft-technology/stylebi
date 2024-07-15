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
import { Component } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DataRef } from "../../../common/data/data-ref";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";
import { VSWizardItem } from "./wizard-item.component";

@Component({
   selector: "wizard-detail-item",
   templateUrl: "./wizard-detail-item.component.html",
   styleUrls: ["./wizard-detail-item.component.scss", "./wizard-group-item.component.scss"]
})
export class VSWizardDetailItem extends VSWizardItem<DataRef> {

   constructor(protected modalService: NgbModal,
               protected clientService: ViewsheetClientService,
               protected treeService: VSWizardBindingTreeService){
      super(modalService, clientService, treeService);
   }

   getDataType(): string {
      return this.dataRef.dataType;
   }

   getFullName(): string {
      return this.dataRef.name;
   }
}
