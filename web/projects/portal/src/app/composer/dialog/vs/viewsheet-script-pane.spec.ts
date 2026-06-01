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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { CodemirrorService } from "../../../../../../shared/util/codemirror/codemirror.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { ScriptPane } from "../../../widget/dialog/script-pane/script-pane.component";
import { FormulaEditorService } from "../../../widget/formula-editor/formula-editor.service";
import { FormulaFunctionAnalyzerService } from "../../../widget/dialog/script-pane/formula-function-analyzer.service";
import { HelpUrlService } from "../../../widget/help-link/help-url.service";
import { ModelService } from "../../../widget/services/model.service";
import { ViewsheetScriptPaneModel } from "../../data/vs/viewsheet-script-pane-model";
import { ViewsheetScriptPane } from "./viewsheet-script-pane.component";

describe("Viewsheet script pane Test", () => {
   const createModel: () => ViewsheetScriptPaneModel = () => {
      return {
         onInit: "",
         onLoad: "",
         enableScript: true
      };
   };

   const createTreeModel: () => ScriptPaneTreeModel = () => {
      return {
         columnTree: null,
         functionTree: null,
         operatorTree: null,
      };
   };

   let fixture: ComponentFixture<ViewsheetScriptPane>;
   let viewsheetScriptPane: ViewsheetScriptPane;
   let uiContextService: any;
   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;

   beforeEach(() => {
      uiContextService = {
         isVS: vi.fn(),
         isAdhoc: vi.fn(),
         getDefaultTab: vi.fn(),
         setDefaultTab: vi.fn()
      };
      const ts = {
         options: {
            typeTip: "",
            hintDelay: 1700
         },
         destroy: vi.fn()
      };
      const codemirror = {
         createTernServer: vi.fn(() => ts),
         getEcmaScriptDefs: vi.fn(() => [{"Date": {"prototype": {}}}]),
         hasToken: vi.fn(() => false),
         createCodeMirrorInstance: vi.fn(() => ({
            focus: vi.fn(),
            setCursor: vi.fn(),
            lineCount: vi.fn(() => 0),
            lastLine: vi.fn(() => ""),
            on: vi.fn(),
            toTextArea: vi.fn(),
            getLine: vi.fn(() => ""),
            // ScriptPane.ngAfterViewInit() schedules a setTimeout that calls
            // codemirrorInstance.refresh(); missing it caused a post-teardown
            // TypeError when the timer fired after the fixture was destroyed.
            refresh: vi.fn(),
         }))
      };

      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            ReactiveFormsModule,
            NgbModule,
            HttpClientTestingModule,
            ViewsheetScriptPane,
            ScriptPane,
         ],
         
         providers: [
            NgbModal, FormulaEditorService,
            FormulaFunctionAnalyzerService,
            { provide: UIContextService, useValue: uiContextService },
            HelpUrlService, ModelService
         ],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .overrideComponent(ScriptPane, {set: {providers: [{provide: CodemirrorService, useValue: codemirror}]}})
         .compileComponents();

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
   });

   //Bug #18853 should select onRefresh by default
   it("check ui on script pane", () => {
      // In jsdom, scriptEditorContainer.getClientRects() returns an empty list,
      // so ScriptPane.isEditorElementDisplayed() is false and ngAfterViewInit
      // schedules a setTimeout that calls codemirrorInstance.refresh(). The
      // fixture is destroyed (which nulls codemirrorInstance) before the timer
      // fires, leading to a "Cannot read properties of null (reading 'refresh')"
      // unhandled error. Force getClientRects() to return a non-empty list so
      // the post-init refresh is never scheduled.
      vi.spyOn(Element.prototype, "getClientRects").mockImplementation(() => ({
         length: 1,
         0: new DOMRect(0, 0, 100, 100),
         item: () => new DOMRect(0, 0, 100, 100),
         [Symbol.iterator]: function*() { yield new DOMRect(0, 0, 100, 100); }
      }) as any);

      fixture = TestBed.createComponent(ViewsheetScriptPane);
      viewsheetScriptPane = <ViewsheetScriptPane> fixture.componentInstance;
      viewsheetScriptPane.scriptTreeModel = createTreeModel();
      viewsheetScriptPane.model = createModel();
      fixture.detectChanges();

      httpTestingController.match(() => true).forEach((r) => r.flush(createTreeModel()));

      let onInit = fixture.nativeElement.querySelectorAll("input[type=radio]")[0];
      let onRefresh = fixture.nativeElement.querySelectorAll("input[type=radio]")[1];
      expect(onInit.checked).toBeFalsy();
      expect(onRefresh.checked).toBeTruthy();
   });
});
