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
import inetsoft.uql.xmla.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Enumeration;


/**
 * XDomainWrapper wraps an XDomain for XML storage.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class XDomainWrapper implements XMLSerializable {

   public XDomainWrapper() {
      this(null);
   }

   public XDomainWrapper(XDomain domain) {
      setDomain(domain);
   }

   /**
    * Generate the XML segment to represent this domain.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      if(domain != null) {
         domain.writeXML(writer);
      }
   }

   /**
    * Parse the XML element that contains information on this wrapper.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      setDomain(null);
      String cls = Tool.getAttribute(elem, "class");

      try {
         domain = (XDomain) Class.forName(cls).newInstance();
         domain.parseXML(elem);
         setDomain(domain);
      }
      catch(Exception ex) {
         LOG.warn("Failed to parse domain", ex);
      }
   }

   private void postParse() {
      if(domain instanceof Domain) {
         Domain dom = (Domain) domain;
         float version = Float.parseFloat(dom.getVersion());

         if(version > 6.0) {
            return;
         }

         dom.setVersion(FileVersions.DOMAIN);
         dom.clearCache();
         Enumeration cubes = dom.getCubes();

         while(cubes.hasMoreElements()) {
            Cube cube = (Cube) cubes.nextElement();
            Enumeration dimensions = cube.getDimensions();

            while(dimensions.hasMoreElements()) {
               Dimension dimension = (Dimension) dimensions.nextElement();
               dimension.setType(getNewType(dimension.getType()));
            }
         }
      }
   }

   /**
    * Get new type.
    */
   private int getNewType(int type) {
      if(type == 0) {
         return DataRef.NONE;
      }
      else if(type == 1) {
         return DataRef.CUBE_TIME_DIMENSION;
      }
      else if(type == 2) {
         return DataRef.CUBE_DIMENSION;
      }
      else if(type == 4) {
         return DataRef.CUBE_MODEL_DIMENSION;
      }
      else if(type == 5) {
         return DataRef.CUBE_MODEL_TIME_DIMENSION;
      }
      else if(type == 7) {
         return DataRef.CUBE_DIMENSION;
      }
      else if(type == 8) {
         return DataRef.CUBE_MEASURE;
      }

      return type;
   }

   /**
    * Get the XDomain wrapped by this class
    * @return XDomain set by setDomain or parseXML
    */
   public XDomain getDomain() {
      return domain;
   }

   /**
    * Set the XDomain wrapped by this class
    */
   public void setDomain(XDomain domain) {
      this.domain = domain;
   }

   private XDomain domain;
   private static final Logger LOG =
      LoggerFactory.getLogger(XDomainWrapper.class);
}
