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
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

let dataView: TreeNodeModel = {
   label: "_#(js:Data View)",
   children: [
      {
         label: "_#(js:Chart)",
         icon: "chart-icon",
         leaf: true,
         dragName: "dragchart"
      },
      {
         label: "_#(js:Crosstab)",
         icon: "crosstab-icon",
         leaf: true,
         dragName: "dragcrosstab"
      },
      {
         label: "_#(js:Table)",
         icon: "table-icon",
         leaf: true,
         dragName: "dragtable"
      },
      {
         label: "_#(js:Freehand Table)",
         icon: "formula-table-icon",
         leaf: true,
         dragName: "dragfreehandtable"
      }
   ],
   icon: "folder-toolbox-icon",
};

let filter: TreeNodeModel = {
   label: "_#(js:Filter)",
   children: [
      {
         label: "_#(js:Selection List)",
         icon: "selection-list-icon",
         leaf: true,
         dragName: "dragselectionlist"
      },
      {
         label: "_#(js:Selection Tree)",
         icon: "selection-tree-icon",
         leaf: true,
         dragName: "dragselectiontree"
      },
      {
         label: "_#(js:Range Slider)",
         icon: "range-slider-icon",
         leaf: true,
         dragName: "dragrangeslider"
      },
      {
         label: "_#(js:Calendar)",
         icon: "calendar-icon",
         leaf: true,
         dragName: "dragcalendar"
      },
      {
         label: "_#(js:Selection Container)",
         icon: "selection-container-icon",
         leaf: true,
         dragName: "dragselectioncontainer"
      },
   ],
   icon: "folder-toolbox-icon"
};

let output: TreeNodeModel = {
   label: "_#(js:Output)",
   children: [
      {
         label: "_#(js:Text)",
         icon: "text-box-icon",
         leaf: true,
         dragName: "dragtext"
      },
      {
         label: "_#(js:Image)",
         icon: "image-icon",
         leaf: true,
         dragName: "dragimage"
      },
      {
         label: "_#(js:Gauge)",
         icon: "gauge-icon",
         leaf: true,
         dragName: "draggauge"
      },
   ],
   icon: "folder-toolbox-icon"
};

let form: TreeNodeModel = {
   label: "_#(js:Form)",
   children: [
      {
         label: "_#(js:Slider)",
         icon: "slider-icon",
         leaf: true,
         dragName: "dragslider"
      },
      {
         label: "_#(js:Spinner)",
         icon: "spinner-icon",
         leaf: true,
         dragName: "dragspinner"
      },
      {
         label: "_#(js:CheckBox)",
         icon: "checkbox-icon",
         leaf: true,
         dragName: "dragcheckbox"
      },
      {
         label: "_#(js:RadioButton)",
         icon: "radio-button-icon",
         leaf: true,
         dragName: "dragradiobutton"
      },
      {
         label: "_#(js:ComboBox)",
         icon: "dropdown-box-icon",
         leaf: true,
         dragName: "dragcombobox"
      },
      {
         label: "_#(js:TextInput)",
         icon: "text-input-icon",
         leaf: true,
         dragName: "dragtextinput"
      },
      {
         label: "_#(js:Submit)",
         icon: "submit-icon",
         leaf: true,
         dragName: "dragsubmit"
      },
   ],
   icon: "folder-toolbox-icon"
};

let shape: TreeNodeModel = {
   label: "_#(js:Shape)",
   children: [
      {
         label: "_#(js:Line)",
         icon: "line-icon",
         leaf: true,
         dragName: "dragline"
      },
      {
         label: "_#(js:Rectangle)",
         icon: "rectangle-icon",
         leaf: true,
         dragName: "dragrectangle"
      },
      {
         label: "_#(js:Oval)",
         icon: "oval-icon",
         leaf: true,
         dragName: "dragoval"
      },
   ],
   icon: "folder-toolbox-icon"
};

export const toolbox: TreeNodeModel = {
   label: "_#(js:Toolbox)",
   children: [
      dataView,
      filter,
      output,
      form,
      shape,
   ],
   icon: "folder-toolbox-icon"
};

export const toolboxDeployed: TreeNodeModel = {
   label: "_#(js:Toolbox)",
   children: [
      {
         label: "_#(js:Text)",
         icon: "text-box-icon",
         leaf: true,
         dragName: "dragtext"
      },
      {
         label: "_#(js:Image)",
         icon: "image-icon",
         leaf: true,
         dragName: "dragimage"
      }
   ],
   icon: "folder-toolbox-icon"
};
