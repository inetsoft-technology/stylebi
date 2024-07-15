/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Copyright (c) 2023, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */

import {
   AfterViewChecked,
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   Renderer2,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { DataRef } from "../../../common/data/data-ref";
import { FormulaFunctionAnalyzerService } from "./formula-function-analyzer.service";
import { HelpUrlService } from "../../help-link/help-url.service";
import { TreeNodeModel } from "../../tree/tree-node-model";
import { AnalysisResult } from "./analysis-result";
import { CodemirrorHighlightTextInfo } from "../codemirror-highlight-text-info";
import { TreeTool } from "../../../common/util/tree-tool";
import { CodemirrorService } from "../../../../../../shared/util/codemirror/codemirror.service";

const LINT_MARKERS = "CodeMirror-lint-markers";

@Component({
   selector: "script-pane",
   templateUrl: "script-pane.component.html",
   styleUrls: ["script-pane.component.scss"]
})
export class ScriptPane implements AfterViewInit, AfterViewChecked, OnInit, OnDestroy, OnChanges {
   @Input() columnTreeRoot: TreeNodeModel;
   @Input() columnTreeEnabled: boolean = true;
   @Input() functionOperatorShowRoot: boolean = false;
   @Input() functionTreeEnabled: boolean = true;
   @Input() columnShowRoot: boolean = false;
   @Input() grayedOutFields: DataRef[];
   @Input() showTooltip: boolean = false;
   @Input() required: boolean = true;
   @Input() preventEscape = false;
   @Input() fullContainer: boolean;
   @Input() disabled: boolean = false;
   @Input() showOriginalName: boolean = false;
   @Input() propertyDefinitions: any;
   @Output() expressionChange: EventEmitter<any> = new EventEmitter<any>();
   @Output() analysisResultsChange = new EventEmitter<AnalysisResult[]>();
   @ViewChild("scriptEditor") scriptEditor: ElementRef;
   @ViewChild("scriptEditorContainer") scriptEditorContainer: ElementRef;

   private _expression: string;
   private codemirrorInstance: any;
   private _sql: boolean = false;
   private ternServer: any;
   private initialized = false;
   private viewChecked = false;
   private _scriptDefinitions: any;
   private cancelAutocomplete: () => any;
   private init: boolean = true;
   private returnToken = false; // return without function
   private functionOperatorRoot: TreeNodeModel;
   private _functionTreeRoot: TreeNodeModel;
   private _operatorTreeRoot: TreeNodeModel;
   private _analysisResults: AnalysisResult[] = [];
   helpURL = "";
   needUseVirtualScroll: boolean = true;

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

   get analysisResults(): AnalysisResult[] {
      return this._analysisResults;
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

   @Input()
   set operatorTreeRoot(operatorTreeRoot: TreeNodeModel) {
      this.functionOperatorRoot = null;
      this.init = true;
      this._operatorTreeRoot = operatorTreeRoot;
   }

   get operatorTreeRoot(): TreeNodeModel {
      return this._operatorTreeRoot;
   }

   get expressionMissing(): boolean {
      return this.required && (!this.expression || this.expression.trim() === "");
   }

   get returnError(): boolean {
      return !!this._expression && this.returnToken;
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

   @Input()
   set cursor(cursor: { line: number, ch: number }) {
      if(this.codemirrorInstance) {
         this.codemirrorInstance.setCursor(cursor.line, cursor.ch);
      }
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

   // combined function/operator tree
   get functionOperatorTreeRoot(): TreeNodeModel {
      if(this.functionTreeRoot && this.operatorTreeRoot) {
         if(this.init) {
            this.functionTreeRoot.expanded = this.operatorTreeRoot.expanded = false;
            this.init = false;
         }

         if(this.functionOperatorRoot == null) {
            this.functionOperatorRoot = {
               label: "_#(js:root)",
               children: [...(this.sql ? [this.functionTreeRoot] : this.functionTreeRoot.children),
                  this.operatorTreeRoot]
            };
         }

         return this.functionOperatorRoot;
      }

      return this.functionTreeRoot ? this.functionTreeRoot : this.operatorTreeRoot || {};
   }

   constructor(private codemirrorService: CodemirrorService, private zone: NgZone,
               private analyzerService: FormulaFunctionAnalyzerService,
               private helpService: HelpUrlService,
               private renderer: Renderer2, private host: ElementRef)
   {
   }

   ngOnInit(): void {
      this.helpService.getScriptHelpUrl().subscribe((url) => this.helpURL = url);
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.columnTreeRoot && this.initialized) {
         this.needUseVirtualScroll = TreeTool.needUseVirtualScroll(this.columnTreeRoot);
      }
   }

   ngAfterViewInit(): void {
      this.initCodeMirror();

      if(!this.isEditorElementDisplayed()) {
         // created with display:none, need to refresh the code mirror instance when displayed
         setTimeout(() => this.codemirrorInstance.refresh(), 0);
      }
      this.initialized = true;
      this.needUseVirtualScroll = TreeTool.needUseVirtualScroll(this.columnTreeRoot);
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

   ngOnDestroy(): void {
      this.destroyCodeMirror();
   }

   private isEditorElementDisplayed(): boolean {
      const clientRects = this.scriptEditorContainer.nativeElement.getClientRects();
      return !!clientRects && clientRects.length > 0 && !!clientRects[0];
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
            readOnly: this.disabled,
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

         this.codemirrorInstance.focus();
         this.codemirrorInstance.setCursor({
            line: this.codemirrorInstance.lineCount(),
            ch: this.codemirrorInstance.lastLine().length
         });

         this.renderAnalysisResults();

         this.codemirrorInstance.on("change", (doc, obj) => {
            this.zone.run(() => {
               this.expressionChange.emit({
                  expression: this.codemirrorInstance.getValue(),
                  selection: obj
               });
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
                  changeObj.text.length === 1 && changeObj.text[0] === ".")
               {
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
                        fromLine == toLine && fromCh == toCh)
                     {
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

   private docTooltip(data): any {
      let tip1 = document.createElement("span");
      let tip2 = document.createElement("strong");
      tip2.appendChild(document.createTextNode(data.type || "not found"));

      if (data.url) {
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
      this.analysisResultsChange.emit(this._analysisResults);

      // render new
      this.renderAnalysisResults();
   }

   private renderAnalysisResults(): void {
      if(!!this.codemirrorInstance?.doc) {
         this.codemirrorInstance.operation(() => {
            const doc = this.codemirrorInstance.doc;

            this.analysisResults
               ?.filter((result) => result instanceof CodemirrorHighlightTextInfo)
               .map((analysisResult) => (<CodemirrorHighlightTextInfo> analysisResult))
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

   @HostListener("keyup.esc", ["$event"])
   onKeyUp($event: KeyboardEvent): void {
      if(this.preventEscape) {
         $event.preventDefault();
         $event.stopPropagation();
      }
   }

   itemClicked(node: TreeNodeModel, target: string): void {
      // make sure focus is on text area
      setTimeout(() => this.codemirrorInstance.focus(), 200);

      this.expressionChange.emit({
         target,
         node,
         expression: this.codemirrorInstance.getValue(),
         selection: {
            from: this.codemirrorInstance.getCursor("from"),
            to: this.codemirrorInstance.getCursor("to")
         }
      });
   }

   /**
    * Method for determining the css class of the tree.
    */
   getCSSIcon(node: any): string {
      let css: string = "";

      if(node.data && node.data.data == "New Aggregate") {
         css += "summary-icon";
      }
      else if(node.data && node.data.useragg == "true") {
         css += "summary-icon";
      }
      else if(node.data && node.data.useragg == "false") {
         css += "summary-icon";
      }
      else if(node.data && node.data.name == "LOGIC_MODEL") {
         css += "db-model-icon";
      }
      else if(node.data && (node.data.isTable == "true" || node.type == "entity" ||
         node.data.name == "TABLE" || node.data.name == "PHYSICAL_TABLE"))
      {
         css += "data-table-icon";
      }
      else if(node.data && (node.data.isField == "true" || node.data.type == "column") ||
         node.type === "field" || node.data && node.data.name == "COLUMN" ||
         node.data && node.data.name == "DATE_COLUMN")
      {
         css += "column-icon";
      }
      else if(node.children && node.children.length > 0) {
         if(node.expanded) {
            css += "folder-open-icon";
         }
         else {
            css += "folder-icon";
         }
      }

      return css;
   }

   public static insertText(expression: string, value: string, selection: any): string {
      let lines = expression.split(/\r?\n/);
      let line = lines[selection.from.line];

      if(selection.from.line == selection.to.line) {
         line = line.substring(0, selection.from.ch) + line.substring(selection.to.ch);
         lines[selection.from.line] = line;
      }
      else {
         lines[selection.from.line] = line.substring(0, selection.from.ch);

         line = lines[selection.to.line];

         if(selection.to.ch < line.length) {
            lines[selection.from.line] += line.substring(selection.to.ch);
         }

         lines.splice(selection.from.line + 1, selection.to.line - selection.from.line);
      }

      line = lines[selection.from.line];
      line = line.substring(0, selection.from.ch) + value +
         line.substring(selection.from.ch);
      lines[selection.from.line] = line;
      return lines.join("\n");
   }

   isGrayedOutField(evt: any): boolean {
      let grayedFields = this.grayedOutFields;
      let name: string = null;

      if(evt && evt.data && evt.data.data) {
         name = evt.data.data.replace(":", ".");
      }

      if(name == null) {
         return false;
      }

      for(let i = 0; grayedFields && i < grayedFields.length; i++) {
         if(name == grayedFields[i] || name == grayedFields[i].name) {
            return true;
         }
      }

      return false;
   }

   blockKeys(event: KeyboardEvent): void {
      switch(event.keyCode) {
      case 46: // Delete key
         event.stopPropagation();
         break;
      case 67: // copy
      case 86: // paste
      case 88: // cut
      case 68: // remove
         if(event.ctrlKey) {
            event.stopPropagation();
         }
      }
   }

   // enable browser context menu to support copy/paste from popup menu
   rightClick(event: Event) {
      event.stopPropagation();
      return true;
   }
}
