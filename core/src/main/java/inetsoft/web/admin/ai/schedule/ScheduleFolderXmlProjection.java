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

import inetsoft.uql.asset.internal.AssetFolder;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * A total, hashable projection of a schedule-task folder, used both as the plan-hash input and as
 * the apply-time verification signal (design §2) -- the folder-shaped analog of {@link
 * ScheduleXmlProjection}, replicated rather than shared per that class's own precedent.
 *
 * <p>{@link AssetFolder#writeXML} already serializes exactly what identifies a folder's own
 * content: its {@code owner} plus each DIRECT child's own {@code AssetEntry.writeXML} (path/type
 * only -- {@code AssetEntry.writeXML} does not recurse into a child folder's own further
 * descendants), so this is reused verbatim rather than inventing a new projection format, the same
 * "no new projection format to invent" reasoning {@code ScheduleXmlProjection}'s own javadoc
 * documents. Unlike a schedule task, a folder's own {@code AssetFolder} object carries no {@code
 * path} field of its own (it is keyed by the caller's {@code AssetEntry} identifier, not embedded
 * in the persisted object) -- {@code path} is prefixed here so two folders with identical
 * owner/children at different paths still hash differently.
 *
 * <p>Deliberately not a full recursive projection: a rename/move's PROPOSED value is built from the
 * unmutated source folder object with only its own top-level {@code path} prefix changed, not by
 * recomputing every descendant's rewritten path (which {@code ScheduleTaskFolderService
 * #changeFolder}'s own recursive walk would do at apply time) -- this is intentionally narrower,
 * enough that two materially different requests hash differently, not a general-purpose diff of
 * the whole subtree. Same bar {@code ScheduleXmlProjection} itself documents for the task area.
 */
public final class ScheduleFolderXmlProjection {
   private ScheduleFolderXmlProjection() {
   }

   /**
    * @return the projection of {@code folder} as if it were addressed at {@code path}, or {@code
    *         null} if {@code folder} is {@code null} (the folder does not exist).
    */
   public static String project(String path, AssetFolder folder) {
      if(folder == null) {
         return null;
      }

      StringWriter sw = new StringWriter();

      try(PrintWriter pw = new PrintWriter(sw)) {
         pw.print("<path><![CDATA[");
         pw.print(path);
         pw.print("]]></path>");
         folder.writeXML(pw);
      }

      return sw.toString();
   }
}
