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
import {
   AfterViewChecked,
   AfterViewInit, Component, ElementRef, EventEmitter, Input,
   NgZone, OnDestroy, OnInit,
   Output,
   Renderer2,
   ViewChild
} from "@angular/core";
import { CodemirrorService } from "../../../../../shared/util/codemirror/codemirror.service";
import { FormulaFunctionAnalyzerService } from "../../widget/dialog/script-pane/formula-function-analyzer.service";
import { HelpUrlService } from "../../widget/help-link/help-url.service";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { CodemirrorHighlightTextInfo } from "../../widget/dialog/codemirror-highlight-text-info";
import { AnalysisResult } from "../../widget/dialog/script-pane/analysis-result";
import { HttpClient } from "@angular/common/http";
import { ScriptModel } from "../../composer/data/script/script";

const LINT_MARKERS = "CodeMirror-lint-markers";
const URI_OPEN_CODEMIRROR = "../api/codemirror/open";
const URI_SCRIPT_SCRIPTDEFINITIPN = "../api/script/scriptDefinition";
const URI_CHECK_SCRIPT = "../api/composer/check/script";

@Component({
   selector: "codemirror",
   templateUrl: "codemirror.component.html",
})
export class CodemirrorComponent implements AfterViewInit, AfterViewChecked, OnDestroy, OnInit {
   @Input() text: string;
   @Output() codemirrorInstanceEmitter: EventEmitter<any> = new EventEmitter();
   @Output() expressionChange: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild("scriptEditor") scriptEditor: ElementRef;
   @ViewChild("scriptEditorContainer") scriptEditorContainer: ElementRef;

   private codemirrorInstance: any;
   private ternServer: any;
   private _sql: boolean = false;
   private initialized = false;
   private init: boolean = true;
   private _expression: string;
   private _scriptDefinitions: any;
   private functionOperatorRoot: TreeNodeModel;
   private _functionTreeRoot: TreeNodeModel;
   private _operatorTreeRoot: TreeNodeModel;
   private _analysisResults: AnalysisResult[] = [];
   private returnToken = false; // return without function
   private viewChecked = false;
   private cancelAutocomplete: () => any;
   private currId: string = "";
   private currCh: number;
   private list: string[] = [];
   private timer: any;

   constructor(private codemirrorService: CodemirrorService, private zone: NgZone,
               private analyzerService: FormulaFunctionAnalyzerService,
               private http: HttpClient,
               private helpService: HelpUrlService,
               private renderer: Renderer2, private host: ElementRef) {
      this.timer = null;
   }

   @Input() set sql(sql: boolean) {
      this._sql = sql;

      if(this.initialized) {
         this.destroyCodeMirror();
         this.initCodeMirror();
      }
   }

   get sql(): boolean {
      return this._sql;
   }

   @Input()
   set scriptDefinitions(value: any) {
      this._scriptDefinitions = value;

      if(this.initialized) {
         this.destroyCodeMirror();
         this.initCodeMirror();
      }
   }

   get scriptDefinitions(): any {
      return this._scriptDefinitions;
   }

   get analysisResults(): AnalysisResult[] {
      return this._analysisResults;
   }

   @Input()
   set expression(value: string) {
      this._expression = value;

      if(this.codemirrorInstance && this.codemirrorInstance.getValue() !== value) {
         const pos = this.codemirrorInstance.getCursor("from");
         this.codemirrorInstance.setValue(value || "");
         this.codemirrorInstance.setCursor(pos);
      }

      this.triggerSyntaxAnalyzer();
   }

   get expression(): string {
      return this._expression;
   }

   @Input()
   set cursor(cursor: { line: number, ch: number }) {
      if(this.codemirrorInstance) {
         this.codemirrorInstance.setCursor(cursor.line, cursor.ch);
      }
   }

   @Input()
   set functionTreeRoot(functionTreeRoot: TreeNodeModel) {
      this.functionOperatorRoot = null;
      this.init = true;
      this._functionTreeRoot = functionTreeRoot;
   }

