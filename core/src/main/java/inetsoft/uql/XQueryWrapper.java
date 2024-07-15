/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql;

import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * XQueryWrapper object wraps an XQuery object for writing/parsing XML.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class XQueryWrapper implements XMLSerializable {

   public XQueryWrapper() {
      this(null);
   }

   public XQueryWrapper(XQuery xquery) {
      setXQuery(xquery);
   }

   /**
    * Generate the XML segment to represent this query.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      if(xquery != null) {
         writer.println("<query name=\"" + Tool.escape(xquery.getName()) +
                 "\" type=\"" + xquery.getType() + "\" datasource=\"" +
                 Tool.escape(xquery.getDataSource().getFullName()) + "\">");
         xquery.writeXML(writer);
         writer.println("</query>");
      }
   }

   /**
    * Parse the XML element that contains information on this wrapper.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      setXQuery(null);
      DataSourceRegistry dxreg = DataSourceRegistry.getRegistry();
      String name = Tool.getAttribute(elem, "name");
      String type = Tool.getAttribute(elem, "type");
      String dxname = Tool.getAttribute(elem, "datasource");
      XDataSource datasource = null;
      String cls = Config.getQueryClass(type);

      if(cls == null) {
         LOG.warn("Query type not defined: " + type + ", " + name + ", " + dxname);
         return;
      }

      datasource = dxreg.getDataSource(dxname);

      if(datasource == null) {
         LOG.warn("Data source(" + dxname + ") not found, query ignored: " + name);
         return;
      }

      try {
         NodeList list2 = Tool.getChildNodesByTagName(elem, "query_" + type);
         Node queryNode = null;

         if(list2 == null || list2.getLength() != 1) {
            LOG.debug("Missing query tag: query_" + type);
            assert list2 != null;

            // if the query_TYPE node doesn't exist, use the first node so
            // custom (Tabular) query may use any tag name and doesn't have
            // to conform to the query_TYPE convention
            if(list2.getLength() > 0) {
               queryNode = elem.getFirstChild();
            }
         }
         else {
            queryNode = list2.item(0);
         }

         Class<?> dxClass = Config.getClass(type, cls);
         XQuery dx = (XQuery) dxClass.getConstructor().newInstance();

         dx.setName(name);
         dx.setDataSource(datasource);
         dx.parseXML((Element) queryNode);
         setXQuery(dx);
      }
      catch(Throwable e) {
         LOG.error("Error parsing query: " + name, e);
      }
   }

   /**
    * Get the XQuery wrapped by this class
    * @return XQuery set by setXQuery or parseXML
    */
   public XQuery getXQuery() {
      return xquery;
   }

   /**
    * Set the XQuery wrapped by this class
    */
   public void setXQuery(XQuery xquery) {
      this.xquery = xquery;
   }

   private XQuery xquery;
   private static final Logger LOG =
      LoggerFactory.getLogger(XQueryWrapper.class);
}
