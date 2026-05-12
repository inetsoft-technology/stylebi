/*
 * This file is part of StyleBI.
 * Copyright (C) 2026 InetSoft Technology
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
package inetsoft.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;

/**
 * User documentation index URL for context-sensitive script help.
 */
public final class InetsoftUserDocumentation {
   private InetsoftUserDocumentation() {
   }

   private static final String STYLEBI_DOC_SITE = "https://www.inetsoft.com/docs/stylebi/";
   private static final String DOC_INDEX_PREFIX =
      STYLEBI_DOC_SITE + "InetSoftUserDocumentation/";
   private static final String DOC_INDEX_SUFFIX = "/index.html";

   private static volatile String indexBaseUrl;

   /**
    * Base URL of the user documentation index (no fragment), e.g.
    * {@code https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.1.0/index.html}.
    */
   public static String getUserDocumentationIndexBaseUrl() {
      String local = indexBaseUrl;

      if(local != null) {
         return local;
      }

      synchronized(InetsoftUserDocumentation.class) {
         local = indexBaseUrl;

         if(local == null) {
            indexBaseUrl = local = loadIndexBaseUrl();
         }

         return local;
      }
   }

   /**
    * Full context-sensitive help URL for the given CSH id (anchor only, no encoding).
    */
   public static String contextSensitiveHelpUrl(String cshid) {
      return getUserDocumentationIndexBaseUrl() + "#cshid=" + cshid;
   }

   /**
    * Normalizes {@code !url} entries for StyleBI script API docs to the versioned index base.
    * Rewrites legacy {@code .../stylebi/index.html#cshid=} URLs and aligns Tern output when the
    * build used a {@code -SNAPSHOT} {@code project.version}.
    */
   public static void rewriteScriptApiDocUrls(JsonNode node) {
      if(node == null || node.isNull()) {
         return;
      }

      if(node.isObject()) {
         ObjectNode o = (ObjectNode) node;
         JsonNode urlNode = o.get("!url");

         if(urlNode != null && urlNode.isTextual()) {
            String t = urlNode.asText();
            String n = normalizeStyleBiScriptDocUrl(t);

            if(!n.equals(t)) {
               o.put("!url", n);
            }
         }

         for(Iterator<String> it = o.fieldNames(); it.hasNext(); ) {
            rewriteScriptApiDocUrls(o.get(it.next()));
         }
      }
      else if(node.isArray()) {
         for(JsonNode c : node) {
            rewriteScriptApiDocUrls(c);
         }
      }
   }

   /**
    * Rewrites any legacy unversioned StyleBI doc URL embedded inside an HTML string
    * (e.g. a Bundle.properties label value that contains an {@code <a href='...'>} tag).
    */
   public static String normalizeStyleBiDocHtmlContent(String html) {
      if(html == null || !html.contains(STYLEBI_DOC_SITE)) {
         return html;
      }

      return html.replace(STYLEBI_DOC_SITE + "index.html",
                          getUserDocumentationIndexBaseUrl());
   }

   static String normalizeStyleBiScriptDocUrl(String url) {
      if(url == null) {
         return null;
      }

      int csh = url.indexOf("#cshid=");

      if(csh < 0 || !url.startsWith(STYLEBI_DOC_SITE)) {
         return url;
      }

      return getUserDocumentationIndexBaseUrl() + url.substring(csh);
   }

   private static String loadIndexBaseUrl() {
      Package pkg = InetsoftUserDocumentation.class.getPackage();

      if(pkg != null) {
         String ver = pkg.getSpecificationVersion();

         if(ver != null && !ver.isBlank()) {
            return indexBaseForDocVersion(stripSnapshotSuffix(ver.trim()));
         }
      }

      return indexBaseForDocVersion("1.1.0");
   }

   private static String indexBaseForDocVersion(String docVersion) {
      return DOC_INDEX_PREFIX + docVersion + DOC_INDEX_SUFFIX;
   }

   private static String stripSnapshotSuffix(String version) {
      if(version.endsWith("-SNAPSHOT")) {
         return version.substring(0, version.length() - "-SNAPSHOT".length());
      }

      return version;
   }
}
