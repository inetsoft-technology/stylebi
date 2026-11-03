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

package inetsoft.web.wiz.service;

/**
 * Signals that something was not finished yet: a chart graph (VGraphPair not ready, the default),
 * or the data of a table whose query is still running ({@link Subject#TABLE}). The subject only
 * selects the wording of the 503 answer; the retry contract is the same.
 */
public class RenderNotReadyException extends RuntimeException {
   /** What was not ready. */
   public enum Subject {
      CHART,
      TABLE
   }

   public RenderNotReadyException(int retryAfter) {
      this(retryAfter, Subject.CHART);
   }

   public RenderNotReadyException(int retryAfter, Subject subject) {
      super((subject == Subject.TABLE ? "Table data not ready" : "Chart image not ready") +
         "; retry after " + retryAfter + "s");
      this.retryAfter = retryAfter;
      this.subject = subject;
   }

   public int getRetryAfter() { return retryAfter; }
   public Subject getSubject() { return subject; }

   private final int retryAfter;
   private final Subject subject;
}
