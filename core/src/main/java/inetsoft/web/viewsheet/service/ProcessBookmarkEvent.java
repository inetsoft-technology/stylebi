/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.springframework.context.ApplicationEvent;

import java.security.Principal;

public class ProcessBookmarkEvent extends ApplicationEvent {
   private final String id;
   private final RuntimeViewsheet rvs;
   private final String linkUri;
   private final Principal principal;
   private final String bookmarkName;
   private final IdentityID bookmarkUser;
   private final OpenViewsheetEvent event;
   private final CommandDispatcher dispatcher;

   public ProcessBookmarkEvent(Object source, String id, RuntimeViewsheet rvs, String linkUri,
                               Principal principal, String bookmarkName, IdentityID bookmarkUser,
                               OpenViewsheetEvent event, CommandDispatcher dispatcher)
   {
      super(source);
      this.id = id;
      this.rvs = rvs;
      this.linkUri = linkUri;
      this.principal = principal;
      this.bookmarkName = bookmarkName;
      this.bookmarkUser = bookmarkUser;
      this.event = event;
      this.dispatcher = dispatcher;
   }

   public String getId() {
      return id;
   }

   public RuntimeViewsheet getRvs() {
      return rvs;
   }

   public String getLinkUri() {
      return linkUri;
   }

   public Principal getPrincipal() {
      return principal;
   }

   public String getBookmarkName() {
      return bookmarkName;
   }

   public IdentityID getBookmarkUser() {
      return bookmarkUser;
   }

   public OpenViewsheetEvent getEvent() {
      return event;
   }

   public CommandDispatcher getDispatcher() {
      return dispatcher;
   }
}
