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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { TabularView } from "../../common/data/tabular/tabular-view";
import { TabularButton } from "../../common/data/tabular/tabular-button";
import { Observable } from "rxjs";
import { TreeNodeModel } from "../tree/tree-node-model";
import { TabularGrid } from "./tabular-grid";
import { TabularGridCell } from "./tabular-grid-cell";
import { TabularGooglePickerEditor } from "./google-picker/tabular-google-picker-editor.component";
import { TabularAutocompleteEditor } from "./tabular-autocomplete-editor.component";
import { TabularFileEditor } from "./tabular-file-editor.component";
import { TabularListEditor } from "./tabular-list-editor.component";
import { TabularRestParametersEditorComponent } from "./tabular-rest-parameters-editor.component";
import { TabularQueryParameterEditor } from "./tabular-query-parameter-editor.component";
import { TabularHttpParameterEditorComponent } from "./tabular-http-parameter-editor.component";
import { TabularDateEditor } from "./tabular-date-editor.component";
import { TabularColumnDefinitionEditor } from "./tabular-column-definition-editor.component";
import { TabularTagsEditor } from "./tabular-tags-editor.component";
import { TabularNumberEditor } from "./tabular-number-editor.component";
import { TabularBooleanEditor } from "./tabular-boolean-editor.component";
import { TabularTextEditor } from "./tabular-text-editor.component";
import { NgTemplateOutlet } from "@angular/common";

@Component({
    selector: "tabular-view",
    templateUrl: "tabular-view.component.html",
    styleUrls: ["tabular-view.component.scss"],
    imports: [NgTemplateOutlet, TabularTextEditor, TabularBooleanEditor, TabularNumberEditor, TabularTagsEditor, TabularColumnDefinitionEditor, TabularDateEditor, TabularHttpParameterEditorComponent, TabularQueryParameterEditor, TabularRestParametersEditorComponent, TabularListEditor, TabularFileEditor, TabularAutocompleteEditor, TabularGooglePickerEditor]
})
export class TabularViewComponent {
   @Input() browseFunction: (path: string, property: string) => Observable<TreeNodeModel>;
   @Input() panel = false;
   @Input() cancelButtonExists = false;
   @Output() viewChange = new EventEmitter<TabularView[]>();
   @Output() validChange = new EventEmitter<boolean>();
   @Output() buttonClick = new EventEmitter<TabularButton>();
   tabularGrid: TabularGrid;
   _rootView: TabularView;

   @Input() set rootView(view: TabularView) {
      const grid = new TabularGrid(view);
      this._rootView = view;

      if(this.tabularGrid) {
         grid.copyValid(this.tabularGrid);
      }

      this.tabularGrid = grid;
      this.validChange.emit(this.tabularGrid.isValid());
   }

   get rootView() {
      return this._rootView;
   }

   buttonClicked(view: TabularView): void {
      const button = view.button;
      button.clicked = true;

      if(button.type == "URL") {
         if(button.url != null && button.url.length > 0) {
            window.open(button.url);
         }

         button.clicked = false;
      }
      else {
         if(this.cancelButtonExists && (button.type == "METHOD" || button.type == "REFRESH")) {
            button.loading = true;
         }

         const views = [view, this.rootView];
         this.viewChange.emit(views);
         this.buttonClick.emit(button);
      }
   }

   validChanged(valid: boolean, cell: TabularGridCell): void {
      cell.valid = valid;
      this.validChange.emit(this.tabularGrid.isValid());
   }

   viewChanged(view: TabularView): void {
      // Send changed view and its parent view to identify it
      this.viewChange.emit([view, this.rootView]);
   }

   viewsChanged(views: TabularView[]): void {
      if(views.length == 1) {
         this.viewChanged(views[0]);
      }

      // Send changed view and its parent view to identify it
      this.viewChange.emit(views);
   }
}
