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
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { TableViewGeneralPaneModel } from "../model/table-view-general-pane-model";

@Component({
   selector: "table-view-general-pane",
   templateUrl: "table-view-general-pane.component.html",
})
export class TableViewGeneralPane implements OnInit {
   @Input() model: TableViewGeneralPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() variableValues: string[];
   @Input() vsId: string;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;

   initForm(): void {
      this.form.addControl("maxRows", new UntypedFormControl(this.model.maxRows,
                                                      FormValidators.positiveIntegerInRange));
      this.form.addControl("generalPropPaneForm", new UntypedFormGroup({}));
      this.form.addControl("sizePositionPaneForm", new UntypedFormGroup({}));
   }

   ngOnInit(): void {
      this.initForm();
   }
}
