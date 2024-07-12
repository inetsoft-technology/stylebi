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
import { StyleConstants } from "./style-constants";
import { TableStyleModel } from "../../composer/data/tablestyle/table-style-model";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { Tool } from "../../../../../shared/util/tool";
import { AssetConstants } from "../data/asset-constants";
import { AssetType } from "../../../../../shared/data/asset-type";
import { SpecificationModel } from "../../composer/data/tablestyle/specification-model";

export class TableStyleUtil {
   public static deleteCustom(style: TableStyleModel, specId: number): void {
      if(style.styleFormat == null) {
         return;
      }

      let newSpecList = [];
      let specList = style.styleFormat.specList;

      for(let i = 0; i < specList.length; i++) {
         let nspec = specList[i];

         if(i < specId) {
            newSpecList.push(nspec);
         }
         else if(i > specId) {
            nspec.id--;
            newSpecList.push(nspec);
         }
      }

      style.isModified = true;
      style.styleFormat.specList = newSpecList;
      style.selectedRegion = TableStyleUtil.BODY;
   }


   /**  1. There are 4 cases when add table style to undo list.
    *  (1) Index is the end and count is 10 (get list after first and push new style)
    *  (2) Index is in the middle but count is 10 (get list before index and push new style)
    *  (3) Index is in the end but count is smaller than 10 (push new style)
    *  (4) Index is in the middle and count is smaller than 10 (push new style)
    *  (5) list is empty (push new style)
    */
   public static addUndoList(style: TableStyleModel) {
      let nstyle = Tool.clone(style.styleFormat);
      let origianlIndex = style.styleFormat.origianlIndex;
      let newList = [];

      if(!style.undoRedoList) {
         style.undoRedoList = newList;
      }

      let currentIndex = style.currentIndex;

      // (1) if current Index is equals to 9, it means length is 10 and index is in the end.
      if(currentIndex == TableStyleUtil.UNDO_REDO_COUNT - 1) {
         for(let i = 1; i < TableStyleUtil.UNDO_REDO_COUNT; i++) {
            newList.push(style.undoRedoList[i]);
         }

         origianlIndex--;
         nstyle.origianlIndex = origianlIndex == 0 ? -1 : origianlIndex;
         style.undoRedoList = newList;
      }
      // (2) index is in the middle but count is 10, remove elements after currentIndex and push
      // new style.
      else if(style.undoRedoList.length == TableStyleUtil.UNDO_REDO_COUNT) {
         for(let i = currentIndex + 1; i < TableStyleUtil.UNDO_REDO_COUNT; i++) {
            style.undoRedoList.pop();
         }
      }

      // (1) (2) (3) (4) (5) all cases should push new style to list.
      style.undoRedoList.push(nstyle);
      style.currentIndex = style.undoRedoList.length - 1;
   }

   public static selectRegionTree(style: TableStyleModel) {
      if(style == null || style.regionsTreeRoot == null) {
         return;
      }

      let root = style.regionsTreeRoot;
      let defaultRegion = root.children[0];
      let customRegion = root.children[1];

      for(let i = 0; i < defaultRegion.children.length; i++) {
         if(defaultRegion.children[i].data == style.selectedRegion) {
            style.selectedTreeNode = defaultRegion.children[i];
            style.selectedRegionLabel = defaultRegion.children[i].label;
            return;
         }
      }

      for(let i = 0; i < customRegion.children.length; i++) {
         if(customRegion.children[i].data == style.selectedRegion) {
            style.selectedTreeNode = customRegion.children[i];
            style.selectedRegionLabel = customRegion.children[i].label;
            return;
         }
      }
   }

   public static updateCustomLabel(styleModel: TableStyleModel) {
      let selectedRegion = styleModel.selectedRegion;

      if(!TableStyleUtil.isDefaultRegion(selectedRegion)) {
         let id = parseInt(selectedRegion, 10);

         if(id > 0 || id < styleModel.styleFormat.specList.length) {
            styleModel.selectedRegionLabel = styleModel.styleFormat.specList[id].label;
         }
      }
   }

