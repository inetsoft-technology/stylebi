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
package inetsoft.web.admin.ai.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested stored-asset change (01-design.md section 6.3). {@code unitType} is a
 * discriminator FIRST -- valid {@code verb}s depend on it: {@code folder} accepts {@code create}/
 * {@code rename}/{@code delete}; {@code file} accepts {@code write}/{@code rename}/{@code delete}.
 *
 * <p>{@code content} is required for {@code write} and is plain UTF-8 text (01-design.md Flagged
 * Decision 6) -- a binary {@code sourcePath}/multipart upload path is NOT implemented in this cut;
 * see 04-build.md for why and what a follow-up would need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredAssetChangeRequest {
   public static final String UNIT_FILE = "file";
   public static final String UNIT_FOLDER = "folder";
   public static final String VERB_CREATE = "create";
   public static final String VERB_WRITE = "write";
   public static final String VERB_RENAME = "rename";
   public static final String VERB_DELETE = "delete";

   public String getUnitType() { return unitType; }
   public void setUnitType(String v) { this.unitType = v; }

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   public String getPath() { return path; }
   public void setPath(String v) { this.path = v; }

   /** {@code rename} only -- a bare sibling name, not a full path. */
   public String getNewName() { return newName; }
   public void setNewName(String v) { this.newName = v; }

   /** {@code write} only -- plain UTF-8 text. */
   public String getContent() { return content; }
   public void setContent(String v) { this.content = v; }

   /** Reserved for a future {@code force}-past-cap delete; unused in this cut. */
   public Boolean getForce() { return force; }
   public void setForce(Boolean v) { this.force = v; }

   private String unitType;
   private String verb;
   private String path;
   private String newName;
   private String content;
   private Boolean force;
}
