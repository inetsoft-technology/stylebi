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
package inetsoft.web.portal.model.database;

import inetsoft.uql.DrillPath;

import java.util.List;

public class AutoDrillPathModel {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getLink() {
      return link;
   }

   public void setLink(String link) {
      this.link = link;
   }

   public String getTargetFrame() {
      return targetFrame;
   }

   public void setTargetFrame(String targetFrame) {
      this.targetFrame = targetFrame;
   }

   public String getTip() {
      return tip;
   }

   public void setTip(String tip) {
      this.tip = tip;
   }

   public List<DrillParameterModel> getParams() {
      return params;
   }

   public void setParams(List<DrillParameterModel> params) {
      this.params = params;
   }

   public boolean isPassParams() {
      return passParams;
   }

   public void setPassParams(boolean passParams) {
      this.passParams = passParams;
   }

   public boolean isDisablePrompting() {
      return disablePrompting;
   }

   public void setDisablePrompting(boolean disablePrompting) {
      this.disablePrompting = disablePrompting;
   }

   public int getLinkType() {
      return linkType;
   }

   public void setLinkType(int linkType) {
      this.linkType = linkType;
   }

   public DrillSubQueryModel getQuery() {
      return query;
   }

   public void setQuery(DrillSubQueryModel query) {
      this.query = query;
   }

   public List<String> getQueryFields() {
      return queryFields;
   }

   public void setQueryFields(List<String> queryFields) {
      this.queryFields = queryFields;
   }

   private String name = "";
   private String link = "";
   private String targetFrame = "";
   private String tip = "";
   private List<DrillParameterModel> params;
   private boolean passParams = true;
   private boolean disablePrompting;
   private int linkType = DrillPath.WEB_LINK;
   private DrillSubQueryModel query;
   private List<String> queryFields;
}