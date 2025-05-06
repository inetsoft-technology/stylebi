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
package inetsoft.web.viewsheet.event;

import inetsoft.uql.asset.AssetEntry;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that encapsulates the parameters for opening a viewsheet.
 *
 * @since 12.3
 */
public class OpenViewsheetEvent {
   /**
    * Gets the asset entry identifier of the viewsheet.
    *
    * @return the entry identifier.
    */
   public String getEntryId() {
      return entryId;
   }

   /**
    * Sets the asset entry identifier of the viewsheet.
    *
    * @param entryId the entry identifier.
    */
   public void setEntryId(String entryId) {
      this.entryId = entryId;
   }

   /**
    * Gets the viewport width of the browser.
    *
    * @return the width.
    */
   public int getWidth() {
      return width;
   }

   /**
    * Sets the viewport width of the browser.
    *
    * @param width the width.
    */
   public void setWidth(int width) {
      this.width = width;
   }

   /**
    * Gets the viewport height of the browser.
    *
    * @return the height.
    */
   public int getHeight() {
      return height;
   }

   /**
    * Sets the viewport height of the browser.
    *
    * @param height the height.
    */
   public void setHeight(int height) {
      this.height = height;
   }

   /**
    * Gets the flag that indicates if the client is a mobile device.
    *
    * @return <tt>true</tt> if a mobile device; <tt>false</tt> otherwise.
    */
   public boolean isMobile() {
      return mobile;
   }

   /**
    * Sets the flag that indicates if the client is a mobile device.
    *
    * @param mobile <tt>true</tt> if a mobile device; <tt>false</tt> otherwise.
    */
   public void setMobile(boolean mobile) {
      this.mobile = mobile;
   }

   /**
    * Gets the runtime identifier of the viewsheet from which this viewsheet was linked.
    *
    * @return the source viewsheet runtime identifier.
    */
   public String getDrillFrom() {
      return drillFrom;
   }

   /**
    * Sets the runtime identifier of the viewsheet from which this viewsheet was linked.
    *
    * @param drillFrom the source viewsheet runtime identifier.
    */
   public void setDrillFrom(String drillFrom) {
      this.drillFrom = drillFrom;
   }

   /**
    * Gets the flag that indicates if this request is to synchronize a viewsheet instance
    * with another.
    *
    * @return <tt>true</tt> to synchronize; <tt>false</tt> otherwise.
    */
   public boolean isSync() {
      return sync;
   }

   /**
    * Sets the flag that indicates if this request is to synchronize a viewsheet instance
    * with another.
    *
    * @param sync <tt>true</tt> to synchronize; <tt>false</tt> otherwise.
    */
   public void setSync(boolean sync) {
      this.sync = sync;
   }

   /**
    * Gets the runtime identifier of the viewsheet that is to be rendered in a full screen
    * view.
    *
    * @return the source viewsheet runtime identifier.
    */
   public String getFullScreenId() {
      return fullScreenId;
   }

   /**
    * Sets the runtime identifier of the viewsheet that is to be rendered in a full screen
    * view.
    *
    * @param fullScreenId the source viewsheet runtime identifier.
    */
   public void setFullScreenId(String fullScreenId) {
      this.fullScreenId = fullScreenId;
   }

   /**
    * Gets the runtime identifier of the viewsheet instance to be opened.
    *
    * @return the runtime viewsheet identifier.
    */
   public String getRuntimeViewsheetId() {
      return runtimeViewsheetId;
   }

   /**
    * Sets the runtime identifier of the viewsheet instance to be opened.
    *
    * @param runtimeViewsheetId the runtime viewsheet identifier.
    */
   public void setRuntimeViewsheetId(String runtimeViewsheetId) {
      this.runtimeViewsheetId = runtimeViewsheetId;
   }

   /**
    * Gets the runtime identifier of the embedded viewsheet.
    *
    * @return the embedded viewsheet identifier.
    */
   public String getEmbeddedViewsheetId() {
      return embeddedViewsheetId;
   }

