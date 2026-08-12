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

package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One option the request asked for that did not take effect, while the rest of the request applied
 * normally. Distinct from an error: the call succeeded and the chart changed — this names the part
 * of it that did not.
 *
 * <p>The alternative, rejecting the whole request with a 400, is worse rather than stricter: a single
 * misspelled legend position would discard the title and the axis range that were computed correctly
 * in the same call, turning a partial success into a total failure. The fallback behaviour is
 * therefore unchanged — a skipped option stays skipped, a defaulted one stays defaulted — and this
 * only reports it.
 *
 * <p>Not to be confused with {@code CreateViewsheetResult.note}, which is the copy-then-apply fallback
 * channel and nothing else: only that one means the caller's original chart was mutated rather than
 * duplicated. Mixing the two made the caller read a legend typo as a failed copy.
 *
 * <p>Only BUSINESS-level skips belong here — ones caused by what the request asked for (a chart with
 * no Y axis to scale, a value outside the legal set, a formula name that is not recognized). A
 * defensive null check that only trips on an internally inconsistent chart is a bug to be found in the
 * logs, and reporting it would bury the warnings that a user can act on.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplyWarning {
   public ApplyWarning() {
   }

   public ApplyWarning(String option, String reason) {
      this.option = option;
      this.reason = reason;
   }

   /** The request field that did not take effect, e.g. "yAxisScale", "legendPosition", "filter:STATE". */
   public String getOption() {
      return option;
   }

   public void setOption(String option) {
      this.option = option;
   }

   /**
    * A single sentence written for the END USER — it is relayed into the chat reply as it stands.
    * Never an exception message, a stack trace or an internal identifier.
    */
   public String getReason() {
      return reason;
   }

   public void setReason(String reason) {
      this.reason = reason;
   }

   private String option;
   private String reason;
}
