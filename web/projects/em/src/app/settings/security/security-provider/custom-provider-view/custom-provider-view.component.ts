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
   OnDestroy,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { GuiTool } from "../../../../../../../portal/src/app/common/util/gui-tool";
import { CodemirrorService } from "../../../../../../../shared/util/codemirror/codemirror.service";
import { CustomProviderModel } from "../security-provider-model/custom-provider-model";
import { take } from "rxjs/operators";
import { Subscription } from "rxjs";
import {
   DefaultCodemirrorService
} from "../../../../../../../shared/util/codemirror/default-codemirror.service";

@Component({
   selector: "em-custom-provider-view",
   templateUrl: "./custom-provider-view.component.html",
   styleUrls: ["./custom-provider-view.component.scss"],
   providers: [{
      provide: CodemirrorService,
      useClass: DefaultCodemirrorService,
      deps: []
   }]
})
export class CustomProviderViewComponent implements OnInit, AfterViewInit, AfterViewChecked, OnDestroy {
   @Input() form: UntypedFormGroup;
   @Input() isAuthentication = false;
   @Output() changed = new EventEmitter<void>();
   @ViewChild("scriptEditor", { static: true }) scriptEditor: ElementRef;
   @ViewChild("scriptEditorContainer", { static: true }) scriptEditorContainer: ElementRef;

   private _model: CustomProviderModel;
   private codemirrorInstance: any;
   private refreshRequired = true;
   private valueChangeSubscription: Subscription;

   @Input()
   get model(): CustomProviderModel {
      return this._model;
   }

   set model(model: CustomProviderModel) {
      this._model = model;

      if(!!this.valueChangeSubscription) {
         this.valueChangeSubscription.unsubscribe();
      }

      if(model && this.form) {
         const cForm: UntypedFormGroup = <UntypedFormGroup>this.form.controls["customForm"];
         cForm.controls["className"].setValue(model.className);
         cForm.controls["jsonConfiguration"].setValue(model.jsonConfiguration);
      }

      if(model && this.codemirrorInstance && this.codemirrorInstance.getValue() !== model.jsonConfiguration) {
         const pos = this.codemirrorInstance.getCursor("from");
         this.setValue(model.jsonConfiguration || "");
         this.codemirrorInstance.setCursor(pos);
      }

      if(model && this.form) {
         this.valueChangeSubscription = this.form.controls["customForm"].valueChanges.pipe(take(1))
            .subscribe(() => this.changed.emit());
      }
   }

   constructor(private codemirrorService: CodemirrorService, private zone: NgZone) {
   }

   ngOnInit(): void {
      this.initForm();
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

   initForm() {
      this.form.addControl("customForm",
         new UntypedFormGroup({
            className: new UntypedFormControl("", [Validators.required]),
            jsonConfiguration: new UntypedFormControl("")
         }));
   }

   private initCodeMirror(): void {
      this.zone.runOutsideAngular(() => {
         const config: any = {
            lineNumbers: true,
            lineWrapping: false,
            mode: "javascript",
            json: true,
            foldGutter: true,
            gutters: [
               "CodeMirror-linenumbers",
               "CodeMirror-foldgutter"
            ],
            matchBrackets: true,
            theme: "inetsoft"
         };

         this.codemirrorInstance =
            this.codemirrorService.createCodeMirrorInstance(this.scriptEditor.nativeElement, config);

         if(this.model) {
            this.setValue(this.model.jsonConfiguration || "");
         }

         this.codemirrorInstance.on("change", () => {
            this.zone.run(() => {
               if(this.form) {
                  const cForm: UntypedFormGroup = <UntypedFormGroup>this.form.controls["customForm"];
                  cForm.controls["jsonConfiguration"].setValue(this.codemirrorInstance.getValue());
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

   setValue(value: string) {
      this.codemirrorInstance.setValue(value);
      setTimeout(() => this.codemirrorInstance.refresh(), 0);
   }
}
