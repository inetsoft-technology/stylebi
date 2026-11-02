/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * ObjectWizardToolBarComponent - Full Editor clicked before the preview object reached the
 * client (Bug #78734).
 *
 * The request then carries no assembly name and the server answers the preview assembly it
 * resolved. The toolbar must open that assembly whichever arrives first, the response or the
 * preview object, and must fall back to the original object (emit null) when the server has
 * no answer. Uses the real HttpClient with HttpTestingController so the reply time is under
 * the test's control.
 */

import { HttpErrorResponse, HttpRequest } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { AiAssistantDialogService } from "../../common/services/ai-assistant-dialog.service";
import { ComponentTool } from "../../common/util/component-tool";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { ObjectWizardToolBarComponent } from "./object-wizard-tool-bar.component";

const URL = "../api/vswizard/object/toolbar/full-editor";
const TEMP = "Recommender__1700000005000";

function vsObject(absoluteName: string, objectType: string): VSObjectModel {
   return <VSObjectModel> <any> { absoluteName, objectType };
}

describe("ObjectWizardToolBarComponent - Full Editor before the preview arrives", () => {
   let fixture: ComponentFixture<ObjectWizardToolBarComponent>;
   let http: HttpTestingController;
   let emitted: VSObjectModel[];

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, ObjectWizardToolBarComponent],
         providers: [
            { provide: AiAssistantDialogService, useValue: { openAiAssistantDialog: vi.fn() } },
            { provide: AiAssistantService, useValue: { lastBindingObject: "" } },
            { provide: ContextProvider, useValue: { vsWizard: true } },
            { provide: NgbModal, useValue: { open: vi.fn() } }
         ]
      });
      TestBed.overrideComponent(ObjectWizardToolBarComponent, {
         set: { imports: [], schemas: [NO_ERRORS_SCHEMA] }
      });

      fixture = TestBed.createComponent(ObjectWizardToolBarComponent);
      fixture.componentRef.setInput("runtimeId", "rt-78734");
      fixture.componentRef.setInput("vsObject", null);
      fixture.componentRef.setInput("isFullEditorVisible", true);
      fixture.detectChanges();
      http = TestBed.inject(HttpTestingController);
      emitted = [];
      fixture.componentInstance.onFullEditor.subscribe((obj: VSObjectModel) => emitted.push(obj));
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
   });

   function clickFullEditor(): void {
      const action = fixture.componentInstance.actions
         .find(a => a.label === "_#(js:Full Editor)");
      expect(action.enabled()).toBe(true);
      expect(action.visible()).toBe(true);
      action.action();
   }

   function expectRequest(): ReturnType<HttpTestingController["expectOne"]> {
      return http.expectOne((req: HttpRequest<any>) => req.url === URL);
   }

   // ordering 2: the response arrives before the preview object
   it("opens the assembly the server resolved, with its own type", () => {
      clickFullEditor();
      const request = expectRequest();
      expect(request.request.params.get("id")).toBe("rt-78734");
      expect(request.request.params.has("assemblyName")).toBe(false);

      request.flush({ absoluteName: TEMP, objectType: "VSCrosstab" });

      expect(emitted.length).toBe(1);
      expect(emitted[0].absoluteName).toBe(TEMP);
      expect(emitted[0].objectType).toBe("VSCrosstab");
   });

   // ordering 1: the preview object arrives after the click but before the response
   it("opens the preview object when it arrives before the response", () => {
      clickFullEditor();
      const request = expectRequest();
      expect(request.request.params.has("assemblyName")).toBe(false);

      const preview = vsObject(TEMP, "VSChart");
      fixture.componentRef.setInput("vsObject", preview);
      fixture.detectChanges();
      request.flush({ absoluteName: TEMP, objectType: "VSChart" });

      expect(emitted).toEqual([preview]);
      expect(emitted[0]).toBe(preview);
   });

   it("sends the preview name and opens the preview object once it is shown", () => {
      const preview = vsObject(TEMP, "VSChart");
      fixture.componentRef.setInput("vsObject", preview);
      fixture.detectChanges();

      clickFullEditor();
      const request = expectRequest();
      expect(request.request.params.get("assemblyName")).toBe(TEMP);
      request.flush({ absoluteName: TEMP, objectType: "VSChart" });

      expect(emitted.length).toBe(1);
      expect(emitted[0]).toBe(preview);
   });

   // Bug #44232: no preview assembly on the server either, so the original object is opened
   it("emits null when the server answers an empty body", () => {
      clickFullEditor();
      expectRequest().flush(null);

      expect(emitted).toEqual([null]);
   });

   it("emits null, not an object without a name, when the server answers {}", () => {
      clickFullEditor();
      expectRequest().flush({});

      expect(emitted).toEqual([null]);
   });

   it("emits null when the answer has no name or no type", () => {
      clickFullEditor();
      expectRequest().flush({ objectType: "VSChart" });
      clickFullEditor();
      expectRequest().flush({ absoluteName: TEMP, objectType: null });

      expect(emitted).toEqual([null, null]);
   });

   // Bug #76409: a failed request shows the error and opens nothing
   it("shows the error and emits nothing when the request fails", () => {
      const showHttpError = vi.spyOn(ComponentTool, "showHttpError")
         .mockImplementation(() => null);

      clickFullEditor();
      expectRequest().flush({ message: "boom" }, { status: 500, statusText: "Server Error" });

      expect(emitted).toEqual([]);
      expect(showHttpError).toHaveBeenCalledTimes(1);
      expect(showHttpError.mock.calls[0][1]).toBeInstanceOf(HttpErrorResponse);
   });
});
