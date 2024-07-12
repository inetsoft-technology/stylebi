/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * XMLSerializable defines the common method of write xml segment
 * to a print writer and parse it out from an xml segment.
 *
 * @version 6.0 9/30/2003
 * @author mikec
 */
public interface XMLSerializable {
   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   public void writeXML(PrintWriter writer);

   /**
    * Method to parse an xml segment.
    */
   public void parseXML(Element tag) throws Exception;
}

