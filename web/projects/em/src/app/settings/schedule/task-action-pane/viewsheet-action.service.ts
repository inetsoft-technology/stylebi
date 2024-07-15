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
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { BookmarkListModel } from "../model/bookmark-list-model";
import { HighlightListModel } from "../model/highlight-list-model";
import { ViewsheetParametersModel } from "../model/viewsheet-parameters-model";
import { ViewsheetTreeListModel } from "../model/viewsheet-tree-list-model";

@Injectable({
   providedIn: "root"
})
export class ViewsheetActionService {
   constructor(private http: HttpClient) {
   }

   getHighlights(id: string): Observable<HighlightListModel> {
      const params = new HttpParams().set("id", Tool.byteEncode(id));
      return this.http.get<HighlightListModel>(
         "../api/em/schedule/task/action/viewsheet/highlights", {params});
   }

   getParameters(id: string): Observable<ViewsheetParametersModel> {
      const params = new HttpParams().set("id", Tool.byteEncode(id));
      return this.http.get<ViewsheetParametersModel>(
         "../api/em/schedule/task/action/viewsheet/parameters", {params});
   }

   getViewsheets(): Observable<any> {
      return this.http.get<any>(
         "../api/em/schedule/task/action/viewsheets", {});
   }

   getTableDataAssemblies(id: string): Observable<string[]> {
      const params = new HttpParams().set("id", Tool.byteEncode(id));
      return this.http.get<string[]>(
         "../api/em/schedule/task/action/viewsheet/tableDataAssemblies", {params});
   }

   getBookmarks(id: string): Observable<BookmarkListModel> {
      const params = new HttpParams().set("id", Tool.byteEncode(id));
      return this.http.get<BookmarkListModel>(
         "../api/em/schedule/task/action/bookmarks", {params});
   }

   getFolders(): Observable<ViewsheetTreeListModel> {
      return this.http.get<ViewsheetTreeListModel>(
         "../api/em/schedule/task/action/viewsheet/folders");
   }

   hasPrintLayout(id: string): Observable<boolean> {
      const params = new HttpParams().set("id", Tool.byteEncode(id));
      return this.http.get<boolean>(
         "../api/em/schedule/task/action/hasPrintLayout", {params});
   }
}