   get functionTreeRoot(): TreeNodeModel {
      return this._functionTreeRoot;
   }

   ngOnDestroy(): void {
      this.destroyCodeMirror();
   }

   ngAfterViewChecked(): void {
      // if codemirror is refresh when the tab is not visible, sometimes the text won't
      // be visible until clicked. refresh() again to ensure it's shown
      if(!this.viewChecked) {
         this.zone.runOutsideAngular(() => {
            setTimeout(() => {
               if(this.isEditorElementDisplayed()) {
                  this.viewChecked = true;

                  if(this.codemirrorInstance != null) {
                     this.codemirrorInstance.refresh();
                  }
               }
            }, 0);
         });
      }
   }

   ngOnInit(): void {
      this.http.get<any>(URI_SCRIPT_SCRIPTDEFINITIPN).subscribe((data) => {
         this.scriptDefinitions = data;
      });
   }

   ngAfterViewInit(): void {
      this.initCodeMirror();
      this.codemirrorInstanceEmitter.emit(this.codemirrorInstance);

      if(!this.isEditorElementDisplayed()) {
         // created with display:none, need to refresh the code mirror instance when displayed
         setTimeout(() => this.codemirrorInstance.refresh(), 0);
      }

      this.initialized = true;
   }

   private triggerSyntaxAnalyzer(): void {
      let analysisResults = this.analyzerService.syntaxAnalysis(
         this._expression, this.functionTreeRoot);
      this.setAnalysisResults(analysisResults);
   }

   private setAnalysisResults(analysisResults: AnalysisResult[]): void {
      // clear old
      this._analysisResults
         ?.filter((result) => result instanceof CodemirrorHighlightTextInfo)
         .map((analysisResult) => (<CodemirrorHighlightTextInfo> analysisResult))
         .forEach(analysisResult =>
         {
            analysisResult.textMarker?.clear();
         });

      this.codemirrorInstance?.doc?.clearGutter(LINT_MARKERS);
      this._analysisResults = analysisResults;
      // render new
      this.renderAnalysisResults();
   }

