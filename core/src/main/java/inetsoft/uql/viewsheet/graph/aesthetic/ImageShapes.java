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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.aesthetic.SVGShape;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * This class tracks configurable color palettes.
 *
 * @author InetSoft Technology
 * @version 11.3
 */
public class ImageShapes {
   /**
    * Get the name of the color palettes.
    */
   public static Collection<String> getShapeNames() {
     return getShapeNames(getKey());
   }

   public static Collection<String> getShapeNames(String orgID) {
      synchronized(ImageShapes.class) {
         singleton.loadShapes(orgID);
         return singleton.getShapes(orgID).keySet();
      }
   }

   private static Map<String, GShape> getShapes() {
      return singleton.cache.get(getKey());
   }

   private static Map<String, GShape> getShapes(String orgID) {
      return singleton.cache.get(orgID);
   }

   public static void clearShapes() {
      if(singleton.cache.get(getKey()) != null) {
         singleton.cache.remove(getKey());
      }
   }

   /**
    * Get the named shape.
    */
   public static GShape getShape(String name) {
      synchronized(ImageShapes.class) {
         singleton.loadShapes();
         Map<String, GShape> shapeMap = singleton.getShapes();
         GShape shape = shapeMap == null ? null : shapeMap.get(name);

         //retry for host org if Global Default visible
         if(shape == null && SUtil.isDefaultVSGloballyVisible()) {
            singleton.loadShapes(Organization.getDefaultOrganizationID());
            shapeMap = singleton.getShapes(Organization.getDefaultOrganizationID());
            shape = shapeMap == null ? null : shapeMap.get(name);
         }

         return shape;
      }
   }

   /**
    * Load the builtin shapes.
    */
   private void loadBuiltins(Map<String, GShape> shapes) {
      String dir = "/inetsoft/uql/viewsheet/graph/shapes/";

      for(String name : builtins) {
         if(name.endsWith(".svg")) {
            shapes.put(name, new SVGShape(dir + name));
         }
         else {
            shapes.put(name, new GShape.ImageShape(dir + name));
         }
      }
   }

   public static String getShapesDirectory() {
     return getShapesDirectory(OrganizationManager.getInstance().getCurrentOrgID());
   }

   public static String getShapesDirectory(String orgID) {
      boolean multiTenant = SUtil.isMultiTenant();

      return multiTenant ? "portal/" + orgID + "/shapes" :
         getGlobalShapesDirectory();
   }

   public static String getGlobalShapesDirectory() {
      return "portal/shapes";
   }

   private static String getKey() {
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   /**
    * Load shapes from portal/shapes.
    */
   private synchronized void loadShapes() {
      loadShapes(getKey());
   }

   private synchronized void loadShapes(String orgID) {
      try {
         Map<String, GShape> shapes = cache.get(orgID);
         boolean init = shapes == null;
         DataSpace dataspace = DataSpace.getDataSpace();
         String dir = getShapesDirectory(orgID);

         if(!dataspace.exists(null, dir)) {
            dir = getGlobalShapesDirectory();
         }

         int idx = dir.lastIndexOf("/");
         String orgDir = dir.substring(0, idx);
         long last0 = dataspace.getLastModified(orgDir, "shapes");

         // here use != instead of use >
         // @see FileSystemDataSpace.getLastModified for folder temp
         if(init || last0 != last) {
            shapes = new LinkedHashMap<>();
            loadBuiltins(shapes);
            last = last0;
            //load the shapes of the global
            loadShapeFromFolder(getGlobalShapesDirectory(), shapes);

            if(!Tool.equals(dir, getGlobalShapesDirectory())) {
               //load the shapes of the organization
               loadShapeFromFolder(dir, shapes);
            }

            cache.put(orgID, shapes);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to read custom image shapes", ex);
      }
   }

   private void loadShapeFromFolder(String dir, Map<String, GShape> shapes) throws Exception {
      DataSpace dataspace = DataSpace.getDataSpace();
      Map<String, String[]> allShapes = getShapesFromFolder(new TreeMap<>(), dir);

      allShapes.forEach((lastModified, shapeInfo) -> {
         String folder = shapeInfo[0];
         String file = shapeInfo[1];

         try(InputStream input = dataspace.getInputStream(folder, file)) {
            if(input != null) {
               GShape.ImageShape shape = new GShape.ImageShape(file);
               shape.setImage(Tool.getImage(input));
               shapes.put(file, shape);
            }
         }
         catch(IOException e) {
            throw new RuntimeException(e);
         }
      });
   }

   private Map<String, String[]> getShapesFromFolder(Map<String, String[]> shapes, String dir) {
      DataSpace dataspace = DataSpace.getDataSpace();
      String[] files = dataspace.list(dir);

      for(String file : files) {
         String path = dir + "/" + file;

         if(dataspace.isDirectory(path)) {
            getShapesFromFolder(shapes, path);
            continue;
         }

         long lastModified = dataspace.getLastModified(dir, file);
         shapes.put(lastModified + path, new String[]{dir, file});
      }

      return shapes;
   }

   public static List<String> getBuiltins() {
      return builtins;
   }

   private static List<String> builtins = Arrays.asList(
      "100ArrowDown.svg", "101ArrowUp.svg", "102Check.svg", "103Cancel.svg",
      "104Exclamation.svg", "105Flag.svg", "106Light.svg", "107Star.svg",
      "108No.svg", "109Man.svg", "110Woman.svg", "111FaceHappy.svg",
      "112FaceSad.svg", "113Face.svg", "114ArrowUperRight.svg",
      "115ArrowLowerRight.svg");
   private static ImageShapes singleton = new ImageShapes();
   private Map<String, Map<String, GShape>> cache = new HashMap<>();
   private long last = 0;
   private static final Logger LOG = LoggerFactory.getLogger(ImageShapes.class);
}
