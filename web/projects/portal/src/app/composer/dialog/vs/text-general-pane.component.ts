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
import { Component, Input, OnInit } from "@angular/core";
import { UntypedFormGroup, FormsModule } from "@angular/forms";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TextGeneralPaneModel } from "../../data/vs/text-general-pane-model";
import { PopLocation, PopComponentService } from "../../../vsobjects/objects/data-tip/pop-component.service";
import { TruncatePipe } from "../../../widget/pipe/truncate.pipe";
import { NotificationsComponent } from "../../../widget/notifications/notifications.component";
import { SizePositionPane } from "../../../vsobjects/dialog/size-position-pane.component";
import { PaddingPane } from "../../../vsobjects/dialog/padding-pane.component";
import { AlphaDropdown } from "../../../widget/format/alpha-dropdown.component";
import { NgFor, NgIf } from "@angular/common";
import { TipPane } from "../../../vsobjects/dialog/graph/tip-pane.component";
import { TextPane } from "./text-pane.component";
import { OutputGeneralPane } from "./output-general-pane.component";

@Component({
    selector: "text-general-pane",
    templateUrl: "text-general-pane.component.html",
    imports: [
        OutputGeneralPane,
        TextPane,
        TipPane,
        FormsModule,
        NgFor,
        AlphaDropdown,
        NgIf,
        PaddingPane,
        SizePositionPane,
        NotificationsComponent,
        TruncatePipe,
    ]
})
export class TextGeneralPane implements OnInit {
   @Input() model: TextGeneralPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() variableValues: string[];
   @Input() layoutObject: boolean = false;
   @Input() vsId: string = null;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Input() objectAddRemoved: boolean = false;
   alphaInvalid: boolean = false;

   constructor(public popService: PopComponentService) {
   }

   ngOnInit(): void {
      this.form.addControl("outputForm", new UntypedFormGroup({}));
      this.form.addControl("sizePositionPaneForm", new UntypedFormGroup({}));
   }

   changeAlphaWarning(event) {
      this.alphaInvalid = event;
   }

   getKeys(): string[] {
      return Object.keys(PopLocation);
   }

   get strippedDrillmemberVariableValues(): string[] {
      let strippedList: string[] = [];

      if(this.variableValues) {
         this.variableValues.forEach((variable: string) => {

            if(variable.indexOf(".drillMember") == -1) {
               strippedList.push(variable);
            }
         });
      }

      return strippedList;
   }

}
