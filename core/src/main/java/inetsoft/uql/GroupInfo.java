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

import inetsoft.uql.erm.DataRef;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Enumeration;

/**
 * GroupInfo store group field and sort info.
 * @version Oct 20, 2003
 * @author haiqiangy@inetsoft.com
 */
public interface GroupInfo {
   /**
    * Get all grouping columns.
    */
   public Enumeration getGroupRefs();

   /**
    * Remove group column
    */
   public void removeGroupRef(DataRef group);

   /**
    * Get order type.
    * @param group - column index
    * @return type of order.
    */
   public int getOrderType(DataRef group);

   /**
    * Write this group info to XML.
    * @param writer the stream to output the XML text to
    */
   public void writeXML(PrintWriter writer);

   /**
    * Read in the XML representation of this object.
    * @param tag the XML element representing this object.
    */
   public void parseXML(Element tag) throws Exception;

   /**
    * Make a copy of this object.
    */
   public Object clone();
}