   public static isDefaultRegion(region: string) {
      return region == TableStyleUtil.TOP_BORDER || region == TableStyleUtil.LEFT_BORDER ||
         region == TableStyleUtil.RIGHT_BORDER || region == TableStyleUtil.BOTTOM_BORDER ||
         region == TableStyleUtil.HEADER_ROW || region == TableStyleUtil.HEADER_COLUMN ||
         region == TableStyleUtil.TRAILER_ROW || region == TableStyleUtil.TRAILER_COLUMN ||
         region == TableStyleUtil.BODY;
   }

   public static isBorderRegion(selectedRegion: string): boolean {
      return selectedRegion == TableStyleUtil.TOP_BORDER ||
         selectedRegion == TableStyleUtil.LEFT_BORDER ||
         selectedRegion == TableStyleUtil.RIGHT_BORDER ||
         selectedRegion == TableStyleUtil.BOTTOM_BORDER;
   }

   public static isGroupTotal(selectedRegion: string,specList: SpecificationModel[]): boolean {
      if(!TableStyleUtil.isDefaultRegion(selectedRegion)) {
         let spec: SpecificationModel = specList[parseInt(selectedRegion, 10)];

         return spec.customType == TableStyleUtil.ROW_GROUP_TOTAL ||
            spec.customType == TableStyleUtil.COLUMN_GROUP_TOTAL;
      }

      return false;
   }

   public static initRegionsTree(styleModel: TableStyleModel) {
      let root: TreeNodeModel = { label: "", data: "", children: [] };
      root.children.push(this.createDefaultRegionNode());
      root.children.push(this.createCustomNode(styleModel));

      styleModel.regionsTreeRoot = root;
   }

   private static createDefaultRegionNode(): TreeNodeModel {
      let root = <TreeNodeModel> {
         label: "_#(js:Default Regions)",
         leaf: false,
         children: []
      };

      let regions = [
         { label: "_#(js:Top Border)", data: TableStyleUtil.TOP_BORDER },
         { label: "_#(js:Bottom Border)", data: TableStyleUtil.BOTTOM_BORDER },
         { label: "_#(js:Left Border)", data: TableStyleUtil.LEFT_BORDER },
         { label: "_#(js:Right Border)", data: TableStyleUtil.RIGHT_BORDER },
         { label: "_#(js:Header Row)", data: TableStyleUtil.HEADER_ROW },
         { label: "_#(js:Trailer Row)", data: TableStyleUtil.TRAILER_ROW },
         { label: "_#(js:Header Column)", data: TableStyleUtil.HEADER_COLUMN },
         { label: "_#(js:Trailer Column)", data: TableStyleUtil.TRAILER_COLUMN },
         { label: "_#(js:Body)", data: TableStyleUtil.BODY }
      ];

      for(let i = 0; i < regions.length; i++) {
         let child = <TreeNodeModel> {
            label: regions[i].label,
            data: regions[i].data,
            icon: "",
            type: this.REGION,
            leaf: true
         };

         root.children.push(child);
      }

      return root;
   }

   public static createCustomNode(styleModel: TableStyleModel): TreeNodeModel {
      let root = <TreeNodeModel> {
         label: "_#(js:Custom Patterns)",
         type: this.CUSTOM_FOLDER,
         data: 0,
         leaf: false,
         expanded: true,
         children: []
      };

      let specModels = styleModel.styleFormat.specList;

      for(let i = 0; i < specModels.length; i++) {
         let child = <TreeNodeModel> {
            label: specModels[i].label,
            data: specModels[i].id,
            icon: "",
            type: this.CUSTOM,
            leaf: true
         };

         root.children.push(child);
      }

      return root;
   }

   public static styleIdentifier(folder: string, name: string) {
      let path = folder == null ? "Table Style/" +
         name : folder + "/" + name;

      return AssetConstants.COMPONENT_SCOPE + "^" + this.ASSET_TABLE_STYLE +
         "^" + this.ASSET_NULL + "^" + path + "^" + this.ASSET_ORGID;
   }

