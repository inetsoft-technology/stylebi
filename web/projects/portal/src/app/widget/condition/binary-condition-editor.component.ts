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
import { ConditionEditor } from "./condition-editor.component";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { VSConditionPairingScope } from "../../composer/gui/wiz/editor-context";

@Component({
    selector: "binary-condition-editor",
    templateUrl: "binary-condition-editor.component.html",
    styleUrls: ["./binary-condition-editor.component.scss"],
    imports: [ConditionEditor]
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
   @Input() runtimeId: string;
   @Input() socketConnection: ViewsheetClientService;
   /** Forwarded to both hosted condition-editors so each value slot can offer Connect to Claude. */
   @Input() operation: ConditionOperation;
   @Input() isHighlight: boolean = false;
   /** Which viewsheet condition list this value belongs to (owning assembly and highlight name),
    *  threaded through to FormulaEditorDialog so its Connect to Claude pairing is scoped to this
    *  one value. Unset outside the viewsheet Conditions/Highlight dialogs. */
   @Input() vsConditionScope: VSConditionPairingScope;
   @Input() isVSContext = true;
   @Input() showOriginalName: boolean = false;
   @Output() valuesChange: EventEmitter<ConditionValue[]> = new EventEmitter<ConditionValue[]>();
}
