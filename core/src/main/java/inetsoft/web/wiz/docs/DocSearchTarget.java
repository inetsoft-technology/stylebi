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
package inetsoft.web.wiz.docs;

import inetsoft.sree.SreeEnv;

import java.util.Optional;

/**
 * Where a documentation search goes, and how it is allowed to get there.
 *
 * <p>Resolution order, first match wins:</p>
 * <ol>
 *   <li>{@value #DOCS_URL_PROPERTY} if it is set. A blank value switches documentation search
 *       off entirely, which is how an air-gapped installation opts out.</li>
 *   <li>{@code chat.app.internal.url}, the server-to-server address of the deployment's own
 *       assistant. Unlike a plugin on a developer machine, this server can reach it, and
 *       deployments in proxy mode have no other URL.</li>
 *   <li>{@code chat.app.server.url}.</li>
 *   <li>{@link #HOSTED_DOCS_URL}, the InetSoft-hosted documentation search.</li>
 * </ol>
 *
 * <p>The first match is final. A deployment whose own assistant is down does not fall through
 * to the hosted default: that would send its users' questions to a third party they never
 * configured.</p>
 */
public record DocSearchTarget(String baseUrl, Source source) {
   public enum Source {
      DOCS_URL(false),
      INTERNAL_URL(true),
      SERVER_URL(true),
      HOSTED_DEFAULT(false);

      Source(boolean ownAssistant) {
         this.ownAssistant = ownAssistant;
      }

      /**
       * True for the deployment's own assistant. Only that one gets the caller's bearer token (a
       * self-hosted assistant that keeps doc search behind auth needs it) and the relaxed TLS
       * trust the rest of the assistant integration uses. The StyleBI bearer token must never
       * reach {@link #DOCS_URL} or the hosted default: those are not the operator's assistant.
       */
      public boolean forwardsCallerToken() {
         return ownAssistant;
      }

      public boolean usesAssistantTls() {
         return ownAssistant;
      }

      private final boolean ownAssistant;
   }

   /** Reads the configured properties. Empty means documentation search is off or unconfigured. */
   public static Optional<DocSearchTarget> resolve() {
      return resolve(SreeEnv.getProperty(DOCS_URL_PROPERTY),
                     SreeEnv.getProperty("chat.app.internal.url"),
                     SreeEnv.getProperty("chat.app.server.url"),
                     HOSTED_DOCS_URL);
   }

   /** The pure rule; {@code docsUrl} is null when the property is unset, blank when disabled. */
   static Optional<DocSearchTarget> resolve(
      String docsUrl, String internalUrl, String serverUrl, String hostedUrl)
   {
      if(docsUrl != null) {
         return isBlank(docsUrl)
            ? Optional.empty()
            : Optional.of(new DocSearchTarget(docsUrl.trim(), Source.DOCS_URL));
      }

      if(!isBlank(internalUrl)) {
         return Optional.of(new DocSearchTarget(internalUrl.trim(), Source.INTERNAL_URL));
      }

      if(!isBlank(serverUrl)) {
         return Optional.of(new DocSearchTarget(serverUrl.trim(), Source.SERVER_URL));
      }

      if(!isBlank(hostedUrl)) {
         return Optional.of(new DocSearchTarget(hostedUrl.trim(), Source.HOSTED_DEFAULT));
      }

      return Optional.empty();
   }

   private static boolean isBlank(String value) {
      return value == null || value.trim().isEmpty();
   }

   public static final String DOCS_URL_PROPERTY = "assistant.docs.url";

   /**
    * The InetSoft-hosted documentation search (the assistant's public {@code /api/doc-search}).
    * An installation with no assistant configured uses it, so the AI plugins get documentation
    * search with no setup. It is reached over verified TLS and never receives the caller's
    * bearer token. An air-gapped installation switches it off by setting
    * {@value #DOCS_URL_PROPERTY} to blank. If the host is down the search fails and the plugins
    * carry on without it.
    */
   static final String HOSTED_DOCS_URL = "https://inetsoft.com:3001";
}
