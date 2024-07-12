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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { ViewsheetOptionsPaneModel } from "../../data/vs/viewsheet-options-pane-model";
import { ViewsheetOptionsPane } from "./viewsheet-options-pane.component";
import { ViewsheetParametersDialog } from "./viewsheet-parameters-dialog.component";
import { TestUtils } from "../../../common/test/test-utils";

let createModel: () => ViewsheetOptionsPaneModel = () => {
   return {
      useMetaData: false,
      promptForParams: true,
      selectionAssociation: true,
      createMv: false,
      onDemandMvEnabled: false,
      maxRows: 50000,
      analysisMaxrow: 5000,
      alias: null,
      snapGrid: 20,
      desc: null,
      serverSideUpdate: true,
      touchInterval: 2,
      listOnPortalTree: true,
      worksheet: false,
      viewsheetParametersDialogModel: {
         enabledParameters: null,
         disabledParameters: null
      },
      selectDataSourceDialogModel: {
         title: null,
         dataSource: null
      }
   };
};

describe("Viewsheet Options Pane Unit Test", () => {
   let fixture: ComponentFixture<ViewsheetOptionsPane>;
   let vsOptionPane: ViewsheetOptionsPane;
   let dataSize: HTMLInputElement;
   let clearBtn: HTMLInputElement;
   let datasourceText;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ ReactiveFormsModule, FormsModule, NgbModule ],
         declarations: [ ViewsheetOptionsPane, ViewsheetParametersDialog, EnterSubmitDirective ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(ViewsheetOptionsPane);
      vsOptionPane = <ViewsheetOptionsPane>fixture.componentInstance;
      vsOptionPane.model = createModel();
      vsOptionPane.form = new FormGroup({});
      fixture.detectChanges();
   });

   //#17036,the design mode data size should be disable when use worksheet
   // Bug #17303 Clear button should be enabled when has datasource
   // Bug #10157 Clear button should clear the selected datasource
   // Bug #20438 should display compelete path for global ws
   xit("Design mode data size clear and data source text status", () => {
      dataSize  = fixture.nativeElement.querySelector("input[ng-reflect-name=maxRows]");
      clearBtn = fixture.nativeElement.querySelectorAll("button.btn-default")[1];
      datasourceText = fixture.nativeElement.querySelector("div.input-with-actions input");
      fixture.detectChanges();
      expect(dataSize.disabled).toBeTruthy();
      expect(clearBtn.disabled).toBeTruthy();

      vsOptionPane.model.selectDataSourceDialogModel.dataSource = {
         type: AssetType.QUERY,
         path: "orders/customers"
      }as AssetEntry;
      fixture.detectChanges();
      expect(dataSize.disabled).toBeFalsy();
      expect(clearBtn.hasAttribute("disabled")).toBeFalsy();
      expect(datasourceText.getAttribute("ng-reflect-model")).toBe("orders/customers");

      vsOptionPane.clear();
      fixture.detectChanges();
      expect(datasourceText.hasAttribute("ng-reflect-model")).toBeFalsy();

      vsOptionPane.model.selectDataSourceDialogModel.dataSource = {
         identifier: "id",
         type: AssetType.WORKSHEET
      }as AssetEntry;
      vsOptionPane.model.worksheet = true;
      fixture.detectChanges();
      expect(dataSize.disabled).toBeTruthy();

      const dataSizeLabel = fixture.debugElement.query(By.css("div.form-floating.col-12 span")).nativeElement;
      expect(TestUtils.toString(dataSizeLabel.textContent)).toBe("Worksheet design mode sample data size");

      vsOptionPane.model.selectDataSourceDialogModel.dataSource = {
         description: "Global Worksheet/Sales/Projection",
         folder: false,
         identifier: "1^2^__NULL__^Sales/Projection",
         path: "Sales/Projection",
         scope: 1,
         type: AssetType.WORKSHEET
      }as AssetEntry;
      vsOptionPane.model.worksheet = true;
      fixture.detectChanges();
      datasourceText = fixture.nativeElement.querySelector("div.input-with-actions input");
      expect(datasourceText.getAttribute("ng-reflect-model")).toBe("Global Worksheet/Sales/Project");
   });

   //#17037, Bug #18366 the design mode data size input check
   xit("Design mode data size input value check", () => {
      dataSize  = fixture.nativeElement.querySelector("input[ng-reflect-name=maxRows]");
      vsOptionPane.model.selectDataSourceDialogModel.dataSource = {
         type: AssetType.QUERY
      }as AssetEntry;

      fixture.detectChanges();
      dataSize.value = "";
      dataSize.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      expect(dataSize.getAttribute("class")).toContain("is-invalid");

      dataSize.value = "0";
      dataSize.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      expect(dataSize.getAttribute("class")).toContain("is-invalid");

      dataSize.value = "5";
      dataSize.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      expect(dataSize.getAttribute("class")).not.toContain("is-invalid");

      dataSize.value = "1.5";
      dataSize.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      expect(dataSize.getAttribute("class")).toContain("is-invalid");
   });
});
