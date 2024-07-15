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
   EventEmitter,
   Input,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { TabularFile } from "../../common/data/tabular/tabular-file";
import { Observable } from "rxjs";
import { TreeNodeModel } from "../tree/tree-node-model";

@Component({
   selector: "tabular-file-editor",
   templateUrl: "tabular-file-editor.component.html",
   styleUrls: ["tabular-file-editor.component.scss"]
})
export class TabularFileEditor implements OnInit {
   @Input() value: string;
   @Input() property: string;
   @Input() editorPropertyNames: string[];
   @Input() editorPropertyValues: string[];
   @Input() pattern: string[];
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Input() browseFunction: (path: string, property: string) => Observable<TreeNodeModel>;
   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() validChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   @ViewChild("tabularFileBrowser") tabularFileBrowser: TemplateRef<any>;
   path: string;
   relativeTo: string;
   foldersOnly: boolean;

   constructor(private modalService: NgbModal) {
   }

   ngOnInit(): void {
      for(let i = 0; i < this.editorPropertyNames.length; i++) {
         if(this.editorPropertyNames[i] == "relativeTo") {
            this.relativeTo = this.editorPropertyValues[i]
               ? this.editorPropertyValues[i].replace(/\\/g, "/") : "";
         }
         else if(this.editorPropertyNames[i] == "foldersOnly") {
            this.foldersOnly = this.editorPropertyValues[i] == "true";
         }
      }

      // value contains absolute path so trim it to show relative path instead
      if(this.value != null) {
         this.path = this.value.replace(/\\/g, "/");

         if(this.relativeTo && this.path.indexOf(this.relativeTo) == 0 &&
            this.path.length > this.relativeTo.length)
         {
            // find the first slash that comes after the relativeTo property
            // and get the remaining path
            this.path = this.path.substring(
               this.path.indexOf("/", this.relativeTo.length - 1) + 1);
         }
      }

      this.validChange.emit(!this.required || (this.required && this.value != null));
   }

   valueChanged(): void {
      this.valueChange.emit(this.value);
      this.validChange.emit(!this.required || (this.required && this.value != null));
   }

   showFileBrowser(): void {
      this.modalService.open(this.tabularFileBrowser).result.then(
         (result: TabularFile) => {
            this.path = result.path;
            this.value = result.absolutePath;
            this.valueChanged();
         },
         (reject) => {
         }
      );
   }
}
