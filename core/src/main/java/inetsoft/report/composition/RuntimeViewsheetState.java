/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.composition;

import java.util.*;

class RuntimeViewsheetState extends RuntimeSheetState {
   public String getBindingId() {
      return bindingId;
   }

   public void setBindingId(String bindingId) {
      this.bindingId = bindingId;
   }

   public String getVs() {
      return vs;
   }

   public void setVs(String vs) {
      this.vs = vs;
   }

   public String getOriginalVs() {
      return originalVs;
   }

   public void setOriginalVs(String originalVs) {
      this.originalVs = originalVs;
   }

   public String getVars() {
      return vars;
   }

   public void setVars(String vars) {
      this.vars = vars;
   }

   public boolean isViewer() {
      return viewer;
   }

   public void setViewer(boolean viewer) {
      this.viewer = viewer;
   }

   public boolean isPreview() {
      return preview;
   }

   public void setPreview(boolean preview) {
      this.preview = preview;
   }

   public boolean isNeedsRefresh() {
      return needsRefresh;
   }

   public void setNeedsRefresh(boolean needsRefresh) {
      this.needsRefresh = needsRefresh;
   }

   public int getMode() {
      return mode;
   }

   public void setMode(int mode) {
      this.mode = mode;
   }

   public String getExecSessionId() {
      return execSessionId;
   }

   public void setExecSessionId(String execSessionId) {
      this.execSessionId = execSessionId;
   }

   public long getTouchts() {
      return touchts;
   }

   public void setTouchts(long touchts) {
      this.touchts = touchts;
   }

   public Map<String, String> getTipviews() {
      return tipviews;
   }

   public void setTipviews(Map<String, String> tipviews) {
      this.tipviews = tipviews == null ? null : new HashMap<>(tipviews);
   }

   public Set<String> getPopcomponents() {
      return popcomponents;
   }

   public void setPopcomponents(Set<String> popcomponents) {
      this.popcomponents = popcomponents == null ? null : new HashSet<>(popcomponents);
   }

   public Map<String, String> getBookmarksMap() {
      return bookmarksMap;
   }

   public void setBookmarksMap(Map<String, String> bookmarksMap) {
      this.bookmarksMap = bookmarksMap;
   }

   public String getIbookmark() {
      return ibookmark;
   }

   public void setIbookmark(String ibookmark) {
      this.ibookmark = ibookmark;
   }

   public String getOpenedBookmark() {
      return openedBookmark;
   }

   public void setOpenedBookmark(String openedBookmark) {
      this.openedBookmark = openedBookmark;
   }

   public long getLastReset() {
      return lastReset;
   }

   public void setLastReset(long lastReset) {
      this.lastReset = lastReset;
   }

   public long getDateCreated() {
      return dateCreated;
   }

   public void setDateCreated(long dateCreated) {
      this.dateCreated = dateCreated;
   }

   public String getRvsLayout() {
      return rvsLayout;
   }

   public void setRvsLayout(String rvsLayout) {
      this.rvsLayout = rvsLayout;
   }

   public List<String> getLayoutPoints() {
      return layoutPoints;
   }

   public void setLayoutPoints(List<String> layoutPoints) {
      this.layoutPoints = layoutPoints;
   }

   public int getLayoutPoint() {
      return layoutPoint;
   }

   public void setLayoutPoint(int layoutPoint) {
      this.layoutPoint = layoutPoint;
   }

   public boolean isWizardViewsheet() {
      return wizardViewsheet;
   }

   public void setWizardViewsheet(boolean wizardViewsheet) {
      this.wizardViewsheet = wizardViewsheet;
   }

   public String getEmbedAssemblyInfo() {
      return embedAssemblyInfo;
   }

   public void setEmbedAssemblyInfo(String embedAssemblyInfo) {
      this.embedAssemblyInfo = embedAssemblyInfo;
   }

   @Override
   public boolean equals(Object o) {
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      RuntimeViewsheetState that = (RuntimeViewsheetState) o;
      return viewer == that.viewer && preview == that.preview &&
         needsRefresh == that.needsRefresh && mode == that.mode && touchts == that.touchts &&
         lastReset == that.lastReset && dateCreated == that.dateCreated &&
         layoutPoint == that.layoutPoint && wizardViewsheet == that.wizardViewsheet &&
         Objects.equals(bindingId, that.bindingId) && Objects.equals(vs, that.vs) &&
         Objects.equals(originalVs, that.originalVs) && Objects.equals(vars, that.vars) &&
         Objects.equals(execSessionId, that.execSessionId) &&
         Objects.equals(tipviews, that.tipviews) &&
         Objects.equals(popcomponents, that.popcomponents) &&
         Objects.equals(bookmarksMap, that.bookmarksMap) &&
         Objects.equals(ibookmark, that.ibookmark) &&
         Objects.equals(openedBookmark, that.openedBookmark) &&
         Objects.equals(rvsLayout, that.rvsLayout) &&
         Objects.equals(layoutPoints, that.layoutPoints) &&
         Objects.equals(embedAssemblyInfo, that.embedAssemblyInfo);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         super.hashCode(), bindingId, vs, originalVs, vars, viewer, preview, needsRefresh, mode,
         execSessionId, touchts, tipviews, popcomponents, bookmarksMap, ibookmark, openedBookmark,
         lastReset, dateCreated, rvsLayout, layoutPoints, layoutPoint,  wizardViewsheet,
         embedAssemblyInfo);
   }

   @Override
   public String toString() {
      return "RuntimeViewsheetState{" +
         "bindingId='" + bindingId + '\'' +
         ", vs='" + vs + '\'' +
         ", originalVs='" + originalVs + '\'' +
         ", vars='" + vars + '\'' +
         ", viewer=" + viewer +
         ", preview=" + preview +
         ", needsRefresh=" + needsRefresh +
         ", mode=" + mode +
         ", execSessionId='" + execSessionId + '\'' +
         ", touchts=" + touchts +
         ", tipviews='" + tipviews + '\'' +
         ", popcomponents='" + popcomponents + '\'' +
         ", bookmarksMap='" + bookmarksMap + '\'' +
         ", ibookmark='" + ibookmark + '\'' +
         ", openedBookmark='" + openedBookmark + '\'' +
         ", lastReset=" + lastReset +
         ", dateCreated=" + dateCreated +
         ", rvsLayout='" + rvsLayout + '\'' +
         ", layoutPoints='" + layoutPoints + '\'' +
         ", layoutPoint=" + layoutPoint +
         ", wizardViewsheet=" + wizardViewsheet +
         ", embedAssemblyInfo='" + embedAssemblyInfo + '\'' +
         '}';
   }

   private String bindingId;
   private String vs;
   private String originalVs;
   private String vars;
   private boolean viewer;
   private boolean preview;
   private boolean needsRefresh;
   private int mode;
   private String execSessionId;
   private long touchts;
   private Map<String, String> tipviews;
   private Set<String> popcomponents;
   private Map<String, String> bookmarksMap;
   private String ibookmark;
   private String openedBookmark;
   private long lastReset;
   private long dateCreated;
   private String rvsLayout;
   private List<String> layoutPoints;
   private int layoutPoint;
   private boolean wizardViewsheet;
   private String embedAssemblyInfo;
}
