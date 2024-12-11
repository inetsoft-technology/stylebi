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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { Tool } from "../../../../../../../shared/util/tool";
import { DataSpaceFileContentModel } from "./data-space-file-content-model";
import { DataSpaceTreeDataSource } from "../data-space-tree-data-source";

@Component({
   selector: "em-text-file-content-view",
   templateUrl: "./text-file-content-view.component.html",
   styleUrls: ["./text-file-content-view.component.scss"]
})
export class TextFileContentViewComponent implements OnInit, OnChanges {
   @Output() contentChanged = new EventEmitter<DataSpaceFileContentModel>();

   private _path: string;
   private _content: string;
   private _editMode: boolean;
   editable: boolean;
   loading: boolean = false;

   @Input() set path(p: string) {
      this._path = p;
   }

   get path(): string {
      return this._path;
   }

   @Input() set editMode(editMode: boolean) {
      this._editMode = editMode;
   }

   get editMode(): boolean {
      return this._editMode;
   }

   constructor(private http: HttpClient, private dataSource: DataSpaceTreeDataSource) {
      this.dataSource.nodeSelected().subscribe((node) => {
         if(this.path) {
            this.fetchContent();
         }
      });
   }

   ngOnInit() {
      this.editable = false;
      this.content = "";
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.path || changes.editMode) {
         this.fetchContent();
      }
   }

   fetchContent() {
      const params = new HttpParams()
         .set("path", Tool.byteEncode(this.path))
         .set("preview", !this.editMode + "");

      if(this.editMode) {
         this.loading = true;
      }

      this.http.get("../api/em/content/data-space/file/content", {params: params})
         .subscribe((model: DataSpaceFileContentModel) => {
               this.content = model.content;
               this.editable = model.editable;
            },
            () => {
               this.loading = false;
            },
            () => {
               this.loading = false;
            });
   }

   saveContent() {
      const content = <DataSpaceFileContentModel>{
         content: this.content,
         path: this.path,
         editable: true
      };

      this.contentChanged.emit(content);
   }

   set content(content: string) {
      // eslint-disable-next-line no-control-regex
      this._content = content.replace(/\u0000/g, "");
   }

   get content(): string {
      return this._content;
   }
}