   private initCodeMirror(): void {
      this.zone.runOutsideAngular(() => {
         const config: any = {
            lineNumbers: true,
            lineWrapping: true,
            mode: this.sql ? "text/x-sql" : "javascript",
            extraKeys: {
               "Ctrl-Space": "autocomplete",
               "Alt-/": "autocomplete",
               "Ctrl-/": "toggleComment"
            },
            foldGutter: true,
            gutters: [
               "CodeMirror-linenumbers",
               "CodeMirror-foldgutter",
               LINT_MARKERS
            ],
            matchBrackets: true,
            theme: "inetsoft",
            // readOnly: this.disabled,
            lint: true
         };

         if(this.sql) {
            config.hintOptions = {
               completeSingle: false
            };
            config.extraKeys = {
               "Ctrl-Space": "autocomplete",
               "Alt-/": "autocomplete",
               "Ctrl-/": "toggleComment"
            };
         }
         else {
            config.hintOptions = {
               completeSingle: false
            };
            config.extraKeys = {
               "Ctrl-O": (cm) => this.ternServer.showDocs(cm, cm.getCursor()),
               "Ctrl-Space": (cm) => this.ternServer.complete(cm),
               "Alt-/": (cm) => this.ternServer.complete(cm),
               "Ctrl-/": "toggleComment"
            };

            const defs = this.codemirrorService.getEcmaScriptDefs();
            delete (defs[0]["Date"]["prototype"]).toJSON;

            if(this.scriptDefinitions) {
               defs.push(this.scriptDefinitions);
            }

            this.ternServer = this.codemirrorService.createTernServer({
               defs: defs,
               useWorker: false,
               queryOptions: {completions: {guess: false}}
            });

            this.ternServer.options.typeTip = this.docTooltip;
            this.ternServer.options.hintDelay = 17000;
         }

         this.codemirrorInstance = this.codemirrorService.createCodeMirrorInstance(
            this.scriptEditor.nativeElement, config);

         if(this._expression) {
            this.codemirrorInstance.setValue(this._expression);
         }

         this.codemirrorInstanceEmitter.emit(this.codemirrorInstance);
         this.renderAnalysisResults();

         this.codemirrorInstance.on("drop", (doc, obj) => {
            obj.preventDefault();
         });

         this.codemirrorInstance.on("dragover", (doc, obj) => {
            obj.preventDefault();
            obj.dataTransfer.dropEffect = "none";
         });

         this.codemirrorInstance.on("change", (doc, obj) => {
            this.zone.run(() => {
               this.expressionChange.emit({
                  expression: this.codemirrorInstance.getValue(),
                  selection: obj
               });

               if (this.timer) {
                  clearTimeout(this.timer);
               }

               this.timer = setTimeout(() => {
                  this.checkSyntax(this.expression);
               }, 3000);

               if(obj.text && this.currId == "") {
                  this.currId = obj.text;
                  this.currCh = this.codemirrorInstance.getCursor().ch;
               }

               this.returnToken =
                  this.codemirrorService.hasToken(this.codemirrorInstance, "keyword", "return") &&
                  !this.codemirrorService.hasToken(this.codemirrorInstance, "keyword", "function");
            });
         });

         this.returnToken =
            this.codemirrorService.hasToken(this.codemirrorInstance, "keyword", "return") &&
            !this.codemirrorService.hasToken(this.codemirrorInstance, "keyword", "function");

         if(!this.sql) {
            this.codemirrorInstance.on(
               "cursorActivity", (cm) => this.ternServer.updateArgHints(cm));
         }

         this.codemirrorInstance.on("inputRead", (cm, changeObj) => {
            if(changeObj.origin == "paste" || changeObj.origin == "cut") {
               return;
            }

            const newText = changeObj.text;

            if(newText.length == 1 && !newText[0].trim()) {
               return;
            }

            if(newText == ")" || newText == "]" || newText == "}") {
               return;
            }

            if(this.sql) {
               this.delayAutocomplete(() => cm.showHint({completeSingle: false}));
            }
            else {
               if(changeObj.origin === "+input" &&
                  changeObj.text.length === 1 && changeObj.text[0] === ".") {
                  if(this.cancelAutocomplete) {
                     this.cancelAutocomplete();
                     this.cancelAutocomplete = null;
                  }

                  this.ternServer.complete(cm);
                  const completion = cm.state.completionActive;
                  const pick = completion.pick;
                  const select = completion.select;
                  completion.pick = function(data, i) {
                     const str: string = data.list[i].text;
                     pick.apply(completion, [data, i]);

                     const fromLine = data.from.line;
                     const toLine = data.to.line;
                     const fromCh = data.from.ch;
                     const toCh = data.to.ch;

                     // if autocomplete a field as ['fieldname'], need to remove the
                     // dot before ['fieldname'] (45387).
                     if(str.startsWith("[") && str.endsWith("]") &&
                        fromLine == toLine && fromCh == toCh) {
                        cm.replaceRange("", {line: fromLine, ch: fromCh - 1},
                           {line: fromLine, ch: fromCh});
                     }
                  };
               }
               else {
                  this.delayAutocomplete(() => {
                     this.ternServer.complete(cm);
                  });
               }
            }
         });
      });
   }

   checkSyntax(text: string) {
      this.http.post(URI_CHECK_SCRIPT, text).subscribe((data) => {
         if(data) {
            const match = data.toString().match(/row:\s*(\d+),\s*col:\s*(\d+),\s*error:\s*(.+)/);
            const rowValue = parseInt(match[1], 10) - 1;
            const colValue = match[2];
            const error = match[3];

            this.codemirrorInstance.doc.markText(
               {line: rowValue, ch: 0},
               {line: rowValue, ch: colValue},
               {
                  className: "alert-danger cm-error",
                     attributes: {
                  title: error
               }
            });

            let marker = document.createElement("div");
            let icon = marker.appendChild(document.createElement("span"));
            icon.className = "cm-error close-circle-icon icon-size-small0";
            marker.setAttribute("title", error);
            this.codemirrorInstance.doc.setGutterMarker(rowValue, LINT_MARKERS, marker);
         }
      });
   }

