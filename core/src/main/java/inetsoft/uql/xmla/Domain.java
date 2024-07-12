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
package inetsoft.uql.xmla;

import inetsoft.uql.XCube;
import inetsoft.uql.XDomain;
import inetsoft.util.*;
import inetsoft.util.xml.XMLStorage.XMLFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A domain is a container for OLAP cubes.
 *
 * @author  InetSoft Technology
 * @since   10.1
 */
public class Domain implements XDomain, XMLFragment {
   /**
    * Set the data source for the domain.
    *
    * @param datasource the data source name.
    */
   @Override
   public void setDataSource(String datasource) {
      this.datasource = datasource;
   }

   /**
    * Get the data source of the domain.
    *
    * @return the data source name.
    */
   @Override
   public String getDataSource() {
      return datasource;
   }

   /**
    * Get the cubes contained in this domain.
    *
    * @return an Enumeration of XCube objects.
    */
   @Override
   public Enumeration getCubes() {
      if(cubes == null) {
         return Collections.emptyEnumeration();
      }

      return Collections.enumeration(cubes);
   }

   /**
    * Set the cubes contained in this domain.
    *
    * @param cubes cubes contained in this domain.
    */
   @SuppressWarnings("unchecked")
   public void setCubes(Collection cubes) {
      this.cubes = new ArrayList<>(cubes);
   }

   /**
    * Get the specified cube.
    *
    * @param name the name of the cube.
    *
    * @return an XCube object, or <code>null</code> if no cube with the
    *         specified name exists.
    */
   @Override
   public XCube getCube(String name) {
      if(cubes == null) {
         return null;
      }

      return cubes.stream()
         .filter(c -> c.getName().equals(name))
         .findFirst()
         .orElse(null);
   }

   /**
    * Clears all cached cube result sets for this data model.
    */
   @Override
   public void clearCache() {
      DataSpace space;

      try {
         space = DataSpace.getDataSpace();
      }
      catch(Exception exc) {
         LOG.error("Failed to get data space", exc);
         return;
      }

      if(space.isDirectory("cubeCache")) {
         String[] files = space.list("cubeCache");

         if(files != null) {
            String prefix = getDataSource()
               .replace(' ', '_')
               .replace('/', '_')
               .replace('\\', '_');

            for(String file : files) {
               if(file.startsWith(prefix)) {
                  space.delete("cubeCache", file);
               }
            }
         }
      }
   }

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writeStart(writer);
      writeEnd(writer);
   }

   /**
    * Write start part tags.
    */
   @Override
   public void writeStart(PrintWriter writer) {
      writer.print("<Domain datasource=\"" +
                   Tool.byteEncode(datasource) + "\"");
      writer.print(" version=\"" + getVersion() + "\"");
      writer.print(" class=\"" + getClass().getName() + "\">");
      writer.print("<Cubes>");

      if(cubes != null) {
         for(Cube cube : cubes) {
            cube.writeXML(writer);
         }
      }

      writer.print("</Cubes>");
   }

   /**
    * Write end part tags.
    */
   @Override
   public void writeEnd(PrintWriter writer) {
      writer.print("</Domain>");
   }

   /**
    * Read in the definition of this object from an XML tag.
    *
    * @param tag the XML Element representing this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      datasource = Tool.getAttribute(tag, "datasource");
      datasource = Tool.byteDecode(datasource);

      String value = Tool.getAttribute(tag, "version");

      if(value != null) {
         version = value;
      }
      // no version? old than than 10.3
      else {
         version = "6.0";
      }

      Element cubesNode = Tool.getChildNodeByTagName(tag, "Cubes");
      NodeList cubesList = Tool.getChildNodesByTagName(cubesNode, "Cube");
      cubes = new ArrayList<>();

      for(int i = 0; i < cubesList.getLength(); i++) {
         Cube cube = new Cube();
         cube.parseXML((Element) cubesList.item(i));
         cubes.add(cube);
      }
   }

   /**
    * Create a copy of this object.
    *
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         Domain domain = (Domain) super.clone();

         if(cubes != null) {
            domain.setCubes(cubes.stream()
               .map(c -> (Cube) c.clone())
               .collect(Collectors.toList()));
         }

         return domain;
      }
      catch(CloneNotSupportedException e) {
         LOG.error("Failed to clone Domain", e);
      }

      return null;
   }

   /**
    * Get version.
    */
   public String getVersion() {
      return version;
   }

   /**
    * Set version.
    */
   public void setVersion(String version) {
      this.version = version;
   }

   private String datasource;
   private List<Cube> cubes = null; // Cubes
   // if no version, it is old than 10.3, set 6.0 as default,
   // the the new created version is FileVersions.DOMAIN
   private String version = FileVersions.DOMAIN;

   private static final Logger LOG = LoggerFactory.getLogger(Domain.class);
}
