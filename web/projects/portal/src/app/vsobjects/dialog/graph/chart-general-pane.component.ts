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
import { Component, Input, ViewChild } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { ChartGeneralPaneModel } from "../../model/chart-general-pane-model";
import { TipPane } from "./tip-pane.component";
import { SizePositionPane } from "../size-position-pane.component";
import { PaddingPane } from "../padding-pane.component";
import { TitlePropPane } from "../title-prop-pane.component";

import { GeneralPropPane } from "../general-prop-pane.component";

@Component({
    selector: "chart-general-pane",
    templateUrl: "chart-general-pane.component.html",
    imports: [
    GeneralPropPane,
    TitlePropPane,
    TipPane,
    PaddingPane,
    SizePositionPane
]
})
export class ChartGeneralPane {
   @Input() vsId: string;
   @Input() assemblyName: string;
   @Input() model: ChartGeneralPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() variableValues: string[];
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;

   @ViewChild(TipPane) tipPane: TipPane;

   get alphaInvalid(): boolean {
      return this.tipPane && this.tipPane.alphaInvalid;
   }

   get titleHeightEnable() {
      return this.model.generalPropPaneModel.enabled == "True" && this.model.titlePropPaneModel.visible;
   }
}
