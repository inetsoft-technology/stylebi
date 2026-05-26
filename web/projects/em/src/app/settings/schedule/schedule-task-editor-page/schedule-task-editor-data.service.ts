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
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { ScheduleTaskEditorModel } from "../../../../../../shared/schedule/model/schedule-task-editor-model";

export const EDIT_TASKS_URI = "../api/em/schedule/edit";
export const SAVE_TASK_URI = "../api/em/schedule/task/save";

@Injectable({ providedIn: "root" })
export class ScheduleTaskEditorDataService {
   constructor(private http: HttpClient) {}

   loadTask(taskName: string): Observable<ScheduleTaskDialogModel> {
      const params = new HttpParams().set("taskName", taskName);
      return this.http.get<ScheduleTaskDialogModel>(EDIT_TASKS_URI, { params });
   }

   saveTask(model: ScheduleTaskEditorModel): Observable<ScheduleTaskDialogModel> {
      return this.http.post<ScheduleTaskDialogModel>(SAVE_TASK_URI, model);
   }
}
