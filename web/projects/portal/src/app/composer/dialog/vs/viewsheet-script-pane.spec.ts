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
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn()
      };
      const ts = {
         options: {
            typeTip: "",
            hintDelay: 1700
         },
         destroy: jest.fn()
      };
      const codemirror = {
         createTernServer: jest.fn(() => ts),
         getEcmaScriptDefs: jest.fn(() => [{"Date": {"prototype": {}}}]),
         hasToken: jest.fn(() => false),
         createCodeMirrorInstance: jest.fn(() => ({
            focus: jest.fn(),
            setCursor: jest.fn(),
            lineCount: jest.fn(() => 0),
            lastLine: jest.fn(() => ""),
            on: jest.fn(),
            toTextArea: jest.fn()
         }))
      };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, HttpClientTestingModule
         ],
         declarations: [
            ViewsheetScriptPane, ScriptPane
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
