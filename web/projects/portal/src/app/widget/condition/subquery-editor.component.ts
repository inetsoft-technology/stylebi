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
import {
   Component,
   Input,
   Output,
   EventEmitter,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { SubqueryValue } from "../../common/data/condition/subquery-value";

@Component({
   selector: "subquery-editor",
   templateUrl: "subquery-editor.component.html"
})
export class SubqueryEditor {
   @Input() subqueryTables: SubqueryTable[];
   @Input() value: SubqueryValue;
   @Input() showOriginalName: boolean = false;
   @Output() valueChange: EventEmitter<SubqueryValue> = new EventEmitter<SubqueryValue>();
   @ViewChild("subqueryDialog") subqueryDialog: TemplateRef<any>;

   constructor(private modalService: NgbModal) {
   }

   openSubqueryDialog(): void {
      this.modalService.open(this.subqueryDialog, {backdrop: false}).result.then(
         (result: SubqueryValue) => {
            this.value = result;
            this.valueChange.emit(this.value);
         }, () => {}
      );
   }
}
