/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.ai.mv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One requested MV change: {@code create} (candidates from a prior {@code analyze_mv}, folding the
 * server's own create + set-cycle steps into one atomic apply), {@code set_cycle} (retarget the
 * data cycle of one or more candidates from the same analysis, independently of {@code create}), or
 * {@code delete} (dispose one or more already-created MVs by name -- also the rollback primitive
 * {@code create} itself relies on).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MvChangeRequest {
   public static final String VERB_CREATE = "create";
   public static final String VERB_SET_CYCLE = "set_cycle";
   public static final String VERB_DELETE = "delete";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /** Required for {@code create}/{@code set_cycle}; unused for {@code delete}. */
   public String getAnalysisId() { return analysisId; }
   public void setAnalysisId(String v) { this.analysisId = v; }

   /** The materialized-view names this entry applies to -- candidate names from {@code
    * analysisId} for {@code create}/{@code set_cycle}, already-created MV names for {@code
    * delete}. */
   public List<String> getMvNames() { return mvNames; }
   public void setMvNames(List<String> v) { this.mvNames = v; }

   /** Data cycle name for {@code create}/{@code set_cycle}; {@code null}/omitted means no cycle. */
   public String getCycle() { return cycle; }
   public void setCycle(String v) { this.cycle = v; }

   /** {@code create} only; defaults to {@code true} (register the MV shell now, materialize data
    * later) -- a safer default than the EM UI's own {@code false}, so a caller that doesn't think
    * to set this explicitly gets the fast, no-background-job path first. */
   public Boolean getNoData() { return noData; }
   public void setNoData(Boolean v) { this.noData = v; }

   /** {@code create} only; defaults to {@code true}. */
   public Boolean getRunInBackground() { return runInBackground; }
   public void setRunInBackground(Boolean v) { this.runInBackground = v; }

   private String verb;
   private String analysisId;
   private List<String> mvNames;
   private String cycle;
   private Boolean noData;
   private Boolean runInBackground;
}
