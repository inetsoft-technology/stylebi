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
 * Bug #78734 - which assembly the binding editor opens when Full Editor is clicked in the
 * Recommender before the preview object reached the client.
 *
 * Follows the whole client path: the real ObjectWizardToolBarComponent emits, then the real
 * ObjectWizardPane.goToFullEditor, VsWizardComponent.goToFullEditor (which falls back to the
 * original object on null) and ComposerMainComponent.openEditPane build the binding pane model.
 */

import { HttpRequest } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { AiAssistantDialogService } from "../../common/services/ai-assistant-dialog.service";
import { ComposerMainComponent } from "../../composer/gui/composer-main.component";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { ObjectWizardPane } from "./object-wizard/object-wizard-pane.component";
import { ObjectWizardToolBarComponent } from "./object-wizard-tool-bar.component";
import { VsWizardComponent } from "./vs-wizard.component";

const URL = "../api/vswizard/object/toolbar/full-editor";
const ORIGINAL = "Chart1";
const TEMP = "Recommender__1700000005000";

describe("Bug #78734 - Full Editor before the preview arrives opens the recommended assembly", () => {
   let fixture: ComponentFixture<ObjectWizardToolBarComponent>;
   let http: HttpTestingController;
   let bindingPaneModel: any;

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
      bindingPaneModel = null;

      // the real pane -> vs-wizard -> composer hops, wired as the templates wire them
      const composer: any = {
         focusedViewsheet: {},
         focusedSheet: { runtimeId: "rt-78734" },
         removeKeydownListener: vi.fn()
      };
      const vsWizard: any = {
         model: { runtimeId: "rt-78734", objectModel: { absoluteName: ORIGINAL, objectType: "VSChart" } },
         onFullEditor: {
            emit: (evt: any) => {
               (<any> ComposerMainComponent.prototype).openEditPane.call(
                  composer, evt.model.objectModel, evt.model.runtimeId, evt.model.oinfo);
               bindingPaneModel = composer.bindingPaneModel;
            }
         }
      };
      const pane: any = {
         onFullEditor: { emit: (evt: any) => VsWizardComponent.prototype.goToFullEditor.call(vsWizard, evt) }
      };
      fixture.componentInstance.onFullEditor.subscribe((obj: VSObjectModel) =>
         ObjectWizardPane.prototype.goToFullEditor.call(pane, obj));
   });

   afterEach(() => http.verify());

   function clickFullEditor(): void {
      fixture.componentInstance.actions.find(a => a.label === "_#(js:Full Editor)").action();
   }

   function request() {
      return http.expectOne((req: HttpRequest<any>) => req.url === URL);
   }

   it("opens the recommended assembly, not the original, when the response arrives first", () => {
      clickFullEditor();
      request().flush({ absoluteName: TEMP, objectType: "VSCrosstab" });

      expect(bindingPaneModel.absoluteName).toBe(TEMP);
      expect(bindingPaneModel.objectType).toBe("VSCrosstab");
   });

   it("opens the preview when it arrives before the response", () => {
      clickFullEditor();
      const req = request();
      fixture.componentRef.setInput("vsObject", <any> { absoluteName: TEMP, objectType: "VSChart" });
      fixture.detectChanges();
      req.flush({ absoluteName: TEMP, objectType: "VSChart" });

      expect(bindingPaneModel.absoluteName).toBe(TEMP);
      expect(bindingPaneModel.objectType).toBe("VSChart");
   });

   // Bug #44232: no preview assembly on the server, so the original object is still opened
   it("opens the original object when the server has no preview assembly", () => {
      clickFullEditor();
      request().flush(null);

      expect(bindingPaneModel.absoluteName).toBe(ORIGINAL);
   });
});
