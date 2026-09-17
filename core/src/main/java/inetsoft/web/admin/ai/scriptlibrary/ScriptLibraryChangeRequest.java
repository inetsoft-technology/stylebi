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
package inetsoft.web.admin.ai.scriptlibrary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested change against the Script Library: {@code verb: "create"|"rename"|"update"|
 * "delete"}, {@code name} (the script's current name -- for {@code create}, the name to give the
 * new script), {@code newName} ({@code rename} only), {@code description}/{@code text}
 * ({@code create}/{@code update} only -- {@code update} never touches {@code text}, matching
 * Enterprise Manager's own Script Library settings-page editor, which is rename/description only;
 * editing a script's body is composer-chat's capability), and {@code force}
 * ({@code delete} only).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScriptLibraryChangeRequest {
   public static final String VERB_CREATE = "create";
   public static final String VERB_RENAME = "rename";
   public static final String VERB_UPDATE = "update";
   public static final String VERB_DELETE = "delete";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /** The script's current name -- for {@code create}, the name to give the new script. Exactly
    * as returned by {@code list_script_library}/{@code get_script_library_entry} for every verb
    * other than {@code create}. */
   public String getName() { return name; }
   public void setName(String v) { this.name = v; }

   /** {@code verb: "rename"} only, required. */
   public String getNewName() { return newName; }
   public void setNewName(String v) { this.newName = v; }

   /** {@code verb: "create"} (optional, defaults to blank) or {@code "update"} (optional --
    * omitted leaves the description unchanged; at least one of {@code description} must be
    * present on an {@code update}, matching Viewsheets' own {@code update} verb discipline). */
   public String getDescription() { return description; }
   public void setDescription(String v) { this.description = v; }

   /** {@code verb: "create"} only, optional (defaults to an empty script body, matching
    * {@code ScriptLibraryController.create}'s own default). Never used for {@code update} --
    * present there, it is refused loud rather than silently ignored. */
   public String getText() { return text; }
   public void setText(String v) { this.text = v; }

   /** {@code verb: "delete"} only, default {@code false}. Set {@code true} to delete a script
    * that other assets still depend on -- otherwise the plan is refused loud, naming the
    * dependents. */
   public Boolean getForce() { return force; }
   public void setForce(Boolean v) { this.force = v; }

   private String verb;
   private String name;
   private String newName;
   private String description;
   private String text;
   private Boolean force;
}
