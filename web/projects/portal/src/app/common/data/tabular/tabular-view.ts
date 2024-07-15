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
import { TabularEditor } from "./tabular-editor";
import { TabularButton } from "./tabular-button";

export interface TabularView {
   type: string;
   text: string;
   color: string;
   font: string;
   value: string;
   row: number;
   col: number;
   rowspan: number;
   colspan: number;
   align: string;
   verticalAlign: string;
   paddingLeft: number;
   paddingRight: number;
   paddingTop: number;
   paddingBottom: number;
   password: boolean;
   displayLabel: string;
   editor: TabularEditor;
   button: TabularButton;
   views: TabularView[];
   required: boolean;
   visible: boolean;
   min: number;
   max: number;
   pattern: string[];
}
