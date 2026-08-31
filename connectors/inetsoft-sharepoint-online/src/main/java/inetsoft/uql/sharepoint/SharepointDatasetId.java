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
package inetsoft.uql.sharepoint;

/**
 * Composes and parses the SPI's {@code TabularDatasetRef} id for this connector, which is
 * naturally composite (site + list).
 *
 * {@code TabularDatasetRef.id}'s javadoc forbids a {@code .} character anywhere in the id — not
 * merely as the join character. That matters here because the site half is not under our control:
 * {@link SharepointOnlineRuntime}'s existing {@code getSiteId(Site)} truncates the Graph site id
 * {@code "{hostname},{siteCollectionId},{webId}"} to its first comma, which keeps exactly the
 * {@code hostname} — e.g. {@code "contoso.sharepoint.com"}. Every site id this connector hands out
 * (root, group sites, subsites — anything but the literal string {@code "root"}) is therefore a
 * hostname, and hostnames contain dots by construction. Choosing a non-dot separator alone would
 * not satisfy the contract; the dots have to be escaped out of each component before joining.
 */
final class SharepointDatasetId {
   private SharepointDatasetId() {
   }

   private static final String SEPARATOR = "~";

   static String compose(String siteId, String listId) {
      return escape(siteId) + SEPARATOR + escape(listId);
   }

   record Parsed(String site, String list) {}

   static Parsed parse(String datasetId) {
      int sep = datasetId.indexOf(SEPARATOR);

      if(sep < 0) {
         throw new IllegalArgumentException("Not a SharePoint dataset id: " + datasetId);
      }

      return new Parsed(unescape(datasetId.substring(0, sep)),
                        unescape(datasetId.substring(sep + 1)));
   }

   // Order matters both ways. '%' must be escaped FIRST (else escaping '.'/'~' would introduce new
   // '%' characters that a later '%'->'%25' pass would double-escape), and unescaped LAST (else a
   // literal "%2E" that was never an escaped dot could be misread as one). Standard
   // percent-encoding discipline, applied to exactly the characters this id's contract forbids.
   private static String escape(String v) {
      return v.replace("%", "%25").replace(".", "%2E").replace(SEPARATOR, "%7E");
   }

   private static String unescape(String v) {
      return v.replace("%7E", SEPARATOR).replace("%2E", ".").replace("%25", "%");
   }
}