   /**
    * Sets the runtime identifier of the embedded viewsheet.
    *
    * @param embeddedViewsheetId the embedded viewsheet identifier.
    */
   public void setEmbeddedViewsheetId(String embeddedViewsheetId) {
      this.embeddedViewsheetId = embeddedViewsheetId;
   }

   /**
    * Gets the flag that indicates if the viewsheet will be opened in the viewer.
    *
    * @return <tt>true</tt> if opened in the viewer; <tt>false</tt> if opened in an
    *         editor.
    */
   public boolean isViewer() {
      return viewer;
   }

   /**
    * Sets the flag that indicates if the viewsheet will be opened in the viewer.
    *
    * @param viewer <tt>true</tt> if opened in the viewer; <tt>false</tt> if opened in an
    *               editor.
    */
   public void setViewer(boolean viewer) {
      this.viewer = viewer;
   }

   /**
    * Gets the flag that indicates if the auto-saved version of the viewsheet should be
    * opened.
    *
    * @return <tt>true</tt> to use the auto-saved version; <tt>false</tt> otherwise.
    */
   public boolean isOpenAutoSaved() {
      return openAutoSaved;
   }

   /**
    * Sets the flag that indicates if the auto-saved version of the viewsheet should be
    * opened.
    *
    * @param openAutoSaved <tt>true</tt> to use the auto-saved version; <tt>false</tt>
    *                      otherwise.
    */
   public void setOpenAutoSaved(boolean openAutoSaved) {
      this.openAutoSaved = openAutoSaved;
   }

   /**
    * Gets the flag that indicates if the user has confirmed using the auto-saved file.
    *
    * @return <tt>true</tt> if confirmed; <tt>false</tt> otherwise.
    */
   public boolean isConfirmed() {
      return confirmed;
   }

   /**
    * Sets the flag that indicates if the user has confirmed using the auto-saved file.
    *
    * @param confirmed <tt>true</tt> if confirmed; <tt>false</tt> otherwise.
    */
   public void setConfirmed(boolean confirmed) {
      this.confirmed = confirmed;
   }

   /**
    * Gets the user agent string of the client browser.
    *
    * @return the user agent.
    */
   public String getUserAgent() {
      return userAgent;
   }

   /**
    * Sets the user agent string of the client browser.
    *
    * @param userAgent the user agent.
    */
   public void setUserAgent(String userAgent) {
      this.userAgent = userAgent;
   }

   /**
    * Gets the URL of the page that was loaded in the browser before the viewer page.
    *
    * @return the previous URL.
    */
   public String getPreviousUrl() {
      return previousUrl;
   }

   /**
    * Sets the URL of the page that was loaded in the browser before the viewer page.
    *
    * @param previousUrl the previous URL.
    */
   public void setPreviousUrl(String previousUrl) {
      this.previousUrl = previousUrl;
   }

   /**
    * Gets the name of the bookmark to open.
    *
    * @return the bookmark name.
    */
   public String getBookmarkName() {
      return bookmarkName;
   }

   /**
    * Sets the name of the bookmark to open.
    *
    * @param bookmarkName the bookmark name.
    */
   public void setBookmarkName(String bookmarkName) {
      this.bookmarkName = bookmarkName;
   }

   /**
    * Gets the name of the user that owns the bookmark.
    *
    * @return the bookmark owner.
    */
   public String getBookmarkUser() {
      return bookmarkUser;
   }

   /**
    * Sets the name of the user that owns the bookmark.
    *
    * @param bookmarkUser the bookmark owner.
    */
   public void setBookmarkUser(String bookmarkUser) {
      this.bookmarkUser = bookmarkUser;
   }

   /**
    *
    * Gets the flag that indicates if the Enter Parameters prompt should not open when the
    * viewsheet opens
    *
    * @return <tt>true</tt> if flag is set to not open prompt <tt>false</tt> otherwise.
    */
   public boolean isDisableParameterSheet() {
      return disableParameterSheet;
   }

   /**
    * Sets the flag that indicates if the Enter Parameters prompt should not open when the
    * viewsheet opens
    *
    * @param disableParameterSheet <tt>true</tt> if flag is set to not open prompt
    *                              <tt>false</tt> otherwise.
    */
   public void setDisableParameterSheet(boolean disableParameterSheet) {
      this.disableParameterSheet = disableParameterSheet;
   }

