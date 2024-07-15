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
import {
   AfterViewChecked,
   AfterViewInit,
   Component,
   ElementRef,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   ViewChild
} from "@angular/core";
import {GuiTool} from "../../../../../../../portal/src/app/common/util/gui-tool";
import {CodemirrorService} from "../../../../../../../shared/util/codemirror/codemirror.service";
import {CustomSSOAttributesModel} from "../sso-settings-model";
import {DefaultCodemirrorService} from "../../../../../../../shared/util/codemirror/default-codemirror.service";

@Component({
   selector: "em-custom-sso-form",
   templateUrl: "./custom-sso-form.component.html",
   styleUrls: ["./custom-sso-form.component.scss"],
   providers: [{
      provide: CodemirrorService,
      useClass: DefaultCodemirrorService,
      deps: []
   }]
})
export class CustomSsoFormComponent implements OnInit, AfterViewInit, AfterViewChecked, OnDestroy {
   @ViewChild("scriptEditor", { static: true }) scriptEditor: ElementRef;
   @ViewChild("scriptEditorContainer", { static: true }) scriptEditorContainer: ElementRef;

   @Input()
   get model(): CustomSSOAttributesModel {
      return this._model;
   }

   set model(model: CustomSSOAttributesModel) {
      this._model = model;

      if(this.model?.useJavaClass == this.model?.useInlineGroovy || !this.model?.useInlineGroovy) {
         this.model.useJavaClass = true;
         this.model.useInlineGroovy = false;
      }

      if(model && this.codemirrorInstance && this.codemirrorInstance.getValue() !== model.inlineGroovyClass) {
         const pos = this.codemirrorInstance.getCursor("from");
         this.setInlineGroovyClass(model.inlineGroovyClass || "");
         this.codemirrorInstance.setCursor(pos);
      }
   }

   private _model: CustomSSOAttributesModel;
   private codemirrorInstance: any;
   private refreshRequired = true;

   get classType(): string {
      return !!this.model?.useInlineGroovy ? "groovy" : "java";
   }

   set classType(value: string) {
      if(this.model) {
         this.model.useInlineGroovy = value === "groovy";
         this.model.useJavaClass = !this.model.useInlineGroovy;

         if(this.model.useInlineGroovy && !this.model.inlineGroovyClass) {
            this.model.inlineGroovyClass = `import javax.servlet.*
import inetsoft.web.security.*

class CustomSSOFilter extends AbstractSecurityFilter {
    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // TODO add filter logic
        chain.doFilter(request, response)
    }
}`;
            this.setInlineGroovyClass(this.model.inlineGroovyClass);
         }
      }
   }

   get javaClassName(): string {
      return this.model?.javaClassName;
   }

   set javaClassName(javaClassName: string) {
      if(!!this.model) {
         this.model.javaClassName = javaClassName;
      }
   }

   constructor(private codeMirrorService: CodemirrorService, private zone: NgZone) {
   }

   ngOnInit(): void {
   }

   ngAfterViewInit(): void {
      this.initCodeMirror();
   }

   ngAfterViewChecked(): void {
      const visible = !!GuiTool.getElementRect(this.scriptEditorContainer.nativeElement);

      if(!this.refreshRequired && !visible) {
         // created with display:none, need to refresh the code mirror instance when displayed
         this.refreshRequired = true;
      }
      else if(this.refreshRequired && visible) {
         this.refreshRequired = false;
         this.zone.runOutsideAngular(() => {
            this.codemirrorInstance.refresh();
            this.codemirrorInstance.focus();
         });
      }
   }

   ngOnDestroy(): void {
      this.destroyCodeMirror();
   }

   private initCodeMirror(): void {
      this.zone.runOutsideAngular(() => {
         const config: any = {
            lineNumbers: true,
            lineWrapping: false,
            mode: "groovy",
            foldGutter: true,
            gutters: [
               "CodeMirror-linenumbers",
               "CodeMirror-foldgutter"
            ],
            matchBrackets: true,
            theme: "inetsoft"
         };

         this.codemirrorInstance =
            this.codeMirrorService.createCodeMirrorInstance(this.scriptEditor.nativeElement, config);

         if(this.model) {
            this.setInlineGroovyClass(this.model.inlineGroovyClass || "");
         }

         this.codemirrorInstance.on("change", () => {
            this.zone.run(() => {
               if(this.model) {
                  this.model.inlineGroovyClass = this.codemirrorInstance.getValue();
               }
            });
         });
      });
   }

   private destroyCodeMirror(): void {
      if(this.codemirrorInstance) {
         this.codemirrorInstance.toTextArea();
         this.codemirrorInstance = null;
      }
   }

   setInlineGroovyClass(value: string) {
      this.codemirrorInstance.setValue(value);
      setTimeout(() => this.codemirrorInstance.refresh(), 0);
   }
}