   private static UNDO_REDO_COUNT: number = 10;
   public static CUSTOM: string = "custom";
   public static CUSTOM_FOLDER: string = "custom_folder";
   public static REGION: string = "region";
   public static BACKGROUND: string = "background";
   public static FOREGROUND: string = "foreground";
   public static ROW_BORDER_COLOR: string = "rowBorderColor";
   public static ROW_BORDER: string = "rowBorder";
   public static COL_BORDER_COLOR: string = "colBorderColor";
   public static COL_BORDER: string = "colBorder";
   public static COLOR: string = "color";
   public static BORDER: string = "border";
   public static TOP_BORDER: string = "Top Border";
   public static LEFT_BORDER: string = "Left Border";
   public static RIGHT_BORDER: string = "Right Border";
   public static BOTTOM_BORDER: string = "Bottom Border";
   public static HEADER_ROW: string = "Header Row";
   public static TRAILER_ROW: string = "Trailer Row";
   public static HEADER_COLUMN: string = "Header Column";
   public static TRAILER_COLUMN: string = "Trailer Column";
   public static BODY: string = "Body";
   public static ROW: string = "Row";
   public static COLUMN: string = "Column";
   public static ROW_GROUP_TOTAL: string = "Row Group Total";
   public static COLUMN_GROUP_TOTAL: string = "Column Group Total";
   public static ASSET_TABLE_STYLE: string = "524288";
   public static ASSET_NULL: string = "__NULL__";
   public static ASSET_ORGID: string = "default_org";

   public static GROUP_LEVELS = [
      { label: "_#(js:Level 1)", value: 1 },
      { label: "_#(js:Level 2)", value: 2 },
      { label: "_#(js:Level 3)", value: 3 },
      { label: "_#(js:Level 4)", value: 4 },
      { label: "_#(js:Level 5)", value: 5 },
      { label: "_#(js:Level 6)", value: 6 },
      { label: "_#(js:Level 7)", value: 7 },
      { label: "_#(js:Level 8)", value: 8 },
      { label: "_#(js:Level 9)", value: 9 },
      { label: "_#(js:Level 10)", value: 10 },
   ];

   public static ALIGBNEBNT_STYLES = [
      { label: "_#(js:None)", value: StyleConstants.NONE },
      { label: "_#(js:Top)", value: StyleConstants.V_TOP },
      { label: "_#(js:Top-Left)", value: StyleConstants.V_TOP | StyleConstants.H_LEFT },
      { label: "_#(js:Left)", value: StyleConstants.H_LEFT },
      { label: "_#(js:Bottom-Left)", value: StyleConstants.V_BOTTOM | StyleConstants.H_LEFT },
      { label: "_#(js:Bottom)", value: StyleConstants.V_BOTTOM },
      { label: "_#(js:Bottom-Right)", value: StyleConstants.V_BOTTOM | StyleConstants.H_RIGHT },
      { label: "_#(js:Right)", value: StyleConstants.H_RIGHT },
      { label: "_#(js:Top-Right)", value: StyleConstants.V_TOP | StyleConstants.H_RIGHT },
      { label: "_#(js:Center)", value: StyleConstants.H_CENTER | StyleConstants.V_CENTER },
   ];

   public static STYLE_BORDER_STYLES = [
      { label: "_#(js:Default)", value: -1, cssClass: null },
      { label: "_#(js:None)", value: StyleConstants.NO_BORDER, cssClass: null },
      { label: null, value: StyleConstants.THIN_LINE, cssClass: "line-style-THIN_LINE" },
      { label: null, value: StyleConstants.MEDIUM_LINE, cssClass: "line-style-MEDIUM_LINE" },
      { label: null, value: StyleConstants.THICK_LINE, cssClass: "line-style-THICK_LINE" },
      { label: null, value: StyleConstants.DOUBLE_LINE, cssClass: "line-style-DOUBLE_LINE" },
      { label: null, value: StyleConstants.DOT_LINE, cssClass: "line-style-DOT_LINE" },
      { label: null, value: StyleConstants.DASH_LINE, cssClass: "line-style-DASH_LINE" },
      { label: null, value: StyleConstants.MEDIUM_DASH, cssClass: "line-style-MEDIUM_DASH" },
      { label: null, value: StyleConstants.LARGE_DASH, cssClass: "line-style-LARGE_DASH" },
   ];
}
