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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ComboBox } from "../../../format/objects/combo-box.component";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { ColumnTypeDialog } from "./column-type-dialog.component";
import { ColumnInfo } from "../../data/ws/column-info";

describe("Column Type Dialog Unit Test", () => {
   let columnInfo: ColumnInfo = {
      ref: {
         attribute: "col0",
         classType: "ColumnRef",
         name: "col0",
         dataType: "string",
         dataRefModel: {},
         alias: null,
         width: 1,
         visible: true,
         valid: true,
         sql: true,
         description: ""
      },
      name: "col0",
      alias: null,
      assembly: "Query1",
      format: "",
      header: "col0",
      visible: true,
      aggregate: false,
      group: false,
      crosstab: false,
      sortType: 0,
      width: 150,
      timeSeries: false,
      index: 0
   };

   let fixture: ComponentFixture<ColumnTypeDialog>;
   let columnTypeDialog: ColumnTypeDialog;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            ColumnTypeDialog, EnterSubmitDirective, ComboBox
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(ColumnTypeDialog);
      columnTypeDialog = <ColumnTypeDialog>fixture.componentInstance;
      columnTypeDialog.colInfo = columnInfo;
      fixture.detectChanges();
   }));

   //Bug #9171 presets should differentiate Date Time and TimeInstant
   it("presets should differentiate Date Time and TimeInstant", () => {
      let type = fixture.nativeElement.querySelector("select#dataType");
      type.value = "date";
      type.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      let formatSpec = fixture.nativeElement.querySelectorAll(
         "combo-box#formatSpec select option");
      expect(formatSpec.length).toBe(12);

      type.value = "time";
      type.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      formatSpec = fixture.nativeElement.querySelectorAll(
         "combo-box#formatSpec select option");
      expect(formatSpec.length).toBe(4);

      type.value = "timeInstant";
      type.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      formatSpec = fixture.nativeElement.querySelectorAll(
         "combo-box#formatSpec select option");
      expect(formatSpec.length).toBe(3);
   });
});