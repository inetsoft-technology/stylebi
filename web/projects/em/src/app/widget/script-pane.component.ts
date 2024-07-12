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
import {
   AfterViewChecked,
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
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
import { ScriptTreeFlatNode, ScriptTreeNode } from "./script-tree-node";
import { ScriptTreeDataSource } from "./script-tree-data-source";
import { CodemirrorService } from "../../../../shared/util/codemirror/codemirror.service";

const LINT_MARKERS = "CodeMirror-lint-markers";

@Component({
   selector: "em-script-pane",
   templateUrl: "./script-pane.component.html",
   styleUrls: ["./script-pane.component.scss"]
})
export class ScriptPaneComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy, AfterViewChecked {
   @Input() columnTreeRoot: ScriptTreeDataSource;
   @Input() functionTreeModel: ScriptTreeNode;
   @Input() operatorTreeModel: ScriptTreeNode;
   @Input() fullContainer: boolean;
   @Output() expressionChange: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild("scriptEditor") scriptEditor: ElementRef;
   @ViewChild("scriptEditorContainer", { static: true }) scriptEditorContainer: ElementRef;
   functionOperatorSource: ScriptTreeDataSource = new ScriptTreeDataSource();
   private codemirrorInstance: any;
   private ternServer: any;
   private _scriptDefinitions: any;

   private _expression: string;
   private returnToken = false; // return without function
   private initialized = false;
   private cancelAutocomplete: () => any;
   private preScriptEditorContainerLeft: number;

   constructor(private codemirrorService: CodemirrorService, private zone: NgZone,
               private renderer: Renderer2, private host: ElementRef)
   {
   }

   ngOnInit(): void {
   }

   ngAfterViewInit(): void {
      this.initCodeMirror();

      if(!this.isEditorElementDisplayed()) {
         // created with display:none, need to refresh the code mirror instance when displayed
         setTimeout(() => this.codemirrorInstance.refresh(), 0);
      }

      this.initialized = true;
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("functionTreeModel") || changes.hasOwnProperty("operatorTreeModel")) {
         this.initFunctionOperatorTreeRoot();
      }
   }

   @Input()
   set expression(value: string) {
      this._expression = value;

      if(this.codemirrorInstance && this.codemirrorInstance.getValue() !== value) {
         const pos = this.codemirrorInstance.getCursor("from");
         this.codemirrorInstance.setValue(value || "");
         this.codemirrorInstance.setCursor(pos);
      }

      // this.triggerSyntaxAnalyzer();
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

   initCodeMirror() {
      this.zone.runOutsideAngular(() => {
         const config: any = {
            lineNumbers: true,
            lineWrapping: false,
            mode: "javascript",
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
            lint: true
         };

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
            useWorker: false
         });

         this.ternServer.options.typeTip = this.docTooltip;
         this.ternServer.options.hintDelay = 17000;

         this.codemirrorInstance =
            this.codemirrorService.createCodeMirrorInstance(this.scriptEditor.nativeElement, config);

         if(this._expression) {
            this.codemirrorInstance.setValue(this._expression);
         }

         this.codemirrorInstance.focus();
         this.codemirrorInstance.setCursor({
            line: this.codemirrorInstance.lineCount(),
            ch: this.codemirrorInstance.lastLine().length
         });

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
         this.codemirrorInstance.on(
            "cursorActivity", (cm) => this.ternServer.updateArgHints(cm));

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
         });
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

   initFunctionOperatorTreeRoot(): void {
      let scriptTreeFlatNodes: ScriptTreeFlatNode[] = [];

      if(this.functionTreeModel) {
         let funNodes = this.functionOperatorSource.transform({nodes: this.functionTreeModel.children}, 0);
         scriptTreeFlatNodes.push(...funNodes);
      }

      if(this.operatorTreeModel) {
         let operatorNodes = this.functionOperatorSource.transform({nodes: [this.operatorTreeModel]}, 0);
         scriptTreeFlatNodes.push(...operatorNodes);
      }

      this.functionOperatorSource.data = scriptTreeFlatNodes;
   }

   nodeSelect(data: any) {
      // make sure focus is on text area
      setTimeout(() => this.codemirrorInstance.focus(), 200);
      this.expressionChange.emit({
         target: data.target,
         node: data.evt.node,
         expression: this.codemirrorInstance.getValue(),
         selection: {
            from: this.codemirrorInstance.getCursor("from"),
            to: this.codemirrorInstance.getCursor("to")
         }
      });
   }

   filterDataSource(filterStr: string, dataSource: ScriptTreeDataSource): void {
      dataSource.search(filterStr);
   }

   get expressionMissing(): boolean {
      return !this.expression || this.expression.trim() === "";
   }

   private isEditorElementDisplayed(): boolean {
      const clientRects = this.scriptEditorContainer.nativeElement.getClientRects();
      return !!clientRects && clientRects.length > 0 && !!clientRects[0];
   }

   private destroyCodeMirror(): void {
      if(this.ternServer) {
         this.ternServer.destroy();
         this.ternServer = null;
      }

      if(this.codemirrorInstance) {
         this.codemirrorInstance.toTextArea();
         this.codemirrorInstance = null;
      }
   }

   setValue(value: string) {
      this.codemirrorInstance.setValue(value);
      setTimeout(() => this.codemirrorInstance.refresh(), 0);
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

   ngAfterViewChecked(): void {
      if(this.scriptEditorContainer) {
         let left = this.scriptEditorContainer.nativeElement.getBoundingClientRect().left;

         if(left != this.preScriptEditorContainerLeft) {
            setTimeout(() => this.codemirrorInstance.refresh(), 0);
         }

         this.preScriptEditorContainerLeft = left;
      }
   }

   ngOnDestroy(): void {
      this.destroyCodeMirror();
   }
}