   private delayAutocomplete(fn: () => any): void {
      if(this.cancelAutocomplete) {
         this.cancelAutocomplete();
         this.cancelAutocomplete = null;
      }

      const delay = this.ternServer && this.ternServer.options.hintDelay
         ? this.ternServer.options.hintDelay : 1700;
      const listener = () => {
         if(this.cancelAutocomplete) {
            this.cancelAutocomplete();
         }
      };
      const callbacks = [];

      const timeout = setTimeout(() => {
         this.codemirrorInstance.off("cursorActivity", listener);
         this.codemirrorInstance.off("blur", listener);
         this.codemirrorInstance.off("scroll", listener);
         this.codemirrorInstance.off("setDoc", listener);
         callbacks.forEach((cb) => cb());
         this.cancelAutocomplete = null;
         fn();
      }, delay);

      this.cancelAutocomplete = () => {
         clearTimeout(timeout);
         this.codemirrorInstance.off("cursorActivity", listener);
         this.codemirrorInstance.off("blur", listener);
         this.codemirrorInstance.off("scroll", listener);
         this.codemirrorInstance.off("setDoc", listener);
         callbacks.forEach((cb) => cb());
         this.cancelAutocomplete = null;
      };

      this.codemirrorInstance.on("cursorActivity", listener);
      this.codemirrorInstance.on("blur", listener);
      this.codemirrorInstance.on("scroll", listener);
      this.codemirrorInstance.on("setDoc", listener);
      callbacks.push(this.renderer.listen(this.host.nativeElement, "mousemove", listener));
      callbacks.push(this.renderer.listen(this.host.nativeElement, "mouseout", listener));
   }

   private docTooltip(data): any {
      let tip1 = document.createElement("span");
      let tip2 = document.createElement("strong");
      tip2.appendChild(document.createTextNode(data.type || "not found"));

      if(data.url) {
         let tip3 = document.createElement("a");
         const link = document.createTextNode("[View Function Help]\n");
         tip3.appendChild(link);
         tip3.style.color = "Blue";
         tip3.style.textDecoration = "underline";

         tip1.appendChild(document.createTextNode(" "));
         let child = tip1.appendChild(tip3);
         child.href = data.url;
         child.target = "_blank";
      }

      tip1.appendChild(tip2);

      return tip1;
   }

   private renderAnalysisResults(): void {
      if(!!this.codemirrorInstance?.doc) {
         this.codemirrorInstance.operation(() => {
            const doc = this.codemirrorInstance.doc;

            this.analysisResults
               ?.filter((result) => result instanceof CodemirrorHighlightTextInfo)
               .map((analysisResult) => (<CodemirrorHighlightTextInfo>analysisResult))
               .forEach(highlightTextInfo => {
                  const error = highlightTextInfo.msg;

                  highlightTextInfo.textMarker = doc.markText(
                     highlightTextInfo.from, highlightTextInfo.to, {
                        className: "alert-danger cm-error",
                        attributes: {
                           title: error
                        }
                     });

                  let marker = document.createElement("div");
                  let icon = marker.appendChild(document.createElement("span"));
                  icon.className = "cm-error close-circle-icon icon-size-small0";
                  marker.setAttribute("title", error);
                  doc.setGutterMarker(highlightTextInfo.from.line, LINT_MARKERS, marker);
               });
         });
      }
   }

   private destroyCodeMirror(): void {
      if(this.cancelAutocomplete) {
         this.cancelAutocomplete();
         this.cancelAutocomplete = null;
      }

      if(this.ternServer) {
         this.ternServer.destroy();
         this.ternServer = null;
      }

      if(this.codemirrorInstance) {
         this.codemirrorInstance.toTextArea();
         this.codemirrorInstance = null;
      }
   }

   private isEditorElementDisplayed(): boolean {
      const clientRects = this.scriptEditorContainer.nativeElement.getClientRects();
      return !!clientRects && clientRects.length > 0 && !!clientRects[0];
   }
}