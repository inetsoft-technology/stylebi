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
package inetsoft.web.wiz.pairing;

import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;

import java.util.Map;
import java.util.Set;

/**
 * Viewer mode: what a session paired to a portal Viewer dashboard may do (Redmine #80547).
 *
 * <p>A Viewer runtime is a runtime-mode copy of a dashboard the user is <i>looking at</i>; the
 * agent that pairs to it may read and export what the user sees and nothing else. The mode is
 * never carried by a client: it is the session's stamp ({@link JoinSession.ConnectionMode#VIEWER},
 * set by {@link SheetJoinService#join} from {@link RuntimeViewsheet#isViewer()}) <b>or</b> the
 * live runtime's own {@code isViewer()}, whichever says viewer, so even a session stamped
 * {@code PAIRED} because the runtime was not resolvable at join is gated as soon as it is.
 *
 * <p>Deny by default. {@link #ALLOWED} is the complete list of agent routes a viewer session may
 * call; everything else under {@code /api/wiz/v1/agent/} that carries a session token is refused
 * by {@link ViewerModeInterceptor}, so a route someone adds later is refused until it is added
 * here on purpose. The openers whose token travels in the request body go through
 * {@link #requireNotViewer} instead.
 *
 * <p>Two hand-synced lists exist (this one and the plugin's tool classification); this one is the
 * gate, the plugin's only gives a clearer message first.
 *
 * <p>Known limit (phase 1): routes that carry no session token at all (the script library, table
 * styles, image upload, fonts) are not reachable by this gate; the plugin refuses them for a
 * viewer session, the server does not.
 */
public final class ViewerModePolicy {
   private ViewerModePolicy() {}

   /** A route as Spring matched it: HTTP method plus the best matching pattern. */
   public record Route(String method, String pattern) {}

   private static final String VS = "/api/wiz/v1/agent/viewsheet/{sessionToken}";

   /**
    * The routes a viewer session may call: the class-R reads (what the viewer shows), export, the
    * keep-alive that keeps the viewer runtime from idling out while an agent is attached, and
    * detach so an agent can end its own session. The design-detail reads (bindings, scripts,
    * conditions, ...) are deliberately NOT here: the agent principal is approved for every
    * permission server-side, so allowing them would show a view-only user script text and binding
    * detail the viewer never does. Moving a route in is a one-line change that needs product
    * sign-off.
    */
   public static final Set<Route> ALLOWED = Set.of(
      new Route("GET", VS + "/model"),
      new Route("GET", VS + "/image"),
      new Route("GET", VS + "/export"),
      new Route("GET", VS + "/properties/list"),
      new Route("GET", VS + "/properties"),
      new Route("GET", VS + "/vs-properties/list"),
      new Route("GET", VS + "/vs-properties"),
      new Route("GET", VS + "/selection/state"),
      new Route("GET", VS + "/input/value"),
      new Route("GET", VS + "/output/value"),
      new Route("GET", VS + "/bookmarks"),
      new Route("GET", VS + "/parameters"),
      new Route("POST", VS + "/detach"),
      // the plugin's liveness probe for every viewsheet session; read-only (runtime id, type, scope)
      new Route("GET", "/api/wiz/v1/agent/script/{sessionToken}/session"),
      new Route("POST", "/api/wiz/v1/agent/sheet/{sessionToken}/keep-alive"));

   /**
    * Allow-listed routes that nonetheless have a parameter a viewer session may not use: the
    * {@code raw} flag of the two property getters returns the unfiltered dialog model, which
    * carries the assembly's script text, click scripts and data bindings -- the design detail the
    * class-R allow-list promises a view-only user does not get. Route pattern to the parameter
    * names that must not be true. A new allow-listed route that takes a flag like this must be
    * declared here ({@code ViewerModePolicyClassificationTest} fails otherwise).
    */
   public static final Map<Route, Set<String>> RESTRICTED_FLAGS = Map.of(
      new Route("GET", VS + "/properties"), Set.of("raw"),
      new Route("GET", VS + "/vs-properties"), Set.of("raw"));

   /** What a viewer session is told when it asks for a raw dialog model. */
   public static final String RAW_REFUSAL =
      "This session is paired to a dashboard open in the portal Viewer. Raw dialog models (raw=true) " +
      "include script text and data bindings and are not available in viewer mode; the " +
      "alias-keyed property values are. Nothing was changed. To see the full model, open the " +
      "dashboard in the Composer and pair from there.";

