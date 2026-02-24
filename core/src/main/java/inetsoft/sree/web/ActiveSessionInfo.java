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
package inetsoft.sree.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Carries information about an active user session, used when a site administrator needs to
 * terminate an existing session in order to log in when the session limit is reached.
 */
public class ActiveSessionInfo implements Serializable {
   @JsonCreator
   public ActiveSessionInfo(
      @JsonProperty("sessionId") String sessionId,
      @JsonProperty("username") String username,
      @JsonProperty("loginTime") long loginTime)
   {
      this.sessionId = sessionId;
      this.username = username;
      this.loginTime = loginTime;
   }

   /**
    * Gets the session ID that uniquely identifies this session. This is the value of
    * {@link inetsoft.uql.XPrincipal#getSessionID()}, not the HTTP session ID.
    */
   public String getSessionId() {
      return sessionId;
   }

   /**
    * Gets the display name of the user associated with this session.
    */
   public String getUsername() {
      return username;
   }

   /**
    * Gets the time at which the session was created, in milliseconds since the epoch.
    */
   public long getLoginTime() {
      return loginTime;
   }

   private final String sessionId;
   private final String username;
   private final long loginTime;
}
