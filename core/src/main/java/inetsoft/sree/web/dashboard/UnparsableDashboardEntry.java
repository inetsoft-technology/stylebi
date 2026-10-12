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
package inetsoft.sree.web.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.PrintWriter;

/**
 * Placeholder for a {@code <node>} entry in a {@link DashboardRegistry} file whose
 * {@code <dashboard>} body could not be parsed (e.g. a viewsheet entry without {@code <path>}, a
 * non-numeric {@code <created>}/{@code <modified>}, or any other exception from the dashboard's
 * own {@code parseXML}, see {@link DashboardRegistry#parseXML}). It keeps the raw, unparsed
 * {@code <dashboard>} node and re-emits it byte-for-structure unchanged from {@link #writeXML},
 * so that {@link DashboardRegistry#update} and {@link DashboardRegistry#save} round-trip the
 * entry instead of silently dropping it from the file on the next rewrite (Bug #78103).
 *
 * <p>It is put into {@link DashboardRegistry#dashboardsMap} under the entry's {@code <name>},
 * exactly like a successfully parsed {@link Dashboard}, so that it keeps its original position in
 * the file (insertion order into the backing {@code LinkedHashMap}) and so that a by-name
 * operation that does not care about the dashboard's content - {@link DashboardRegistry#renameEntry}
 * and {@link DashboardRegistry#removeEntry}, both plain map-key operations - still finds it, the
 * same as a user renaming or removing a broken entry by the name shown in the file would expect.
 * It is never returned by {@link DashboardRegistry#getDashboard} or
 * {@link DashboardRegistry#getDashboardNames}, so it never reaches an ordinary caller and never
 * appears in a dashboard listing.
 */
class UnparsableDashboardEntry implements Dashboard {
   UnparsableDashboardEntry(Element dashboardNode) {
      this.dashboardNode = dashboardNode;
   }

   @Override
   public String getType() {
      return TYPE;
   }

   @Override
   public boolean isComposable() {
      return false;
   }

   @Override
   public String getDescription() {
      return null;
   }

   @Override
   public void setDescription(String description) {
      // not a usable dashboard, nothing to describe
   }

   /**
    * Re-emits the original {@code <dashboard>} node unchanged. The enclosing {@code <node>} and
    * {@code <name>} tags are written by {@link DashboardRegistry#writeXML}, the same as for a
    * successfully parsed dashboard, so only the {@code <dashboard>...</dashboard>} body itself is
    * written here.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      try {
         Transformer transformer = TransformerFactory.newInstance().newTransformer();
         transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
         transformer.transform(new DOMSource(dashboardNode), new StreamResult(writer));
         writer.println();
      }
      catch(TransformerException ex) {
         // should not happen: dashboardNode was itself parsed out of a valid document
         LOG.error("Failed to re-serialize unparsable dashboard node", ex);
      }
   }

   /**
    * Never called: this placeholder is constructed directly from the raw node captured by
    * {@link DashboardRegistry#parseXML}, not by parsing it through this method.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
   }

   /**
    * The raw {@code <dashboard>} node, as read from the registry file.
    */
   private final Element dashboardNode;

   static final String TYPE = "unparsable";
   private static final Logger LOG = LoggerFactory.getLogger(UnparsableDashboardEntry.class);
}
