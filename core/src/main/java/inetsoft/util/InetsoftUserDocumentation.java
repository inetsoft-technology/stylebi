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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User documentation index URL for context-sensitive script help.
 */
public final class InetsoftUserDocumentation {
   private static final Logger LOG = LoggerFactory.getLogger(InetsoftUserDocumentation.class);

   private InetsoftUserDocumentation() {
   }

   private static final String STYLEBI_DOC_SITE = "https://www.inetsoft.com/docs/stylebi/";
   private static final String DOC_INDEX_PREFIX =
      STYLEBI_DOC_SITE + "InetSoftUserDocumentation/";
   private static final String DOC_INDEX_SUFFIX = "/index.html";

   private static final Pattern VERSIONED_DOC_URL_PATTERN = Pattern.compile(
      Pattern.quote(DOC_INDEX_PREFIX) + "[^/]+" + Pattern.quote(DOC_INDEX_SUFFIX));

   // Cached once per JVM lifetime via double-checked locking. There is intentionally no reset
   // mechanism; tests that need to exercise loadIndexBaseUrl() directly must clear this field
   // via reflection in a @BeforeEach, as done in InetsoftUserDocumentationTest.
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
    *
    * @param cshid the context-sensitive help identifier; must not be null
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
               // put() replaces an existing key in-place; no structural change to the map,
               // so the fieldNames() iterator below remains valid.
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
    * Rewrites StyleBI user-documentation index URLs embedded in an HTML string (for example a
    * {@code Bundle.properties} label containing an {@code <a href='...'>} tag) to the current
    * versioned index base. Handles the legacy unversioned {@code .../stylebi/index.html} path and
    * any stale {@code .../InetSoftUserDocumentation/&lt;version&gt;/index.html} URL.
    */
   public static String normalizeStyleBiDocHtmlContent(String html) {
      if(html == null || !html.contains(STYLEBI_DOC_SITE)) {
         return html;
      }

      String base = getUserDocumentationIndexBaseUrl();
      String out = VERSIONED_DOC_URL_PATTERN.matcher(html).replaceAll(Matcher.quoteReplacement(base));
      return out.replace(STYLEBI_DOC_SITE + "index.html", base);
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
            return indexBaseForDocVersion(stripPreReleaseSuffix(ver.trim()));
         }
      }

      LOG.warn("Could not determine Specification-Version from package manifest; " +
               "falling back to hardcoded doc version 1.1.0");
      return indexBaseForDocVersion("1.1.0");
   }

   private static String indexBaseForDocVersion(String docVersion) {
      return DOC_INDEX_PREFIX + docVersion + DOC_INDEX_SUFFIX;
   }

   static String stripPreReleaseSuffix(String version) {
      if(version.startsWith("v") || version.startsWith("V")) {
         version = version.substring(1);
      }

      int dash = version.indexOf('-');
      return dash < 0 ? version : version.substring(0, dash);
   }
}
