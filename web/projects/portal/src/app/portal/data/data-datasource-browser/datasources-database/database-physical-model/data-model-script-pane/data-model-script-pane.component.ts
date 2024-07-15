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
   Component,
   AfterViewInit,
   OnDestroy,
   Output,
   EventEmitter,
   ViewChild,
   ElementRef,
   Input,
   NgZone,
   Renderer2,
   AfterViewChecked
} from "@angular/core";
import * as ECMASCRIPT_DEFS from "tern/defs/ecmascript.json";
import { GuiTool } from "../../../../../../common/util/gui-tool";
import {
   CodemirrorService
} from "../../../../../../../../../shared/util/codemirror/codemirror.service";

@Component({
   selector: "data-model-script-pane",
   templateUrl: "data-model-script-pane.component.html",
   styleUrls: ["data-model-script-pane.component.scss"]
})
export class DataModelScriptPane implements AfterViewInit, OnDestroy, AfterViewChecked {
   @Input() fullContainer: boolean;
   @Output() expressionChange: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("scriptEditor") scriptEditor: ElementRef;
   @ViewChild("scriptEditorContainer") scriptEditorContainer: ElementRef;
   private _expression: string;
   private codemirrorInstance: any;
   private _sql: boolean = false;
   private ternServer: any;
   private initialized = false;
   private cancelAutocomplete: () => any;
   private viewChecked = false;

   @Input()
   set expression(value: string) {
      this._expression = value;

      if(!!this.codemirrorInstance && this.codemirrorInstance.getValue() !== value) {
         const pos = this.codemirrorInstance.getCursor("from");
         this.codemirrorInstance.setValue(value || "");
         this.codemirrorInstance.setCursor(pos);
      }
   }

   get expression(): string {
      return this._expression;
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

   constructor(private zone: NgZone, private renderer: Renderer2, private host: ElementRef,
               private codemirrorService: CodemirrorService) {
   }

   ngAfterViewInit(): void {
      this.initCodeMirror();

      if(!GuiTool.getElementRect(this.scriptEditorContainer.nativeElement)) {
         // created with display:none, need to refresh the code mirror instance when displayed
         setTimeout(() => this.codemirrorInstance.refresh());
      }

      this.initialized = true;
   }

   ngAfterViewChecked(): void {
      // if codemirror is refresh when the tab is not visible, sometimes the text won't
      // be visible until clicked. refresh() again to ensure it's shown
      if(!this.viewChecked) {
         this.zone.runOutsideAngular(() => {
            setTimeout(() => {
               if(this.isEditorElementDisplayed()) {
                  this.viewChecked = true;
                  this.codemirrorInstance.refresh();
               }
            }, 0);
         });
      }
   }

   ngOnDestroy(): void {
      this.destroyCodeMirror();
   }

   public insert(expression: string, value: string): void {
      const selection: any = {
         from: this.codemirrorInstance.getCursor("from"),
         to: this.codemirrorInstance.getCursor("to")
      };

      this.expression = this.insertText(expression, value, selection);
      this.expressionChange.emit(this.expression);
   }

   private isEditorElementDisplayed(): boolean {
      const clientRects = this.scriptEditorContainer.nativeElement.getClientRects();
      return !!clientRects && clientRects.length > 0 && !!clientRects[0];
   }

   private insertText(expression: string, value: string, selection: any): string {
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
               "CodeMirror-foldgutter"
            ],
            matchBrackets: true,
            theme: "inetsoft"
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
            config.extraKeys = {
               "Ctrl-O": (cm) => this.ternServer.showDocs(cm, cm.getCursor()),
               "Ctrl-Space": (cm) => this.ternServer.complete(cm),
               "Alt-/": (cm) => this.ternServer.complete(cm),
               "Ctrl-/": "toggleComment"
            };

            const defs: any[] = [ECMASCRIPT_DEFS];

            this.ternServer = this.codemirrorService.createTernServer({
               defs: defs,
               useWorker: false
            });
         }

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

         this.codemirrorInstance.on("change", () => {
            this.zone.run(() => {
               this.expressionChange.emit(this.codemirrorInstance.getValue());
            });
         });

         if(!this.sql) {
            this.codemirrorInstance.on(
               "cursorActivity", (cm) => this.ternServer.updateArgHints(cm));
         }

         this.codemirrorInstance.on("inputRead", (cm, changeObj) => {
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
               }
               else {
                  this.delayAutocomplete(() => this.ternServer.complete(cm));
               }
            }
         });
      });
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
}