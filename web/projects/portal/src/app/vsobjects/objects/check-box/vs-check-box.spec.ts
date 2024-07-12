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
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { SsoHeartbeatService } from "../../../../../../shared/sso/sso-heartbeat.service";
import { TestUtils } from "../../../common/test/test-utils";
import { StompClientService, ViewsheetClientService } from "../../../common/viewsheet-client";
import { DefaultFocusDirective } from "../../../widget/directive/default-focus.directive";
import { InteractService } from "../../../widget/interact/interact.service";
import { InteractableDirective } from "../../../widget/interact/interactable.directive";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { ContextProvider } from "../../context-provider.service";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { VSCheckBox } from "./vs-check-box.component";

describe("vs check box component unit case", () => {
   let fixture: ComponentFixture<VSCheckBox>;
   let vsCheckBox: VSCheckBox;
   let socket: any;
   let interactService: any;
   let debounceService: any;
   let dataTipService: any;
   let dialogService: any;
   let modelService: any;
   let ssoHeartbeatService: any;

   beforeEach(() => {
      socket = { sendEvent: jest.fn() };
      interactService = {
         addInteractable: jest.fn(),
         notify: jest.fn(),
         removeInteractable: jest.fn()
      };
      const formDataService = {
         checkFormData: jest.fn(),
         removeObject: jest.fn(),
         addObject: jest.fn(),
         replaceObject: jest.fn()
      };
      debounceService = { debounce: jest.fn((key, fn, delay, args) => fn(...args)) };
      dataTipService = { isDataTip: jest.fn() };
      const contextProvider = {};
      dialogService = { open: jest.fn() };
      modelService = { getModel: jest.fn() };
      ssoHeartbeatService = { heartbeat: jest.fn() };

      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [
            VSCheckBox, VSPopComponentDirective, InteractableDirective, DefaultFocusDirective, SafeFontDirective
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ],
         providers: [
            FormInputService,
            ViewsheetClientService,
            StompClientService,
            PopComponentService,
            { provide: ContextProvider, useValue: contextProvider },
            { provide: InteractService, useValue: interactService },
            { provide: CheckFormDataService, useValue: formDataService },
            { provide: DebounceService, useValue: debounceService },
            { provide: DataTipService, useValue: dataTipService },
            { provide: DialogService, useValue: dialogService },
            { provide: ModelService, useValue: modelService },
            { provide: SsoHeartbeatService, useValue: ssoHeartbeatService }
         ]
      }).compileComponents();

      fixture = TestBed.createComponent(VSCheckBox);
   });

   //Bug #18435 should apply H aligment
   //Bug #18926 should apply V aligment
   it("should apply H|V aligment", () => {
      let checkBoxModel = TestUtils.createMockVSCheckBoxModel("checkbox1");
      let vsformat = TestUtils.createMockVSFormatModel();
      vsformat.hAlign = "right";
      vsformat.width = 50;
      vsformat.height = 18;
      vsformat.vAlign = "bottom";
      checkBoxModel.objectFormat = vsformat;
      checkBoxModel.detailFormat = vsformat;
      checkBoxModel.labels = ["AAA", "BBB"];

      vsCheckBox = <VSCheckBox>fixture.componentInstance;
      vsCheckBox.model = checkBoxModel;
      fixture.detectChanges();
      let check = fixture.nativeElement.querySelector("div.vs-check-box");
      let checkItem = fixture.nativeElement.querySelectorAll("div.checkbox")[0];
      expect(check.style["text-align"]).toEqual("right");
      expect(checkItem.style["padding-left"]).not.toBeNull();
      expect(check.style["vertical-align"]).toEqual("bottom");
      //expect(checkItem.style["align-items"]).toBe("flex-end");
   });
});
