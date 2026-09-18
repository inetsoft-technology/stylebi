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
package inetsoft.web.admin.ai.shapes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested custom-shape change (01-design.md section 2.3/3.2). {@code scope} is a genuine,
 * explicit top-level field -- {@code organization} always means the calling principal's own
 * organization, never an {@code orgId} argument (matching Presentation's own ambient-scope
 * convention). {@code name} is the bare shape filename (no path components -- the shape's own
 * identity), {@code subPath} is an optional nested folder inside the resolved shapes directory, and
 * {@code content} (base64, binary-safe) is required for {@code verb=upload} only.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShapeChangeRequest {
   public static final String VERB_UPLOAD = "upload";
   public static final String VERB_DELETE = "delete";
   public static final String SCOPE_GLOBAL = "global";
   public static final String SCOPE_ORGANIZATION = "organization";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   public String getScope() { return scope; }
   public void setScope(String v) { this.scope = v; }

   /** Bare filename only -- no {@code /} or {@code \\}. */
   public String getName() { return name; }
   public void setName(String v) { this.name = v; }

   /** Optional nested folder inside the resolved shapes directory. */
   public String getSubPath() { return subPath; }
   public void setSubPath(String v) { this.subPath = v; }

   /** {@code verb=upload} only -- base64, the full shape file's bytes. */
   public String getContent() { return content; }
   public void setContent(String v) { this.content = v; }

   private String verb;
   private String scope;
   private String name;
   private String subPath;
   private String content;
}
