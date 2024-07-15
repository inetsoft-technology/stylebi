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
package inetsoft.graph.mxgraph.io;

import inetsoft.graph.mxgraph.model.mxGraphModel.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton class that acts as a global registry for codecs. See
 * {@link mxCodec} for an example.
 */
public class mxCodecRegistry {

   private static final Logger log = Logger.getLogger(mxCodecRegistry.class.getName());

   /**
    * Maps from constructor names to codecs.
    */
   protected static Hashtable<String, mxObjectCodec> codecs = new Hashtable<String, mxObjectCodec>();

   /**
    * Maps from classnames to codecnames.
    */
   protected static Hashtable<String, String> aliases = new Hashtable<String, String>();

   /**
    * Holds the list of known packages. Packages are used to prefix short
    * class names (eg. mxCell) in XML markup.
    */
   protected static List<String> packages = new ArrayList<String>();

   // Registers the known codecs and package names
   static {
      addPackage("inetsoft.graph.mxgraph");
      addPackage("inetsoft.graph.mxgraph.util");
      addPackage("inetsoft.graph.mxgraph.model");
      addPackage("inetsoft.graph.mxgraph.view");
      addPackage("java.lang");
      addPackage("java.util");

      register(new mxObjectCodec(new ArrayList<Object>()));
      register(new mxModelCodec());
      register(new mxCellCodec());
      register(new mxStylesheetCodec());

      register(new mxRootChangeCodec());
      register(new mxChildChangeCodec());
      register(new mxTerminalChangeCodec());
      register(new mxGenericChangeCodec(new mxValueChange(), "value"));
      register(new mxGenericChangeCodec(new mxStyleChange(), "style"));
      register(new mxGenericChangeCodec(new mxGeometryChange(), "geometry"));
      register(new mxGenericChangeCodec(new mxCollapseChange(), "collapsed"));
      register(new mxGenericChangeCodec(new mxVisibleChange(), "visible"));
   }

   /**
    * Registers a new codec and associates the name of the template constructor
    * in the codec with the codec object. Automatically creates an alias if the
    * codename and the classname are not equal.
    */
   public static mxObjectCodec register(mxObjectCodec codec)
   {
      if(codec != null) {
         String name = codec.getName();
         codecs.put(name, codec);

         String classname = getName(codec.getTemplate());

         if(!classname.equals(name)) {
            addAlias(classname, name);
         }
      }

      return codec;
   }

   /**
    * Adds an alias for mapping a classname to a codecname.
    */
   public static void addAlias(String classname, String codecname)
   {
      aliases.put(classname, codecname);
   }

   /**
    * Returns a codec that handles the given object, which can be an object
    * instance or an XML node.
    *
    * @param name Java class name.
    */
   public static mxObjectCodec getCodec(String name)
   {
      String tmp = aliases.get(name);

      if(tmp != null) {
         name = tmp;
      }

      mxObjectCodec codec = codecs.get(name);

      // Registers a new default codec for the given name
      // if no codec has been previously defined.
      if(codec == null) {
         Object instance = getInstanceForName(name);

         if(instance != null) {
            try {
               codec = new mxObjectCodec(instance);
               register(codec);
            }
            catch(Exception e) {
               log.log(Level.FINEST, "Failed to create and register a codec for the name: " + name, e);
            }
         }
         else {
            log.log(Level.FINEST, "Failed to create codec for " + name);
         }
      }

      return codec;
   }

   /**
    * Adds the given package name to the list of known package names.
    *
    * @param packagename Name of the package to be added.
    */
   public static void addPackage(String packagename)
   {
      packages.add(packagename);
   }

   /**
    * Creates and returns a new instance for the given class name.
    *
    * @param name Name of the class to be instantiated.
    *
    * @return Returns a new instance of the given class.
    */
   public static Object getInstanceForName(String name)
   {
      Class<?> clazz = getClassForName(name);

      if(clazz != null) {
         if(clazz.isEnum()) {
            // For an enum, use the first constant as the default instance
            return clazz.getEnumConstants()[0];
         }
         else {
            try {
               return clazz.newInstance();
            }
            catch(Exception e) {
               log.log(Level.FINEST, "Failed to construct class instance for " + name, e);
            }
         }
      }

      log.log(Level.FINEST, "Failed to construct instance for " + name);

      return null;
   }

   /**
    * Returns a class that corresponds to the given name.
    *
    * @param name
    *
    * @return Returns the class for the given name.
    */
   public static Class<?> getClassForName(String name)
   {
      try {
         return Class.forName(name);
      }
      catch(Exception e) {
         log.log(Level.FINEST, "Failed to get a class object for " + name, e);
      }

      for(int i = 0; i < packages.size(); i++) {
         String s = packages.get(i);
         String nameWithPackage = s + "." + name;

         try {
            return Class.forName(nameWithPackage);
         }
         catch(Exception e) {
            log.log(Level.FINEST, "Failed to get a class object for " + nameWithPackage, e);
         }
      }

      log.log(Level.FINEST, "Class " + name + " not found");
      return null;
   }

   /**
    * Returns the name that identifies the codec associated
    * with the given instance..
    * <p>
    * The I/O system uses unqualified classnames, eg. for a
    * <code>inetsoft.graph.mxgraph.model.mxCell</code> this returns
    * <code>mxCell</code>.
    *
    * @param instance Instance whose node name should be returned.
    *
    * @return Returns a string that identifies the codec.
    */
   public static String getName(Object instance)
   {
      Class<? extends Object> type = instance.getClass();

      if(type.isArray() || Collection.class.isAssignableFrom(type)
         || Map.class.isAssignableFrom(type))
      {
         return "Array";
      }
      else {
         if(packages.contains(type.getPackage().getName())) {
            return type.getSimpleName();
         }
         else {
            return type.getName();
         }
      }
   }

}
