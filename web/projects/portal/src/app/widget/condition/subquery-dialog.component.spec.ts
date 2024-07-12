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
import { AfterViewInit, Component, NO_ERRORS_SCHEMA, TemplateRef, ViewChild } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { SubqueryValue } from "../../common/data/condition/subquery-value";
import { SubqueryDialog } from "./subquery-dialog.component";

@Component({
   selector: "test-app",
   template: `
     <ng-template #subqueryDialog let-close="close" let-dismiss="dismiss">
       <subquery-dialog (onCommit)="close($event)" (onCancel)="dismiss($event)"
                        [subqueryTables]="subqueryTables"
                        [value]="value"></subquery-dialog>
     </ng-template>
   `
})
class TestApp implements AfterViewInit {
   @ViewChild("subqueryDialog", {static: false}) subqueryDialog: TemplateRef<any>;
   subqueryTables: SubqueryTable[];
   value: SubqueryValue;

   constructor(private modalService: NgbModal) {
   }

   ngAfterViewInit() {
      this.modalService.open(this.subqueryDialog).result
         .then(() => {}, () => {});
   }
}

describe("Subquery Dialog Tests", () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            NgbModule,
            FormsModule
         ],
         providers: [
            NgbModal
         ],
         declarations: [
            TestApp,
            SubqueryDialog
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      });
      TestBed.compileComponents();
   });

   // Bug #9968
   it("a single element SubqueryTable should not break the component", () => {
      let fixture = TestBed.createComponent(TestApp);
      fixture.componentInstance.subqueryTables = [
         {
            name: "table name",
            description: null,
            columns: [],
            currentTable: true
         }
      ];

      fixture.detectChanges(true);
   });
});
