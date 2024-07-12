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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { LinkType } from "../../../../common/data/hyperlink-model";
import { TestUtils } from "../../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { ViewDataService } from "../../../../viewer/services/view-data.service";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ModelService } from "../../../../widget/services/model.service";
import { GaugeActions } from "../../../action/gauge-actions";
import { ContextProvider, ViewerContextProviderFactory } from "../../../context-provider.service";
import { ViewsheetInfo } from "../../../data/viewsheet-info";
import { RichTextService } from "../../../dialog/rich-text-dialog/rich-text.service";
import { VSGaugeModel } from "../../../model/output/vs-gauge-model";
import { ShowHyperlinkService } from "../../../show-hyperlink.service";
import { DataTipService } from "../../data-tip/data-tip.service";
import { PopComponentService } from "../../data-tip/pop-component.service";
import { VSGauge } from "./vs-gauge.component";

declare const window;

describe("VSGauge", () => {
   const createModel: () => VSGaugeModel = () => {
      return Object.assign({
         hyperlinks: [],
         locked: false,
         clickable: false
      }, TestUtils.createMockVSObjectModel("VSGauge", "Gauge1"));
   };

   let model: VSGaugeModel;
   let actions: GaugeActions;
   let viewsheetClient: any;
   let modalService: any;
   let dropdownService: any;
   let viewDataService: any;
   let contextProvider: any;
   let dataTipService: any;
   let popComponentService: any;
   let modelService: any;
   let router: any;
   let richTextService: any;

   beforeEach(() => {
      model = createModel();
      actions = new GaugeActions(model, ViewerContextProviderFactory(false));

      viewsheetClient = { sendEvent: jest.fn() };
      viewsheetClient.runtimeId = "Viewsheet1";
      modalService = { open: jest.fn() };
      dropdownService = { open: jest.fn() };
      contextProvider = {};
      viewDataService = {};
      dataTipService = { isDataTip: jest.fn() };
      popComponentService = { isPopComponent: jest.fn() };
      modelService = { sendModel: jest.fn() };
      router = {
         navigate: jest.fn(),
         events: new Subject<any>()
      };
      richTextService = {
         showAnnotationDialog: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule
         ],
         declarations: [
            VSGauge
         ],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            {provide: ViewsheetClientService, useValue: viewsheetClient},
            {provide: NgbModal, useValue: modalService},
            {provide: FixedDropdownService, useValue: dropdownService},
            {provide: ContextProvider, useValue: contextProvider},
            {provide: ViewDataService, useValue: viewDataService},
            {provide: DataTipService, useValue: dataTipService},
            {provide: PopComponentService, useValue: popComponentService},
            {provide: ModelService, useValue: modelService},
            { provide: Router, useValue: router },
            ShowHyperlinkService,
            { provide: RichTextService, useValue: RichTextService }
         ]
      });
      TestBed.compileComponents();
   });

   // Bug #17228
   xit("should open hyperlink with self target in same window", () => {
      const oldOpen = window.open;

      try {
         model.hyperlinks = [{
            name: "Home",
            label: "Home",
            linkType: LinkType.WEB_LINK,
            link: "http://www.inetsoft.com",
            targetFrame: "",
            query: null,
            wsIdentifier: null,
            tooltip: null,
            bookmarkName: null,
            bookmarkUser: null,
            parameterValues: [],
            sendReportParameters: false,
            sendSelectionParameters: false,
            disablePrompting: false
         }];

         const fixture = TestBed.createComponent(VSGauge);
         fixture.componentInstance.actions = actions;
         fixture.componentInstance.model = model;
         fixture.componentInstance.vsInfo = new ViewsheetInfo([], "/link/");
         fixture.detectChanges();

         window.open = jest.fn();

         const action = actions.clickAction;
         expect(action).toBeTruthy();
         expect(action.id()).toBe("gauge show-hyperlink");
         action.action(null);

         expect(window.open).toHaveBeenCalledWith("http://www.inetsoft.com/", "_self");
      }
      finally {
         window.open = oldOpen;
      }
   });

   // Bug #17228
   xit("should open hyperlink with non-self target in new window", () => {
      const oldOpen = window.open;

      try {
         model.hyperlinks = [{
            name: "Home",
            label: "Home",
            linkType: LinkType.WEB_LINK,
            link: "http://www.inetsoft.com",
            targetFrame: "inetsoft",
            query: null,
            wsIdentifier: null,
            tooltip: null,
            bookmarkName: null,
            bookmarkUser: null,
            parameterValues: [],
            sendReportParameters: false,
            sendSelectionParameters: false,
            disablePrompting: false
         }];

         const fixture = TestBed.createComponent(VSGauge);
         fixture.componentInstance.actions = actions;
         fixture.componentInstance.model = model;
         fixture.detectChanges();

         window.open = jest.fn();

         const action = actions.clickAction;
         expect(action).toBeTruthy();
         expect(action.id()).toBe("gauge show-hyperlink");
         action.action(null);

         expect(window.open).toHaveBeenCalledWith("http://www.inetsoft.com/", "inetsoft");
      }
      finally {
         window.open = oldOpen;
      }
   });

   // Bug #10675 make sure gauge img updates in viewer
   // Bug #17315 gauge not disappearing when used as a data tip
   it("should have different src url when model changes", () => {
      let fixture = TestBed.createComponent(VSGauge);
      fixture.componentInstance.model = createModel();
      fixture.componentInstance.vsInfo = new ViewsheetInfo([], "/link/");
      const src1 = fixture.componentInstance.getSrc();
      fixture.detectChanges();
      const newModel = createModel();
      newModel.genTime += 3;
      fixture.componentInstance.model = newModel;
      const src2 = fixture.componentInstance.getSrc();
      expect(src1).not.toEqual(src2);
   });

   // Bug #20250 should apply data tip alpha
   xit("should apply correct alpha on gauge", () => { // broken test
      model.objectFormat.alpha = 0.3;
      const fixture = TestBed.createComponent(VSGauge);
      fixture.componentInstance.model = model;
      fixture.componentInstance.vsInfo = new ViewsheetInfo([], "/link/");
      dataTipService.isDataTip.mockImplementation(() => true);
      contextProvider.preview = true;
      fixture.detectChanges();

      let gauge = fixture.nativeElement.querySelector("img.vs-gauge__image");
      expect(gauge.style["opacity"]).toBe("0.3");
   });
});
