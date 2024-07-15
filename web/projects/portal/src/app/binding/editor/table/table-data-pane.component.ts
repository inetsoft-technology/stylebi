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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { BindingService } from "../../services/binding.service";
import { TableBindingModel } from "../../data/table/table-binding-model";
import { UIContextService } from "../../../common/services/ui-context.service";
import { GuiTool } from "../../../common/util/gui-tool";

@Component({
   selector: "table-data-pane",
   templateUrl: "table-data-pane.component.html",
   styleUrls: ["../data-pane.component.scss"]
})

export class TableDataPane {
   @Input() bindingModel: TableBindingModel;
   @Input() grayedOutValues: string[] = [];
   @Output() onPopUpWarning: EventEmitter<any> = new EventEmitter<any>();
   isIE: boolean;

   public constructor(protected bindingService: BindingService,
                      private uiContextService: UIContextService)
   {
      this.isIE = GuiTool.isIE();
   }

   isVS() {
      return this.uiContextService.isVS();
   }
}
