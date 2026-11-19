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
package inetsoft.web.admin.ai.sessions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One session to log out, as listed by {@code GET /api/wiz/v1/admin/user-sessions}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSessionChangeRequest {
   public String getSessionId() { return sessionId; }
   public void setSessionId(String v) { this.sessionId = v; }

   private String sessionId;
}
