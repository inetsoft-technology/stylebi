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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.AssetEntry;

import java.awt.*;
import java.util.Date;

/**
 * Class that encapsulates the information required by the viewer to initialize the grid.
 *
 * @since 12.3
 */
public class InitGridCommand implements ViewsheetCommand {
   public String getViewsheetId() {
      return viewsheetId;
   }

   public void setViewsheetId(String viewsheetId) {
      this.viewsheetId = viewsheetId;
   }

   public String getEmbeddedId() {
      return embeddedId;
   }

   public void setEmbeddedId(String embeddedId) {
      this.embeddedId = embeddedId;
   }

   public AssetEntry getEntry() {
      return entry;
   }

   public void setEntry(AssetEntry entry) {
      this.entry = entry;
   }

   public boolean isIniting() {
      return initing;
   }

   public void setIniting(boolean initing) {
      this.initing = initing;
   }

   public Dimension getViewSize() {
      return viewSize;
   }

   public void setViewSize(Dimension viewSize) {
      this.viewSize = viewSize;
   }

   public boolean isEditable() {
      return editable;
   }

   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   public String getLockOwner() {
      return lockOwner;
   }

   public void setLockOwner(String lockOwner) {
      this.lockOwner = lockOwner;
   }

   public Date getLastModified() {
      return lastModified;
   }

   public void setLastModified(Date lastModified) {
      this.lastModified = lastModified;
   }

   public int getScope() {
      return scope;
   }

   public void setScope(int scope) {
      this.scope = scope;
   }

   public float getRuntimeFontScale() {
      return runtimeFontScale;
   }

   public void setRuntimeFontScale(float runtimeFontScale) {
      this.runtimeFontScale = runtimeFontScale;
   }

   public boolean isToolbarVisible() {
      return toolbarVisible;
   }

   public void setToolbarVisible(boolean toolbarVisible) {
      this.toolbarVisible = toolbarVisible;
   }

   public boolean isWallboard() {
      return "true".equals(SreeEnv.getProperty("wallboarding.enabled"));
   }

   public boolean isSingleClick() {
      return "true".equals(SreeEnv.getProperty("viewsheet.hyperlink.singleClick"));
   }

   public boolean isHasSharedFilters() {
      return hasSharedFilters;
   }

   public void setHasSharedFilters(boolean hasSharedFilters) {
      this.hasSharedFilters = hasSharedFilters;
   }

   private String viewsheetId;
   private String embeddedId;
   private AssetEntry entry;
   private boolean initing;
   private Dimension viewSize;
   private boolean editable;
   private String lockOwner;
   private Date lastModified;
   private int scope;
   private float runtimeFontScale;
   private boolean toolbarVisible;
   private boolean hasSharedFilters;
}
