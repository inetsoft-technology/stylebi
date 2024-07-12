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
import { Observable } from "rxjs";
import { BrowseDataModel } from "../../common/data/browse-data-model";
import { ConditionValue } from "../../common/data/condition/condition-value";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { ExpressionValue } from "../../common/data/condition/expression-value";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { TreeNodeModel } from "../tree/tree-node-model";
import { ConditionFieldComboModel } from "./condition-field-combo-model";
import { SourceInfo } from "../../binding/data/source-info";

@Component({
   selector: "binary-condition-editor",
   templateUrl: "binary-condition-editor.component.html",
   styleUrls: ["./binary-condition-editor.component.scss"]
})
export class BinaryConditionEditor {
   public XSchema = XSchema;
   public ConditionValueType = ConditionValueType;
   @Input() vsId: string;
   @Input() dataFunction: () => Observable<BrowseDataModel>;
   @Input() variablesFunction: () => Observable<any[]>;
   @Input() columnTreeFunction: (value: ExpressionValue) => Observable<TreeNodeModel>;
   @Input() scriptDefinitionsFunction: (value: ExpressionValue) => Observable<any>;
   @Input() expressionTypes: ExpressionType[];
   @Input() valueTypes: ConditionValueType[];
   @Input() subqueryTables: SubqueryTable[];
   @Input() fieldsModel: ConditionFieldComboModel;
   @Input() grayedOutFields: DataRef[];
   @Input() field: DataRef;
   @Input() showUseList: boolean;
   @Input() values: ConditionValue[];
   @Input() source: SourceInfo;
   @Input() enableBrowseData: boolean = true;
   @Input() table: string;
   @Input() isVSContext = true;
   @Input() showOriginalName: boolean = false;
   @Output() valuesChange: EventEmitter<ConditionValue[]> = new EventEmitter<ConditionValue[]>();
}
