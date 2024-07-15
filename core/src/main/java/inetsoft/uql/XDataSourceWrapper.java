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
package inetsoft.uql;

import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.PrintWriter;

/**
 * XDataSourceWrapper object wraps an XDataSource object for writing/parsing XML.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class XDataSourceWrapper implements XMLSerializable {

   public XDataSourceWrapper() {
      this(null);
   }

   public XDataSourceWrapper(XDataSource source) {
      setSource(source);
   }

   /**
    * Generate the XML segment to represent this query.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      if(source != null) {
         writer.println("<datasource name=\"" +
                 Tool.escape(source.getFullName()) + "\" type=\"" +
                 source.getType() + "\">");
         source.writeXML(writer);
         writer.println("</datasource>");
      }
   }

   /**
    * Parse the XML element that contains information on this wrapper.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      setSource(null);
      String name = Tool.getAttribute(elem, "name");
      String type = Tool.getAttribute(elem, "type");
      String cls = Config.getDataSourceClass(type);

      if(cls == null) {
         LOG.warn("Data source type not found: " + type);
         return;
      }

      NodeList list2 = Tool.getChildNodesByTagName(elem, "ds_" + type);
      Node dsNode = null;

      if(list2 == null || list2.getLength() != 1) {
         LOG.debug("Missing datasource tag: ds_" + type);
         assert list2 != null;

         // if the query_TYPE node doesn't exist, use the first node so
         // custom (Tabular) query may use any tag name and doesn't have
         // to conform to the query_TYPE convention
         if(list2.getLength() > 0) {
            dsNode = elem.getFirstChild();
         }
      }
      else {
         dsNode = list2.item(0);
      }

      Class<?> dxClass = Config.getClass(type, cls);
      XDataSource dx = (XDataSource) dxClass.getConstructor().newInstance();
      dx.setName(name);
      dx.parseXML((Element) dsNode);

      setSource(dx);
   }

   /**
    * Get the XDataSource wrapped by this class
    * @return XDataSource set by setXDataSource or parseXML
    */
   public XDataSource getSource() {
      return source;
   }

   /**
    * Set the XDataSource wrapped by this class
    */
   public void setSource(XDataSource source) {
      this.source = source;
   }

   private XDataSource source;
   private static final Logger LOG =
      LoggerFactory.getLogger(XDataSourceWrapper.class);
}
