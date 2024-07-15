/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.model.vs;

import inetsoft.web.binding.drm.DataRefModel;

import javax.annotation.Nullable;
import java.util.List;

public class HighlightDialogModel {
   public int getRow() {
      return row;
   }

   public void setRow(int row) {
      this.row = row;
   }

   public int getCol() {
      return col;
   }

   public void setCol(int col) {
      this.col = col;
   }

   public String getMeasure() {
      return measure;
   }

   public void setMeasure(String measure) {
      this.measure = measure;
   }

   public boolean isTableAssembly() {
      return tableAssembly;
   }

   public void setTableAssembly(boolean tableAssembly) {
      this.tableAssembly = tableAssembly;
   }

   public boolean isChartAssembly() {
      return chartAssembly;
   }

   public void setChartAssembly(boolean chartAssembly) {
      this.chartAssembly = chartAssembly;
   }

   public boolean isShowRow() {
      return showRow;
   }

   public void setShowRow(boolean showRow) {
      this.showRow = showRow;
   }

   public boolean isShowFont() {
      return showFont;
   }

   public void setShowFont(boolean showFont) {
      this.showFont = showFont;
   }

   public boolean isConfirmChanges() {
      return confirmChanges;
   }

   public void setConfirmChanges(boolean confirmChanges) {
      this.confirmChanges = confirmChanges;
   }

   public boolean isImageObj() {
      return imageObj;
   }

   public void setImageObj(boolean imageObj) {
      this.imageObj = imageObj;
   }

   public HighlightModel[] getHighlights() {
      return highlights;
   }

   public void setHighlights(HighlightModel[] highlights) {
      this.highlights = highlights;
   }

   public DataRefModel[] getFields() {
      return fields;
   }

   public void setFields(DataRefModel[] fields) {
      this.fields = fields;
   }

   public String getTableName() {
      return tableName;
   }

   public void setTableName(String tableName) {
      this.tableName = tableName;
   }

   public void setUsedHighlightNames(String[] usedHighlightNames) {
      this.usedHighlightNames = usedHighlightNames;
   }

   public String[] getUsedHighlightNames() {
      return this.usedHighlightNames;
   }

   @Nullable
   public List<String> getNonsupportBrowseFields() {
      return nonsupportBrowseFields;
   }

   public void setNonsupportBrowseFields(List<String> nonsupportBrowseFields) {
      this.nonsupportBrowseFields = nonsupportBrowseFields;
   }

   public boolean isAxis() {
      return axis;
   }

   public void setAxis(boolean axis) {
      this.axis = axis;
   }

   public boolean isText() {
      return text;
   }

   public void setText(boolean text) {
      this.text = text;
   }

    private int row;
    private int col;
    private String measure;
    private boolean imageObj;
    private boolean tableAssembly;
    private boolean chartAssembly;
    private boolean showRow;
    private boolean showFont;
    private boolean confirmChanges;
    private HighlightModel[] highlights;
    private DataRefModel[] fields;
    private String tableName;
    private String[] usedHighlightNames;
    private List<String> nonsupportBrowseFields;
    private boolean axis;
    private boolean text;
}
