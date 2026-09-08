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
package inetsoft.web.admin.ai.schedule;

import inetsoft.sree.schedule.ScheduleTask;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

/**
 * A total, hashable projection of a schedule task, used both as the plan-hash input (spec §5) and
 * as the apply-time verification signal (spec §6).
 *
 * <p>{@link ScheduleTask#writeXML} is used rather than the enterprise API DTO because the DTO
 * carries no conditions or actions (Track C.0 finding) -- projecting off it let two materially
 * different tasks hash identically. {@code writeXML} is total, but not stable: {@code
 * inetsoft.sree.schedule.ScheduleManager#setScheduleTask} stamps {@code lastModified} on every
 * save, so a naive equality check would report every successful apply as a verification failure
 * and roll it straight back. {@link #EXCLUDED_ATTRIBUTES} strips exactly the attributes confirmed
 * (or, for {@code path}/{@code editable}/{@code removable}, suspected and pending a regression
 * test -- see {@code 01-spec.md} §5) to be recomputed by the persistence layer rather than part of
 * what an operator approved.
 *
 * <p>This is deliberately NOT a general-purpose XML diff: stripping is attribute-name-based, on
 * the raw {@code writeXML} text, because the attribute names this excludes are specific to the
 * {@code <Task>} element's own scalar fields and do not recur as attribute names anywhere in a
 * condition or action sub-element.
 */
public final class ScheduleXmlProjection {
   private ScheduleXmlProjection() {
   }

   /**
    * @return the normalized projection, or {@code null} if {@code task} is {@code null} (the unit
    *         does not exist).
    */
   public static String project(ScheduleTask task) {
      if(task == null) {
         return null;
      }

      StringWriter sw = new StringWriter();

      try(PrintWriter pw = new PrintWriter(sw)) {
         task.writeXML(pw);
      }

      return normalize(sw.toString());
   }

   /** Strips every {@link #EXCLUDED_ATTRIBUTES} entry from a raw {@code writeXML} string. */
   static String normalize(String xml) {
      String result = xml;

      for(Pattern p : EXCLUSION_PATTERNS) {
         result = p.matcher(result).replaceAll("");
      }

      return result;
   }

   /**
    * Attributes recomputed by the persistence layer, and therefore excluded from both the plan
    * hash and the apply-time verification -- see the class javadoc.
    *
    * <p>{@code lastModified}: CONFIRMED unstable, by reading {@code ScheduleManager
    * #setScheduleTask}, which calls {@code task.setLastModified(System.currentTimeMillis())}
    * unconditionally on every save.
    *
    * <p>{@code path}, {@code editable}, {@code removable}: NOT confirmed either way -- included
    * here conservatively pending the per-attribute regression test spec §5 requires before
    * shipping. Do not remove an entry from this list without a passing test demonstrating it is
    * stable; do not assume a fourth attribute is safe to include in the hash without the same
    * test.
    */
   static final String[] EXCLUDED_ATTRIBUTES = { "lastModified", "path", "editable", "removable" };

   private static final Pattern[] EXCLUSION_PATTERNS = buildPatterns();

   private static Pattern[] buildPatterns() {
      Pattern[] patterns = new Pattern[EXCLUDED_ATTRIBUTES.length];

      for(int i = 0; i < EXCLUDED_ATTRIBUTES.length; i++) {
         patterns[i] = Pattern.compile("\\s+" + Pattern.quote(EXCLUDED_ATTRIBUTES[i]) + "=\"[^\"]*\"");
      }

      return patterns;
   }
}
