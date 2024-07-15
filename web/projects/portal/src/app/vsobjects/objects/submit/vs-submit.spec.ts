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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { SsoHeartbeatService } from "../../../../../../shared/sso/sso-heartbeat.service";
import { TestUtils } from "../../../common/test/test-utils";
import { StompClientService, ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { VSSubmit } from "./vs-submit.component";
import { GlobalSubmitService } from "../../util/global-submit.service";

describe("VS Submit component unit case", () => {
   let fixture: ComponentFixture<VSSubmit>;
   let vsSubmit: VSSubmit;
   let socket: any;
   let dataTipService: any;
   let debounceService: any;
   let ssoHeartbeatService: any;

   beforeEach(() => {
      socket = { sendEvent: jest.fn() };
      dataTipService = { isDataTip: jest.fn() };
      const contextProvider = {};
      debounceService = { debounce: jest.fn((key, fn, delay, args) => fn(...args)) };
      ssoHeartbeatService = { heartbeat: jest.fn() };
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [VSSubmit, VSPopComponentDirective, SafeFontDirective],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            FormInputService,
            ViewsheetClientService,
            StompClientService,
            PopComponentService,
            GlobalSubmitService,
            { provide: ContextProvider, useValue: contextProvider },
            { provide: DataTipService, useValue: dataTipService },
            { provide: DebounceService, useValue: debounceService },
            { provide: SsoHeartbeatService, useValue: ssoHeartbeatService }
         ]
      }).compileComponents();

      fixture = TestBed.createComponent(VSSubmit);
      vsSubmit = <VSSubmit>fixture.componentInstance;
      vsSubmit.model = TestUtils.createMockVSSubmitModel("submit1");
   });

   //Bug #18438 should apply v alignment
   it("should apply v alignment", () => {
      vsSubmit.model.objectFormat.hAlign = "right";
      vsSubmit.model.objectFormat.width = 146;
      vsSubmit.model.objectFormat.height = 65;
      vsSubmit.model.objectFormat.vAlign = "top";

      fixture.detectChanges();
      let sub = fixture.nativeElement.querySelector("button.submit-button");

      expect(sub.style["text-align"]).toEqual("right");
      expect(sub.style["padding-bottom"]).not.toBeNull();
   });

   //Bug #20253 should apply enable property
   it("should apply enable property", () => {
      vsSubmit.model.enabled = false;

      fixture.detectChanges();
      let sub = fixture.nativeElement.querySelector("button.submit-button");

      expect(sub.getAttribute("class")).toMatch(/.*fade-assembly.*/);
   });
});
