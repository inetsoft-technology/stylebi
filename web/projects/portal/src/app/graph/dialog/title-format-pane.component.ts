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
import { Component, Input, TemplateRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DynamicComboBox } from "../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { RotationRadioGroup } from "../../widget/format/rotation-radio-group.component";
import { TitleFormatPaneModel } from "../model/dialog/title-format-pane-model";
import { StyleConstants } from "../../common/util/style-constants";
import { UIContextService } from "../../common/services/ui-context.service";

@Component({
   selector: "title-format-pane",
   templateUrl: "title-format-pane.component.html",
   styles: [`
      .right {
         width:70px;
         text-align:right;
      }
   `]
})
export class TitleFormatPane {
   @Input() viewer: boolean = false;
   @Input() variableValues: string[] = [];
   @Input() model: TitleFormatPaneModel;
   @Input() vsId: string = null;

   constructor(private modalService: NgbModal,
               private uiContextService: UIContextService)
   {
   }

   onValueChange(value: string) {
      this.model.title = value;
   }
}
