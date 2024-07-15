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
import { Component, EventEmitter, Output, Input } from "@angular/core";
import { OperationModel } from "../../../../../data/model/datasources/database/vpm/condition/clause/operation-model";
import { VPMColumnModel } from "../../../../../data/model/datasources/database/vpm/condition/vpm-column-model";
import { ClauseValueModel } from "../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";
import { XSchema } from "../../../../../../common/data/xschema";
import { Observable } from "rxjs";

/**
 * Condition editor for vpm XTrinaryCondition. Edits the 2nd and 3rd expression values.
 */
@Component({
   selector: "vpm-trinary-condition-editor",
   templateUrl: "vpm-trinary-condition-editor.component.html",
})
export class VPMTrinaryConditionEditor {
   @Input() operation: OperationModel;
   @Input() fields: VPMColumnModel[] = [];
   @Input() value2: ClauseValueModel;
   @Input() value3: ClauseValueModel;
   @Input() valueTypes2: string[] = [];
   @Input() valueTypes3: string[] = [];
   @Input() enableBrowseData2: boolean = true;
   @Input() enableBrowseData3: boolean = true;
   @Input() valueFieldType2: string = XSchema.STRING;
   @Input() valueFieldType3: string = XSchema.STRING;
   @Input() dataFunction2: () => Observable<string[]>;
   @Input() dataFunction3: () => Observable<string[]>;
   @Input() varShowDate: boolean = false;
   @Input() datasource: string;
   @Input() isWSQuery: boolean;
   @Output() valuesChange: EventEmitter<ClauseValueModel[]> = new EventEmitter<ClauseValueModel[]>();
}
