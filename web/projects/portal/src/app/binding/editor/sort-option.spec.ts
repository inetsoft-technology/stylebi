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
import { HttpParams, HttpResponse } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { XSchema } from "../../common/data/xschema";
import { UIContextService } from "../../common/services/ui-context.service";
import { DropDownTestModule } from "../../common/test/test-module";
import { TestUtils } from "../../common/test/test-utils";
import { StyleConstants } from "../../common/util/style-constants";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DynamicComboBox } from "../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { DragService } from "../../widget/services/drag.service";
import { ModelService } from "../../widget/services/model.service";
import { NamedGroupInfo } from "../data/named-group-info";
import { BindingService } from "../services/binding.service";
import { SortOption } from "./sort-option.component";
import { FeatureFlagDirective } from "../../../../../shared/feature-flags/feature-flag.directive";
import { FeatureFlagsService } from "../../../../../shared/feature-flags/feature-flags.service";

const BESIC_SORTOPTION: any[] = ["None", "Ascending", "Descending"];
const AGG_SORTOPTION: any[] = ["common.widget.SortOption.byAsc",
                               "common.widget.SortOption.byDesc"];
const SPEC_SORTOPTION: any[] = ["Manual"];

describe("Sort Option Unit Test", () => {
   let bindingModel = TestUtils.createMockBindingModel("chart");
   bindingModel.source.source = "test";
   const bindingService: any = { getURLParams: jest.fn(() => new HttpParams()), bindingModel: bindingModel };

   const modelService: any = {
      getModel: jest.fn(() => observableOf([])),
      putModel: jest.fn(() => observableOf(new HttpResponse({body: null})))
   };
   const uiContextService: any = { isAdhoc: jest.fn() };
   const modalService: any = { open: jest.fn() };
   const featureFlagsService: any = { isFeatureEnabled: jest.fn() };

   let fixture: ComponentFixture<SortOption>;
   let sortOption: SortOption;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            SortOption, DynamicComboBox, FixedDropdownDirective
         ],
         providers: [{
               provide: BindingService, useValue: bindingService
            },
            {
               provide: ModelService, useValue: modelService
            },
            {
               provide: UIContextService, useValue: uiContextService
            },
            {
               provide: NgbModal, useValue: modalService
            },
            DragService,
            {
               provide: FeatureFlagsService, useValue: featureFlagsService
            }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }));

   function checkDisableStatus(elem: Element, disable: boolean): void {
      let flag = "false";
      if(disable) {
         flag = "true";
      }
      expect(elem.getAttribute("ng-reflect-disable")).toEqual(flag);
   }

   //for Bug #10416, Bug #10615, Bug #10241, Bug #18018, Bug #10623 should not load Manual in sort combobox if group is date type
   it("Test sort combobox load", () => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      sortOption = new SortOption(bindingService, modelService, modalService, uiContextService, featureFlagsService);
      sortOption.dimension = TestUtils.createMockBDimensionRef("state");
      sortOption.ngOnInit();

      expect(sortOption.sortOrders.map(item => (TestUtils.toString(item.label))))
         .toEqual(BESIC_SORTOPTION.concat(SPEC_SORTOPTION));

      // Bug #55062, should load manual/named group when not a chart and date type
      sortOption.dimension.dataType = XSchema.DATE;
      sortOption.ngOnInit();
      expect(sortOption.sortOrders.map(item => (TestUtils.toString(item.label))))
         .toEqual(BESIC_SORTOPTION.concat(SPEC_SORTOPTION));


      uiContextService.isAdhoc.mockImplementation(() => true);
      sortOption.ngOnInit();
      expect(sortOption.sortOrders.map(item => (TestUtils.toString(item.label))))
         .toEqual(BESIC_SORTOPTION.concat(SPEC_SORTOPTION));

      let aggMap: any = {
         label: "Sum(id)",
         value: "Sum(id)"
      };
      sortOption.dimension.sortOptionModel.aggregateRefs = [aggMap];
      sortOption.ngOnInit();
      expect(sortOption.sortOrders.map(item => (TestUtils.toString(item.label))))
         .toEqual(BESIC_SORTOPTION.concat(AGG_SORTOPTION).concat(SPEC_SORTOPTION));

      sortOption.dimension.dataType = XSchema.STRING;
      sortOption.ngOnInit();
      expect(sortOption.sortOrders.map(item => (TestUtils.toString(item.label)))).toEqual(BESIC_SORTOPTION.concat(AGG_SORTOPTION).concat(SPEC_SORTOPTION));

      uiContextService.isAdhoc.mockImplementation(() => false);
      sortOption.ngOnInit();
      expect(sortOption.sortOrders.map(item => (TestUtils.toString(item.label)))).toEqual(BESIC_SORTOPTION.concat(AGG_SORTOPTION).concat(SPEC_SORTOPTION));
   });

   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Test ranking combobox disable status", () => {
   //    let dimRef = TestUtils.createMockBDimensionRef("state");
   //    uiContextService.isAdhoc.mockImplementation(() => false);
   //    fixture = TestBed.createComponent(SortOption);
   //    sortOption = <SortOption>fixture.componentInstance;
   //    sortOption.dimension = dimRef;
   //    sortOption.vsId = "crosstab-15042540419170";
   //    fixture.detectChanges();
   //
   //    let rankingCombo: Element = fixture.nativeElement.querySelector(".ranking_id dynamic-combo-box");
   //    checkDisableStatus(rankingCombo, true);
   //
   //    let aggMap: any = {
   //       label: "Sum(id)",
   //       value: "Sum(id)"
   //    };
   //    dimRef.sortOptionModel.aggregateRefs = [aggMap];
   //    let fixture1 = TestBed.createComponent(SortOption);
   //    sortOption = <SortOption>fixture1.componentInstance;
   //    sortOption.dimension = dimRef;
   //    sortOption.vsId = "crosstab-15042540419170";
   //    fixture1.detectChanges();
   //
   //    rankingCombo = fixture1.nativeElement.querySelector(".ranking_id dynamic-combo-box");
   //    checkDisableStatus(rankingCombo, false);
   //
   //    const rankingComboDiv = TestUtils.getDynamicComboDiv(fixture1.nativeElement.querySelector(".ranking_id"));
   //    expect(TestUtils.toString((rankingComboDiv.textContent.trim()))).toEqual("None");
   // });

   //for Bug #19186, rankingCol and rankingN is not right.
   it("Test ranking N status and  ranking Of status", () => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      fixture = TestBed.createComponent(SortOption);
      sortOption = <SortOption>fixture.componentInstance;
      let aggMap: any = {
         label: "Sum(id)",
         value: "Sum(id)"
      };
      sortOption.dimension = TestUtils.createMockBDimensionRef("state");
      sortOption.dimension.sortOptionModel.aggregateRefs = [aggMap];
      sortOption.vsId = "crosstab-15042540419170";
      fixture.detectChanges();

      let topNInput: Element = fixture.nativeElement.querySelector(".top_n_id dynamic-combo-box");
      let ofCombo: Element = fixture.nativeElement.querySelector(".ranking_of_id dynamic-combo-box");
      let groupOthers: HTMLInputElement = fixture.nativeElement.querySelector(".group_other_id input[type=checkbox]");
      checkDisableStatus(topNInput, true);
      checkDisableStatus(ofCombo, true);
      expect(groupOthers.getAttribute("ng-reflect-is-disabled")).toEqual("true");

      sortOption.changeRankingOption(StyleConstants.TOP_N + "");
      fixture.detectChanges();

      topNInput = fixture.nativeElement.querySelector(".top_n_id dynamic-combo-box");
      ofCombo = fixture.nativeElement.querySelector(".ranking_of_id dynamic-combo-box");
      groupOthers = fixture.nativeElement.querySelector(".group_other_id input[type=checkbox]");
      checkDisableStatus(topNInput, false);
      checkDisableStatus(ofCombo, false);
      expect(groupOthers.getAttribute("ng-reflect-is-disabled")).toEqual("false");

      sortOption.changeRankingOption("$(RadioButton1)");
      fixture.detectChanges();

      topNInput = fixture.nativeElement.querySelector(".top_n_id dynamic-combo-box");
      ofCombo = fixture.nativeElement.querySelector(".ranking_of_id dynamic-combo-box");
      checkDisableStatus(topNInput, false);
      checkDisableStatus(ofCombo, false);
   });

   //for Bug #10625, ranking inputfield should not accept invalid number
   //test broken, not properly initialized
   xit("Test ranking input value and valid check", () => {
      uiContextService.isAdhoc.mockImplementation(() => true);
      fixture = TestBed.createComponent(SortOption);
      sortOption = <SortOption>fixture.componentInstance;
      let aggMap: any = {
         label: "Sum(id)",
         value: "Sum(id)"
      };
      sortOption.dimension = TestUtils.createMockBDimensionRef("state");
      sortOption.dimension.sortOptionModel.aggregateRefs = [aggMap];
      sortOption.dimension.rankingOption = StyleConstants.TOP_N + "";
      fixture.detectChanges();
      sortOption.changeRankingN("-1");
      fixture.detectChanges();

      let errorMesg: Element = fixture.nativeElement.querySelector(".alert-danger");
      expect(TestUtils.toString(errorMesg.textContent.trim()))
         .toEqual("common.widget.SortOption.enterPositiveTop");

      uiContextService.isAdhoc.mockImplementation(() => false);
      sortOption.vsId = "crosstab-15042540419170";
      fixture.detectChanges();
      sortOption.changeRankingN("0");
      fixture.detectChanges();

      errorMesg = fixture.nativeElement.querySelector(".alert-danger");
      expect(TestUtils.toString(errorMesg.textContent.trim()))
         .toEqual("common.widget.SortOption.enterPositiveTop");
   });

   //Bug #10619, Bug #16544, Bug #16167
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Test ranking Of combobox value load", (done) => {
   //    uiContextService.isAdhoc.mockImplementation(() => false);
   //    fixture = TestBed.createComponent(SortOption);
   //    sortOption = <SortOption>fixture.componentInstance;
   //    sortOption.dimension = TestUtils.createMockBDimensionRef("state");
   //    let aggMap: any = {
   //       label: "Sum(id)",
   //       value: "Sum(id)"
   //    };
   //    sortOption.dimension.sortOptionModel.aggregateRefs = [aggMap];
   //    sortOption.dimension.rankingOption = StyleConstants.TOP_N + "";
   //    sortOption.vsId = "crosstab-15042540419170";
   //    fixture.detectChanges();
   //
   //    let ofCombo: HTMLElement = TestUtils.getDynamicComboDiv(fixture.nativeElement.querySelector(".ranking_of_id"));
   //    ofCombo.click();
   //    fixture.detectChanges();
   //
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let ofItems: any = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
   //       expect(ofItems.length).toEqual(1);
   //
   //       done();
   //    });
   // });

   // for Bug #16126, sort summarize combobox load empty
   // bad test, don't test external components and use debug elements to test dom when necessary
   // it("Test sort by col combobox load", (done) => {
   //    uiContextService.isAdhoc.mockImplementation(() => true);
   //    fixture = TestBed.createComponent(SortOption);
   //    sortOption = <SortOption>fixture.componentInstance;
   //    sortOption.dimension = TestUtils.createMockBDimensionRef("state");
   //    let aggMap: any[] = [{
   //       label: "Sum(id1)",
   //       value: "Sum(id1)"
   //    },
   //    {
   //       label: "Sum(id2)",
   //       value: "Sum(id2)"
   //    }];
   //    sortOption.dimension.sortOptionModel.aggregateRefs = aggMap;
   //    sortOption.dimension.order = StyleConstants.SORT_VALUE_ASC;
   //    fixture.detectChanges();
   //
   //    let sortByCombo: Element = fixture.nativeElement.querySelector(".sort_by_id dynamic-combo-box");
   //    expect(sortByCombo).not.toBeNull();
   //
   //    let toggle: HTMLElement = TestUtils.getDynamicComboDiv(sortByCombo);
   //    toggle.click();
   //    fixture.detectChanges();
   //
   //    fixture.whenStable().then(() => {
   //       let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
   //       let sortByItems: any = fixedDropdown.querySelectorAll("div.dropdown-container .dropdown-item");
   //       expect(sortByItems.length).toEqual(2);
   //
   //       done();
   //    });
   // });

   //for Bug #10468, Bug #16140, named group combobox load empty, Custom not displayed.
   it("Named group combobox load empty, 'Custom' not displayed", () => {
      uiContextService.isAdhoc.mockImplementation(() => true);
      fixture = TestBed.createComponent(SortOption);
      sortOption = <SortOption>fixture.componentInstance;
      sortOption.dimension = TestUtils.createMockBDimensionRef("state");
      sortOption.dimension.order = 8;
      sortOption.dimension.namedGroupInfo = {
         type: NamedGroupInfo.SIMPLE_NAMEDGROUP_INFO
      } as NamedGroupInfo;
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let namedGroupCombo: Element = fixture.nativeElement.querySelector(".named_group_id select");
         let groupOthers = fixture.nativeElement.querySelectorAll("input[type=checkbox]");
         expect(namedGroupCombo).not.toBeNull();
         expect(namedGroupCombo.getAttribute("ng-reflect-model")).toEqual("Custom");
         expect(groupOthers.length).toEqual(2);
      });
   });

   //for Bug #16203, Bug #17714, Bug #17812, Bug #19402 ranking of default value show error
   it("Test ranking input and of default value when ranking is top", () => {
      sortOption = new SortOption(bindingService, modelService, modalService, uiContextService, featureFlagsService);
      sortOption.dimension = TestUtils.createMockBDimensionRef("state");
      let aggMap: any = {
         label: "Sum(id)",
         value: "Sum(id)"
      };
      sortOption.dimension.sortOptionModel.aggregateRefs = [aggMap];
      sortOption.ngOnInit();

      sortOption.changeRankingOption((StyleConstants.TOP_N + ""));
      expect(sortOption.dimension.rankingOption).toEqual((StyleConstants.TOP_N + ""));
      expect(sortOption.dimension.rankingCol).toEqual("Sum(id)");
      expect(sortOption.dimension.rankingN).toEqual("3");

      sortOption.changeRankingOption((StyleConstants.NONE + ""));
      expect(sortOption.dimension.rankingOption).toBeNull();
      expect(sortOption.dimension.rankingCol).toEqual("Sum(id)");
      expect(sortOption.dimension.rankingN).toEqual("3");
   });

   //for Bug #19289
   it("Test sort summarize combobox default value", () => {
      sortOption = new SortOption(bindingService, modelService, modalService, uiContextService, featureFlagsService);
      uiContextService.isAdhoc.mockImplementation(() => false);
      sortOption.dimension = TestUtils.createMockBDimensionRef("state");
      let aggMap: any = {
         label: "Sum(id)",
         value: "Sum(id)"
      };
      sortOption.dimension.sortOptionModel.aggregateRefs = [aggMap];
      sortOption.ngOnInit();
      sortOption.changeOrderType(StyleConstants.SORT_VALUE_ASC);

      expect(sortOption.dimension.order).toEqual(StyleConstants.SORT_VALUE_ASC);
      expect(sortOption.dimension.sortByCol).toEqual("Sum(id)");
      expect(sortOption.dimension.timeSeries).toEqual(false);
   });

   //for Bug #19350, Bug #19346, Bug #20333 Should show rankingCol and rankingN value only if dynamic combobox is value mode
   it("Should show rankingCol and rankingN value only if dynamic combobox is value mode", (done) => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      fixture = TestBed.createComponent(SortOption);
      sortOption = <SortOption>fixture.componentInstance;
      sortOption.dimension = TestUtils.createMockBDimensionRef("state");
      let aggMap: any = {
         label: "Sum(id)",
         value: "Sum(id)"
      };
      sortOption.dimension.sortOptionModel.aggregateRefs = [aggMap];
      sortOption.dimension.order = StyleConstants.SORT_VALUE_ASC;
      sortOption.dimension.rankingOption = StyleConstants.TOP_N + "";
      sortOption.dimension.rankingCol = "$(RadioButton1)";
      sortOption.dimension.rankingN = "={4}";
      sortOption.dimension.sortByCol = "={'Sum(id)'}";
      fixture.detectChanges();

      expect(sortOption.dimension.rankingCol).toEqual("$(RadioButton1)");
      expect(sortOption.dimension.rankingN).toEqual("={4}");
      expect(sortOption.dimension.sortByCol).toEqual("={'Sum(id)'}");

      //Bug #20333
      let typeButton: HTMLButtonElement = fixture.nativeElement.querySelector(".top_n_id dynamic-combo-box button.type-toggle");
      typeButton.click();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
         let valueItem: any = fixedDropdown.querySelectorAll("a.dropdown-item")[0];
         valueItem.click();

         fixture.detectChanges();
         let topNInput: Element = fixture.nativeElement.querySelector(".top_n_id dynamic-combo-box input");
         expect(topNInput.hasAttribute("ng-reflect-model")).toBeFalsy();

         done();
      });
   });

   //for Bug #18828, should show default sort when remove all aggregate
   it("Should show default sort when remove all aggregate", () => {
      uiContextService.isAdhoc.mockImplementation(() => false);
      fixture = TestBed.createComponent(SortOption);
      sortOption = <SortOption>fixture.componentInstance;
      sortOption.dimension = TestUtils.createMockBDimensionRef("state");
      sortOption.dimension.order = StyleConstants.SORT_VALUE_ASC;
      fixture.detectChanges();

      expect(sortOption.dimension.order).toEqual(StyleConstants.SORT_ASC);
   });
});
