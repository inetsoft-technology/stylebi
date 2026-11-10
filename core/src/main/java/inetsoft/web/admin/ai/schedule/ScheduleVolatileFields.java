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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single place that removes values written by the persistence layer or by the task balancer
 * from the raw {@code writeXML} text the schedule change-plan projections feed into the plan hash
 * (bug #80838). Every schedule projection -- task ({@link ScheduleXmlProjection}), folder ({@link
 * ScheduleFolderXmlProjection}) and cycle ({@code AdminScheduleCycleGateway#projectXml}) -- goes
 * through here, so they cannot diverge on what counts as volatile.
 *
 * <p>Two classes of volatile value are handled:
 * <ul>
 *   <li>persistence stamps: the {@code created*}/{@code modified*} elements of an {@code
 *       AssetEntry} (folder children) and the {@code created}/{@code createdBy}/{@code
 *       modified}/{@code modifiedBy} attributes of a {@code CycleInfo}. They change on every save
 *       or delete-and-recreate and are not part of what an operator approved. A consequence: a
 *       change of last editor, or a no-op re-save, no longer changes the hash.</li>
 *   <li>balancer-owned times: {@code hour}/{@code minute}/{@code second} of a time condition that
 *       is bound to a time range. The operator chose the range; {@code TaskBalancer} picks the
 *       slot from the wall clock and rewrites it whenever it runs. Conditions WITHOUT a range keep
 *       their time in the hash because there it is operator-approved state.</li>
 * </ul>
 *
 * <p>Outstanding previews computed before this change carry the old hash and are invalidated, the
 * same caveat every other hash-method change documents.
 */
final class ScheduleVolatileFields {
   private ScheduleVolatileFields() {
   }

   /** Removes the persistence stamp elements of every {@code AssetEntry} in {@code xml}. */
   static String stripEntryStamps(String xml) {
      return ENTRY_STAMP_ELEMENTS.matcher(xml).replaceAll("");
   }

   /** Removes the persistence stamp attributes of every {@code <CycleInfo ...>} tag in {@code xml}. */
   static String stripCycleInfoStamps(String xml) {
      Matcher tags = CYCLE_INFO_TAG.matcher(xml);
      StringBuilder out = new StringBuilder();

      while(tags.find()) {
         String tag = CYCLE_STAMP_ATTRIBUTES.matcher(tags.group()).replaceAll("");
         tags.appendReplacement(out, Matcher.quoteReplacement(tag));
      }

      tags.appendTail(out);
      return out.toString();
   }

   /**
    * Zeroes hour/minute/second on every time condition that carries a {@code <timeRange>} child.
    * The {@code <timeRange>} element itself is kept: the range binding is operator-owned state.
    */
   static String normalizeBalancerTimes(String xml) {
      Matcher conditions = CONDITION_BLOCK.matcher(xml);
      StringBuilder out = new StringBuilder();

      while(conditions.find()) {
         String block = conditions.group();

         if(block.contains("<timeRange")) {
            int tagEnd = block.indexOf('>') + 1;
            String tag = BALANCER_TIME_ATTRIBUTES.matcher(block.substring(0, tagEnd))
               .replaceAll("$1=\"0\"");
            block = tag + block.substring(tagEnd);
         }

         conditions.appendReplacement(out, Matcher.quoteReplacement(block));
      }

      conditions.appendTail(out);
      return out.toString();
   }

   private static final Pattern ENTRY_STAMP_ELEMENTS = Pattern.compile(
      "<(createdUsername|createdDate|modifiedUsername|modifiedDate)>.*?</\\1>\\s*",
      Pattern.DOTALL);
   private static final Pattern CYCLE_INFO_TAG = Pattern.compile("<CycleInfo\\b[^>]*>");
   private static final Pattern CYCLE_STAMP_ATTRIBUTES = Pattern.compile(
      "\\s+(?:createdBy|created|modifiedBy|modified)=\"[^\"]*\"");
   private static final Pattern CONDITION_BLOCK = Pattern.compile(
      "<Condition\\b.*?</Condition>", Pattern.DOTALL);
   private static final Pattern BALANCER_TIME_ATTRIBUTES = Pattern.compile(
      "(\\s(?:hour|minute|second))=\"[^\"]*\"");
}
