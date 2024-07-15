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
import { HttpClient } from "@angular/common/http";
import {
   HttpClientTestingModule,
   HttpTestingController
} from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { UIContextService } from "../../common/services/ui-context.service";
import { DropDownTestModule } from "../../common/test/test-module";
import { TestUtils } from "../../common/test/test-utils";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DynamicComboBox } from "../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { BindingService } from "../services/binding.service";
import { AggregateFormula } from "../util/aggregate-formula";
import { FormulaOption } from "./formula-option.component";

describe("Formula Option Unit Test", () => {
   let fixture: ComponentFixture<FormulaOption>;
   let formulaOption: FormulaOption;
   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, HttpClientTestingModule, DropDownTestModule
         ],
         declarations: [
            FormulaOption, DynamicComboBox, FixedDropdownDirective
         ],
         providers: [
            BindingService, UIContextService
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
   }));

   //Bug #19370 with combobox should be kept when using expression
   it("with combobox should be kept when using expression", () => {
      fixture = TestBed.createComponent(FormulaOption);
      formulaOption = <FormulaOption>fixture.componentInstance;
      formulaOption.vsId = "crosstab-15115043146760";
      formulaOption.variables = [];
      formulaOption.availableFields = [TestUtils.createMockDataRef("state"), TestUtils.createMockDataRef("id")];
      formulaOption.formulaObjs = [{
         label: "Correlation", value: "Correlation"
      }];
      formulaOption.aggregate = TestUtils.createMockBAggregateRef("id");
      formulaOption.aggregate.formula = AggregateFormula.CORRELATION.formulaName;
      formulaOption.changeSecondColumnValue("={id}");
      fixture.detectChanges();

      expect(formulaOption.aggregate.secondaryColumn).toBeNull();
      expect(formulaOption.aggregate.secondaryColumnValue).toEqual("={id}");
   });

   //Bug #20322
   it("should show aggregate combobox if formulaOptionModel is null", () => {
      fixture = TestBed.createComponent(FormulaOption);
      formulaOption = <FormulaOption>fixture.componentInstance;
      let agg = TestUtils.createMockBAggregateRef("id");
      agg.formulaOptionModel = null;
      formulaOption.aggregate = agg;
      formulaOption.availableFields = [TestUtils.createMockDataRef("state")];
      formulaOption.formulaObjs = [{
         label: "Sum", value: "Sum"
      }];
      fixture.detectChanges();

      let aggComb = fixture.nativeElement.querySelector(".aggregate_id dynamic-combo-box");
      expect(aggComb).not.toBeNull();
   });
});