   /**
    * Whether {@code values} (every value the request supplied for one parameter) would bind to
    * boolean {@code true} the way Spring's {@code StringToBooleanConverter} does: true, on, yes, 1
    * in any case, with surrounding blanks trimmed. A comma-joined value is judged per part, so no
    * spelling that any binding path could turn into true gets through.
    */
   public static boolean bindsToTrue(String[] values) {
      if(values == null) {
         return false;
      }

      for(String value : values) {
         if(value == null) {
            continue;
         }

         for(String part : value.split(",", -1)) {
            String v = part.trim().toLowerCase(java.util.Locale.ROOT);

            if(v.equals("true") || v.equals("on") || v.equals("yes") || v.equals("1")) {
               return true;
            }
         }
      }

      return false;
   }

   /** Prefix every gated route lives under. */
   public static final String AGENT_PREFIX = "/api/wiz/v1/agent/";

   /** The URI template variable that carries the session token on every gated route. */
   public static final String TOKEN_VARIABLE = "sessionToken";

   /** {@code errorCode} of the refusal (the {@link PairingException.Kind} name). */
   public static final String ERROR_CODE = PairingException.Kind.VIEWER_MODE.name();

   /** The refusal text an agent reads (W3). Names the mode, what is allowed and the remedy. */
   public static final String REFUSAL =
      "This session is paired to a dashboard open in the portal Viewer. Claude can read and " +
      "export a viewer dashboard but cannot change it or open other sheets, so this call was " +
      "refused and nothing changed. To edit, open the dashboard in the Composer and pair from " +
      "there.";

   /** What a call on a viewer session whose page has closed or reloaded is told (W4). */
   public static final String VIEWER_CLOSED_TEXT =
      "This session was paired to a dashboard page that has since been closed or reloaded, so " +
      "it was ended. Ask the user to click Connect to Claude in the dashboard again, then run " +
      "connect_sheet with the new code.";

   /**
    * Whether a viewer session may call this route. A {@code HEAD} is judged as the {@code GET}
    * Spring serves it with; a null method or pattern is never allowed (deny by default).
    */
   public static boolean isAllowed(String method, String pattern) {
      if(method == null || pattern == null) {
         return false;
      }

      String m = "HEAD".equalsIgnoreCase(method) ? "GET" : method.toUpperCase();
      return ALLOWED.contains(new Route(m, pattern));
   }

   /**
    * Whether {@code session} is a viewer session: stamped {@code VIEWER}, or its runtime is
    * (right now) a portal Viewer runtime. The live read is what stops a client or a stale stamp
    * from choosing its own mode; a runtime this node cannot resolve contributes nothing, so the
    * stamp alone decides.
    */
   public static boolean isViewer(JoinSession session, SheetRuntimeAccess runtimeAccess) {
      if(session == null) {
         return false;
      }

      if(session.connectionMode() == JoinSession.ConnectionMode.VIEWER) {
         return true;
      }

      if(session.sheetType() != SheetType.VIEWSHEET || runtimeAccess == null) {
         return false;
      }

      return isViewerRuntime(
         runtimeAccess.getRuntimeSheetDirect(session.sheetType(), session.runtimeId()));
   }

   /** Whether {@code runtime} is a portal Viewer runtime ({@code RuntimeViewsheet#isViewer}). */
   public static boolean isViewerRuntime(RuntimeSheet runtime) {
      return runtime instanceof RuntimeViewsheet rvs && rvs.isViewer();
   }

   /**
    * Refuses {@code session} with {@link PairingException.Kind#VIEWER_MODE} when it is a viewer
    * session. For the code paths no HTTP interceptor can see (the openers, whose session token is
    * in the request body).
    */
   public static void requireNotViewer(JoinSession session, SheetRuntimeAccess runtimeAccess)
      throws PairingException
   {
      if(isViewer(session, runtimeAccess)) {
         throw refusal();
      }
   }

   /** The refusal exception: kind {@code VIEWER_MODE} (HTTP 403), text {@link #REFUSAL}. */
   public static PairingException refusal() {
      return new PairingException(PairingException.Kind.VIEWER_MODE, REFUSAL);
   }
}