   /**
    * Gets the viewsheet parameters.
    *
    * @return the viewsheet parameters.
    */
   public Map<String, String[]> getParameters() {
      if(parameters == null) {
         parameters = new HashMap<>();
      }

      return parameters;
   }

   /**
    * Sets the viewsheet parameters.
    *
    * @param parameters the viewsheet parameters.
    */
   public void setParameters(Map<String, String[]> parameters) {
      this.parameters = parameters;
   }

   public double getScale() {
      return scale;
   }

   public void setScale(double scale) {
      this.scale = scale;
   }

   public boolean isManualRefresh() {
      return manualRefresh;
   }

   public void setManualRefresh(boolean manualRefresh) {
      this.manualRefresh = manualRefresh;
   }

   public String getHyperlinkSourceId() {
      return hyperlinkSourceId;
   }

   public void setHyperlinkSourceId(String hyperlinkSourceId) {
      this.hyperlinkSourceId = hyperlinkSourceId;
   }

   public boolean isMeta() {
      return meta;
   }

   public void setMeta(boolean metadata) {
      this.meta = metadata;
   }

   public boolean isNewSheet() {
      return newSheet;
   }

   public void setNewSheet(boolean newSheet) {
      this.newSheet = newSheet;
   }

   public String getLayoutName() {
      return layoutName;
   }

   public void setLayoutName(String layoutName) {
      this.layoutName = layoutName;
   }

   public String getEmbedAssemblyName() {
      return embedAssemblyName;
   }

   public void setEmbedAssemblyName(String embedAssemblyName) {
      this.embedAssemblyName = embedAssemblyName;
   }

   public Dimension getEmbedAssemblySize() {
      return embedAssemblySize;
   }

   public void setEmbedAssemblySize(Dimension embedAssemblySize) {
      this.embedAssemblySize = embedAssemblySize;
   }

   public String getOrgId() {
      return AssetEntry.createAssetEntry(entryId).getOrgID();
   }

   public boolean isEmbed() {
      return embed;
   }

   public void setEmbed(boolean embed) {
      this.embed = embed;
   }

   @Override
   public String toString() {
      return "OpenViewsheetEvent{" +
         "entryId='" + entryId + '\'' +
         ", width=" + width +
         ", height=" + height +
         ", mobile=" + mobile +
         ", drillFrom='" + drillFrom + '\'' +
         ", sync=" + sync +
         ", fullScreenId='" + fullScreenId + '\'' +
         ", runtimeViewsheetId='" + runtimeViewsheetId + '\'' +
         ", embeddedViewsheetId='" + embeddedViewsheetId + '\'' +
         ", viewer=" + viewer +
         ", openAutoSaved=" + openAutoSaved +
         ", confirmed=" + confirmed +
         ", userAgent='" + userAgent + '\'' +
         ", previousUrl='" + previousUrl + '\'' +
         ", bookmarkName='" + bookmarkName + '\'' +
         ", bookmarkUser='" + bookmarkUser + '\'' +
         ", parameters=" + parameters +
         ", scale=" + scale +
         ", manualRefresh=" + manualRefresh +
         ", hyperlinkSourceId=" + hyperlinkSourceId +
         '}';
   }

   private String entryId;
   private int width;
   private int height;
   private boolean mobile;
   private String drillFrom;
   private boolean sync;
   private String fullScreenId;
   private String runtimeViewsheetId;
   private String embeddedViewsheetId;
   private boolean viewer;
   private boolean openAutoSaved;
   private boolean confirmed;
   private String userAgent;
   private String previousUrl;
   private String bookmarkName;
   private String bookmarkUser;
   private boolean meta;
   private boolean newSheet;
   private boolean disableParameterSheet;
   private Map<String, String[]> parameters;
   private double scale;
   private boolean manualRefresh;
   private String hyperlinkSourceId;
   private String layoutName;
   private String embedAssemblyName;
   private Dimension embedAssemblySize;
   private boolean embed;
}
