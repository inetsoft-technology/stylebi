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
import {
   Component, Input, Output, EventEmitter, ViewChild, ElementRef,
   AfterViewInit, OnInit
} from "@angular/core";
import { InlineViewDialogModel } from "./inline-view-dialog-model";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { GetSqlColumnsEvent } from "../../data/model/datasources/database/events/get-sql-columns-event";
import { ComponentTool } from "../../../common/util/component-tool";
import { UntypedFormControl, FormGroup } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";

const SQL_TABLE_COLUMNS_URI: string = "../api/data/physicalmodel/views/columns";

@Component({
   selector: "inline-view-dialog",
   templateUrl: "inline-view-dialog.component.html"
})
export class InlineViewDialog implements OnInit, AfterViewInit {
   @Input() model: InlineViewDialogModel;
   @Input() databaseName: string;
   @Input() additional: string;
   @Input() hasDuplicateCheck: (name: string) => Observable<boolean>;
   @Output() onCommit: EventEmitter<InlineViewDialogModel> =
      new EventEmitter<InlineViewDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("input") input: ElementRef;
   sqlInvalid: boolean = false;
   nameCtrl: UntypedFormControl;

   constructor(private httpClient: HttpClient, private modalService: NgbModal) {
   }

   ngOnInit() {
      this.nameCtrl = new UntypedFormControl(this.model?.name, [
         FormValidators.required,
         FormValidators.invalidDataModelName]);
   }

   ngAfterViewInit() {
      this.input.nativeElement.focus();
      this.input.nativeElement.select();
   }

   /**
    * Expression was changed inside sql querh editor. Update expression and reset validity.
    * @param expression
    */
   expressionChanged(expression: string): void {
      if(expression != this.model.sql) {
         this.model.sql = expression;
         this.sqlInvalid = false;
      }
   }

   /**
    * User clicked ok. Check if sql query is valid and then save inline view.
    */
   ok(): void {
      this.httpClient.post<string[]>(SQL_TABLE_COLUMNS_URI,
                                     new GetSqlColumnsEvent(this.model.sql, this.databaseName, this.additional))
         .subscribe(
            data => {
               this.saveInlineView();
            },
            err => {
               this.sqlInvalid = true;
            }
         );
   }

   /**
    * Check if the view name is a duplicate then commit the current model.
    */
   private saveInlineView(): void {
      this.hasDuplicateCheck(this.model.name).subscribe(
         (duplicate: boolean) => {
            if(duplicate) {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                                      "_#(js:data.physicalmodel.inlineViewNameDuplicate)");
            }
            else {
               this.onCommit.emit(this.model);
            }
         },
      );
   }

   /**
    * Close the dialog.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
