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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { DebugElement, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { Router } from "@angular/router";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { TestUtils } from "../../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { ViewDataService } from "../../../../viewer/services/view-data.service";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ModelService } from "../../../../widget/services/model.service";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { ContextProvider } from "../../../context-provider.service";
import { RichTextService } from "../../../dialog/rich-text-dialog/rich-text.service";
import { VSTextModel } from "../../../model/output/vs-text-model";
import { ShowHyperlinkService } from "../../../show-hyperlink.service";
import { DataTipService } from "../../data-tip/data-tip.service";
import { PopComponentService } from "../../data-tip/pop-component.service";
import { VSText } from "./vs-text.component";

let createModel: () => VSTextModel = () => {
   return Object.assign({
      text: "text\n123",
      shadow: false,
      autoSize: false,
      url: false,
      hyperlinks: [],
      presenter: false,
      clickable: false
   }, TestUtils.createMockVSObjectModel("VSText", "VSText1"));
};

describe("VS Text Component Unit Test", () => {
   let fixture: ComponentFixture<VSText>;
   let viewsheetClientService: any;
   let dropdownService: any;
   let contextProvider: any;
   let modelService: any;
   let viewDataService: any;
   let de: DebugElement;
   let el: HTMLElement;
   let dataTipService: any;
   let router: any;
   let richTextService: any;

   beforeEach(async(() => {
      viewsheetClientService = {};
      dropdownService = {};
      modelService = { sendModel: jest.fn() };
      dataTipService = { isDataTip: jest.fn() };
      viewDataService = {};
      contextProvider = {};

      router = {
         navigate: jest.fn(),
         events: new Subject<any>()
      };
      richTextService = {
         showAnnotationDialog: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule,
            FormsModule,
            NgbModule,
            HttpClientTestingModule
         ],
         declarations: [ VSText ],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: ViewsheetClientService, useValue: viewsheetClientService },
            { provide: FixedDropdownService, useValue: dropdownService },
            { provide: ContextProvider, useValue: contextProvider },
            { provide: ViewDataService, useValue: viewDataService },
            { provide: ModelService, useValue: modelService },
            { provide: Router, useValue: router },
            PopComponentService,
            ShowHyperlinkService,
            DebounceService,
            {provide: DataTipService, useValue: dataTipService},
            { provide: RichTextService, useValue: richTextService }
         ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(VSText);
      fixture.componentInstance.model = createModel();
      fixture.componentInstance.tooltip = "mock tooltip";
      fixture.detectChanges();
   }));

   it("should have a shadow when not in edit mode and shadow checkbox is checked", () => {
      de = fixture.debugElement.query(By.css(".text-view"));
      el = de.nativeElement;

      expect(el.classList.contains("shadowText")).toBe(false);
      fixture.componentInstance.model.shadow = true;
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         expect(el.classList.contains("shadowText")).toBe(true);
      });
   });

   //Bug #21090 should handle newline
   xit("should handle newline in text", () => { // broken test
      let text = fixture.debugElement.query(By.css("div.text-view")).nativeElement;
      expect(text.textContent).toBe("text<br>123");
   });
});
