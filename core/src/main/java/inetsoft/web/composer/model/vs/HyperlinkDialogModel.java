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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.binding.drm.DataRefModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HyperlinkDialogModel implements Serializable {
   public int getLinkType() {
      return linkType;
   }

   public void setLinkType(int linkType) {
      this.linkType = linkType;
   }

   public String getWebLink() {
      return webLink;
   }

   public void setWebLink(String webLink) {
      this.webLink = webLink;
   }

   public String getAssetLinkPath() {
      return assetLinkPath;
   }

   public void setAssetLinkPath(String assetLinkPath) {
      this.assetLinkPath = assetLinkPath;
   }

   public String getAssetLinkId() {
      return assetLinkId;
   }

   public void setAssetLinkId(String assetLinkId) {
      this.assetLinkId = assetLinkId;
   }

   public String getBookmark() {
      return bookmark;
   }

   public void setBookmark(String bookmark) {
      this.bookmark = bookmark;
   }

   public String getTargetFrame() {
      return targetFrame;
   }

   public void setTargetFrame(String targetFrame) {
      this.targetFrame = targetFrame;
   }

   public boolean isSelf() {
      return self;
   }

   public void setSelf(boolean self) {
      this.self = self;
   }

   public String getTooltip() {
      return tooltip;
   }

   public void setTooltip(String tooltip) {
      this.tooltip = tooltip;
   }

   public boolean isDisableParameterPrompt() {
      return disableParameterPrompt;
   }

   public void setDisableParameterPrompt(boolean disableParameterPrompt) {
      this.disableParameterPrompt = disableParameterPrompt;
   }

   public boolean isSendViewsheetParameters() {
      return sendViewsheetParameters;
   }

   public void setSendViewsheetParameters(boolean sendViewsheetParameters) {
      this.sendViewsheetParameters = sendViewsheetParameters;
   }

   public boolean isSendSelectionsAsParameters() {
      return sendSelectionsAsParameters;
   }

   public void setSendSelectionsAsParameters(boolean sendSelectionsAsParameters) {
      this.sendSelectionsAsParameters = sendSelectionsAsParameters;
   }

   public List<InputParameterDialogModel> getParamList() {
      if(paramList == null) {
         return new ArrayList<>();
      }

      return paramList;
   }

   public void setParamList(List<InputParameterDialogModel> paramList) {
      this.paramList = paramList;
   }

   public List<DataRefModel> getFields() {
      if(fields == null) {
         return new ArrayList<>();
      }

      return fields;
   }

   public void setFields(List<DataRefModel> fields) {
      this.fields = fields;
   }

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

   public String getColName() {
      return colName;
   }

   public void setColName(String colName) {
      this.colName = colName;
   }

   public boolean isTable() {
      return table;
   }

   public void setTable(boolean table) {
      this.table = table;
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

   public boolean isApplyToRow() {
      return applyToRow;
   }

   public void setApplyToRow(boolean applyToRow) {
      this.applyToRow = applyToRow;
   }

   public boolean isShowRow() {
      return showRow;
   }

   public void setShowRow(boolean showRow) {
      this.showRow = showRow;
   }

   public DataRefModel[] getGrayedOutFields() {
      return grayedOutFields;
   }

   public void setGrayedOutFields(DataRefModel[] grayedOutFields) {
      this.grayedOutFields = grayedOutFields;
   }

   @Override
   public String toString() {
      return "HyperlinkDialogModel{" +
         "webLink=" + webLink +
         ", assetLinkPath=" + assetLinkPath +
         ", assetLinkId=" + assetLinkId +
         ", bookmark=" + bookmark +
         ", targetFrame=" + targetFrame +
         ", self=" + self +
         ", tooltip=" + tooltip +
         ", disableParameterPrompt=" + disableParameterPrompt +
         ", sendViewsheetParameters=" + sendViewsheetParameters +
         ", sendSelectionsAsParameters=" + sendSelectionsAsParameters +
         ", applyToRow=" + applyToRow +
         ", showRow=" + showRow +
         ", paramList=" + paramList +
         ", row=" + row +
         ", col=" + col +
         ", isTable=" + table +
         '}';
   }

   private int linkType;
   private String webLink;
   private String assetLinkPath;
   private String assetLinkId;
   private String bookmark;
   private String targetFrame;
   private boolean self;
   private String tooltip;
   private boolean disableParameterPrompt;
   private boolean sendViewsheetParameters;
   private boolean sendSelectionsAsParameters;
   private List<InputParameterDialogModel> paramList;
   private List<DataRefModel> fields;
   private int row;
   private int col;
   private String colName;
   private boolean table;
   private boolean axis;
   private boolean text;
   private boolean applyToRow;
   private boolean showRow;
   private DataRefModel[] grayedOutFields;
}
