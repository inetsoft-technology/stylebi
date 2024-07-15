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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { LinkType } from "../../../../common/data/hyperlink-model";
import { TestUtils } from "../../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { ModelService } from "../../../../widget/services/model.service";
import { DefaultScaleService } from "../../../../widget/services/scale/default-scale-service";
import { ScaleService } from "../../../../widget/services/scale/scale-service";
import { AssemblyActionFactory } from "../../../action/assembly-action-factory.service";
import { ImageActions } from "../../../action/image-actions";
import { ContextProvider, ViewerContextProviderFactory } from "../../../context-provider.service";
import { ViewsheetInfo } from "../../../data/viewsheet-info";
import { RichTextService } from "../../../dialog/rich-text-dialog/rich-text.service";
import { VSImageModel } from "../../../model/output/vs-image-model";
import { ShowHyperlinkService } from "../../../show-hyperlink.service";
import { VSAnnotation } from "../../annotation/vs-annotation.component";
import { DataTipService } from "../../data-tip/data-tip.service";
import { PopComponentService } from "../../data-tip/pop-component.service";
import { VSImage } from "./vs-image.component";

declare const window;

xdescribe("VSImage", () => {
   const createModel: () => VSImageModel = () => {
      return Object.assign({
         hyperlinks: [],
         noImageFlag: false,
         alpha: "0.6",
      }, TestUtils.createMockVSImageModel("Image1"));
   };

   let model: VSImageModel;
   let actions: ImageActions;
   let viewsheetClient: any;
   let modalService: any;
   let dropdownService: any;
   let popComponentService: any;
   let contextProvider: any;
   let dataTipService: any;
   let modelService: any;
   let router: any;
   let richTextService: any;

   beforeEach(() => {
      model = createModel();
      actions = new ImageActions(model, ViewerContextProviderFactory(false));

      viewsheetClient = { sendEvent: jest.fn() };
      viewsheetClient.runtimeId = "Viewsheet1";
      modalService = { open: jest.fn() };
      dropdownService = { open: jest.fn() };
      popComponentService = { isPopComponent: jest.fn() };
      let assemblyActionFactory = { createActions: jest.fn() };
      contextProvider = { viewer: true, composer: false, preview: false, binding: false };
      dataTipService = { isDataTip: jest.fn() };
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
         ],
         declarations: [
            VSImage,
            VSAnnotation
         ],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: ViewsheetClientService, useValue: viewsheetClient },
            { provide: NgbModal, useValue: modalService },
            { provide: FixedDropdownService, useValue: dropdownService },
            { provide: PopComponentService, useValue: popComponentService },
            { provide: AssemblyActionFactory, useValue: assemblyActionFactory },
            { provide: ContextProvider, useValue: contextProvider },
            { provide: DataTipService, useValue: dataTipService },
            { provide: ScaleService, useClass: DefaultScaleService },
            { provide: ModelService, useValue: modelService },
            { provide: Router, useValue: router },
            ShowHyperlinkService, DebounceService,
            { provide: RichTextService, useValue: richTextService }
         ]
      });
      TestBed.compileComponents();
   });

   // Bug #17228
   it("should open hyperlink with self target in same window", () => {
      const oldOpen = window.open;

      try {
         model.hyperlinks = [{
            name: "Home",
            label: "Home",
            linkType: LinkType.WEB_LINK,
            link: "http://www.inetsoft.com",
            targetFrame: "self",
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

         const fixture = TestBed.createComponent(VSImage);
         fixture.componentInstance.actions = actions;
         fixture.componentInstance.model = model;
         fixture.componentInstance.vsInfo = new ViewsheetInfo([], "/link/");
         fixture.detectChanges();

         window.open = jest.fn();

         const action = actions.clickAction;
         expect(action).toBeTruthy();
         expect(action.id()).toBe("image show-hyperlink");
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

         const fixture = TestBed.createComponent(VSImage);
         fixture.componentInstance.actions = actions;
         fixture.componentInstance.model = model;
         fixture.detectChanges();

         window.open = jest.fn();

         const action = actions.clickAction;
         expect(action).toBeTruthy();
         expect(action.id()).toBe("image show-hyperlink");
         action.action(null);

         expect(window.open).toHaveBeenCalledWith("http://www.inetsoft.com/", "inetsoft");
      }
      finally {
         window.open = oldOpen;
      }
   });

   // Bug #17807
   it("should not have annotations with parents that have hidden overflow", () => {
      contextProvider.viewer = true;
      contextProvider.preview = false;
      model.assemblyAnnotationModels = [
         <any> TestUtils.createMockVSObjectModel("VSAnnotation", "mockAnnotation")
      ];

      const fixture = TestBed.createComponent(VSImage);
      fixture.componentInstance.actions = actions;
      fixture.componentInstance.model = model;
      fixture.detectChanges();
      let debugElement = fixture.debugElement.query(By.directive(VSAnnotation));
      expect(debugElement).not.toBeNull();

      while(debugElement) {
         const style = window.getComputedStyle(debugElement.nativeElement);
         expect(style.getPropertyValue("overflow")).not.toBe("hidden");
         debugElement = debugElement.parent;
      }
   });

   // Bug #19028 should apply alpha on image
   // Bug #20250 should apply data tip alpha
   xit("should apply correct alpha on image", () => {
      contextProvider.viewer = true;
      contextProvider.preview = false;
      model.alpha = "0.5";
      model.objectFormat.alpha = 0.3;
      const fixture = TestBed.createComponent(VSImage);
      fixture.componentInstance.model = model;
      fixture.componentInstance.vsInfo = new ViewsheetInfo([], "/link/");
      fixture.detectChanges();

      let image = fixture.nativeElement.querySelector("img.image-content");
      expect(image.style["opacity"]).toBe("0.5");

      dataTipService.isDataTip.mockImplementation(() => true);
      contextProvider.preview = true;
      fixture.detectChanges();

      image = fixture.nativeElement.querySelector("img.image-content");
      expect(image.style["opacity"]).toBe("0.3");
   });

   // Bug #20479 should apply border
   it("should apply border on image", () => {
      contextProvider.viewer = true;
      contextProvider.preview = false;
      model.scaleInfo = {
         tiled: false,
         scaleImage: true,
         preserveAspectRatio: false
      };
      model.objectFormat.width = 167;
      model.objectFormat.height = 145;
      model.objectFormat.border = {
         bottom: "3px solid #993300", top: "1px dashed #99cc00",
         left: "3px double #ff0000", right: "1px solid #0000ff"};
      const fixture = TestBed.createComponent(VSImage);
      fixture.componentInstance.model = model;
      fixture.componentInstance.vsInfo = new ViewsheetInfo([], "/link/");
      fixture.detectChanges();

      let image = fixture.nativeElement.querySelector("img.image-content");
      expect(image.style["width"]).toBe("163px");
      expect(image.style["height"]).toBe("141px");
   });

   // Bug #20755 should apply shadow
   it("should apply shadow on image", () => {
      contextProvider.viewer = true;
      contextProvider.preview = false;
      model.shadow = true;
      model.animateGif = true;
      const fixture = TestBed.createComponent(VSImage);
      fixture.componentInstance.model = model;
      fixture.componentInstance.vsInfo = new ViewsheetInfo([], "/link/");
      fixture.detectChanges();

      let image = fixture.nativeElement.querySelector("img.image-content");
      expect(image.classList).toContain("image-content-shadow");
   });
});
