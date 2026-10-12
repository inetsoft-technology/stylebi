/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { UntypedFormBuilder } from "@angular/forms";
import { describe, expect, it } from "vitest";
import { ScheduleTaskEditorComponent } from "./schedule-task-editor.component";

// Bug #77856, a task name made only of whitespace is required, like an empty one
describe("ScheduleTaskEditorComponent (portal) task name", () => {
   function create(): ScheduleTaskEditorComponent {
      const editor = new ScheduleTaskEditorComponent(
         null, null, null, null, new UntypedFormBuilder(), null, null, null);
      editor.model = { label: "Task" } as any;
      return editor;
   }

   it.each(["", " ", "   "])("rejects %j as required", (name) => {
      const editor = create();
      const control = editor.form.get("name");
      control.setValue(name);

      expect(control.errors).toEqual({ required: true });
      expect(editor.form.valid).toBe(false);
   });

   it.each(["Task", " a", "a "])("accepts %j", (name) => {
      const editor = create();
      editor.form.get("name").setValue(name);

      expect(editor.form.valid).toBe(true);
      // the name is saved as typed, not trimmed
      expect(editor.model.label).toBe(name);
   });
});
