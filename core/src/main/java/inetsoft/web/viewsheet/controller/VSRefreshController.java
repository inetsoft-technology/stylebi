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
package inetsoft.web.viewsheet.controller;

import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.RefreshVSAssemblyEvent;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class VSRefreshController {
   /**
    * Creates a new instance of <tt>VSRefreshController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime vs associated with the websocket
    */
   @Autowired
   public VSRefreshController(RuntimeViewsheetRef runtimeViewsheetRef,
                              VSRefreshServiceProxy vsRefreshServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsRefreshServiceProxy = vsRefreshServiceProxy;
   }

   /**
    * Refresh a viewsheet
    */
   // Same rationale as OpenViewsheetController.openViewsheet: refreshing a viewsheet re-runs
   // its runtime queries, which the product's query.runtime.timeout=0 default already treats
   // as legitimately unbounded.
   @LoadingMask(value = true, watchdogTimeout = 0)
   @MessageMapping("/vs/refresh")
   public void refreshViewsheet(@Payload VSRefreshEvent event, Principal principal,
                                CommandDispatcher commandDispatcher,
                                @LinkUri String linkUri) throws Exception
   {
      refreshViewsheet(this.runtimeViewsheetRef.getRuntimeId(), event, principal,
                       commandDispatcher, linkUri);
   }

   /**
    * Refresh a viewsheet using an explicit runtime id, instead of re-deriving one from
    * {@link RuntimeViewsheetRef} -- a WebSocket-session-scoped bean that is only ever populated
    * from a native STOMP header sent by a real browser session. A caller with no live STOMP
    * session (e.g. the wiz agent's plain-HTTP-driven mutations, which invoke this as an ordinary
    * Java method call) gets null back from {@code runtimeViewsheetRef.getRuntimeId()}, which used
    * to reach {@code RuntimeSheetCache.getAffinityKey} unguarded and throw
    * {@code NullPointerException("Ouch! Argument cannot be null: key")} (bug #76666). Mirrors
    * {@link #refreshVsAssembly} / {@link #refreshVsAssemblyView} below, which already take their
    * runtime id explicitly from the event payload rather than this session-scoped bean -- any
    * session-less caller that already knows its own runtime id (e.g. a
    * {@code @ClusterProxyKey}-routed service method) should call this overload directly instead
    * of the STOMP-mapped one above.
    */
   public void refreshViewsheet(String runtimeId, VSRefreshEvent event, Principal principal,
                                CommandDispatcher commandDispatcher, String linkUri)
      throws Exception
   {
      vsRefreshServiceProxy.refreshViewsheetAsync(runtimeId, event, principal,
                                                  commandDispatcher, linkUri);
   }

   @MessageMapping("/vs/refresh/assembly")
   public void refreshVsAssembly(RefreshVSAssemblyEvent event, CommandDispatcher dispatcher,
                                 @LinkUri String linkUri, Principal principal)
      throws Exception
   {
      vsRefreshServiceProxy.refreshVsAssembly(event.getVsRuntimeId(), event, dispatcher, linkUri, principal);
   }

   @MessageMapping("/vs/refresh/assembly/view")
   public void refreshVsAssemblyView(RefreshVSAssemblyEvent event,
                                     CommandDispatcher dispatcher,
                                     @LinkUri String linkUri,
                                     Principal principal)
      throws Exception
   {
      String runtimeId = event.getVsRuntimeId();
      vsRefreshServiceProxy.refreshVsAssemblyView(
         runtimeId, event.getAssemblyName(), dispatcher, linkUri, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSRefreshServiceProxy vsRefreshServiceProxy;
}
