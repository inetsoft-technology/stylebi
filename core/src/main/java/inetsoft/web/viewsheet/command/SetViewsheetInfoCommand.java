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
package inetsoft.web.viewsheet.command;

import inetsoft.uql.asset.AssetEntry;

import java.util.*;

/**
 * Command that encapsulates information about the opened viewsheet to the client.
 *
 * @since 12.3
 */
public class SetViewsheetInfoCommand implements ViewsheetCommand, InitializingCommand {
   public Map<String, Object> getInfo() {
      return info;
   }

   public void setInfo(Map<String, Object> info) {
      this.info = info;
   }

   public Map<String, Object> getAssemblyInfo() {
      return assemblyInfo;
   }

   public void setAssemblyInfo(Map<String, Object> assemblyInfo) {
      this.assemblyInfo = assemblyInfo;
   }

   public AssetEntry getBaseEntry() {
      return baseEntry;
   }

   public void setBaseEntry(AssetEntry baseEntry) {
      this.baseEntry = baseEntry;
   }

   public List<String> getLayouts() {
      if(layouts == null) {
         return new ArrayList<>();
      }

      return layouts;
   }

   public void setLayouts(List<String> layouts) {
      this.layouts = layouts;
   }

   public boolean isAnnotation() {
      return annotation;
   }

   public void setAnnotation(boolean annotation) {
      this.annotation = annotation;
   }

   public boolean isAnnotated() {
      return annotated;
   }

   public void setAnnotated(boolean annotated) {
      this.annotated = annotated;
   }

   public boolean isFormTable() {
      return formTable;
   }

   public void setFormTable(boolean formTable) {
      this.formTable = formTable;
   }

   public String getLinkUri() {
      return linkUri;
   }

   public void setLinkUri(String linkUri) {
      this.linkUri = linkUri;
   }

   public String getAssetId() {
      return assetId;
   }

   public void setAssetId(String assetId) {
      this.assetId = assetId;
   }

   public boolean isHasScript() {
      return hasScript;
   }

   public void setHasScript(boolean hasScript) {
      this.hasScript = hasScript;
   }

   private Map<String, Object> info;
   private Map<String, Object> assemblyInfo;
   private AssetEntry baseEntry;
   private List<String> layouts;
   private boolean annotation;
   private boolean annotated;
   private boolean formTable;
   private String linkUri;
   private String assetId;
   private boolean hasScript;
}
