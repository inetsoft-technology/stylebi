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
package inetsoft.uql.util.jtype;

import inetsoft.uql.XNode;
import inetsoft.uql.schema.*;
import inetsoft.util.Tool;

import javax.xml.datatype.XMLGregorianCalendar;
import java.beans.Introspector;
import java.lang.reflect.*;
import java.util.*;

/**
 * Parser for creating a XTypeNode tree from a Java object. The tree
 * can be constructed from object public method or public fields.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class JTypeParser {
   /**
    * Flag for using public fields.
    */
   public static final int FIELD = 1;
   /**
    * Flag for using public getter methods.
    */
   public static final int GETTER = 2;
   /**
    * Flag for using public setter methods.
    */
   public static final int SETTER = 4;

   /**
    * Create a parser.
    * @param getter true if using getter to retrieve members.
    */
   public JTypeParser(int type) {
      this.parsetype = type;
   }

   /**
    * Set whether object should be treated as optional
    * (with min occurs set to 0).
    */
   public void setObjectOptional(boolean optional) {
      objectOptional = optional;
   }

   /**
    * Check if object is optional.
    */
   public boolean isObjectOptional() {
      return objectOptional;
   }

   /**
    * Set the type resolver for resolving type of items in any collection.
    */
   public void setTypeResolver(TypeResolver resolver) {
      this.resolver = resolver;
   }

   /**
    * Get the type resolver.
    */
   public TypeResolver getTypeResolver() {
      return resolver;
   }

   /**
    * Check whether to decapitalize property name derived from a getter method.
    */
   public boolean isDecapitalize() {
      return decapitalize;
   }

   /**
    * Set whether to decapitalize property name derived from a getter method.
    */
   public void setDecapitalize(boolean decapitalize) {
      this.decapitalize = decapitalize;
   }

   /**
    * Convert a Java type to XTypeNode tree.
    */
   public XTypeNode parse(String name, Class<?> type) throws Exception {
      String fullname;

      if(type.isArray()) {
         fullname = type.getComponentType().getName();
      }
      else {
         fullname = type.getName();
      }

      return parse0(name, fullname, type, null);
   }

   /**
    * Parse holder as a container. At prepsent, List and Array is supported.
    */
   public XTypeNode parseContainerHolder(String name, Class<?> clazz,
                                         Method mtd, int indx)
      throws Exception
   {
      Type[] params = mtd.getGenericParameterTypes();

      if(indx >= params.length) {
         return null;
      }

      Type param = params[indx];
      boolean ctype = param instanceof Class;
      // the container class, e.g. List, Array...
      Class container = null;

      if(!(param instanceof ParameterizedType)) {
         return null;
      }

      ParameterizedType ptype = (ParameterizedType) param;
      Type[] types = ptype.getActualTypeArguments();

      if(types.length != 1 || !(types[0] instanceof ParameterizedType)) {
         return null;
      }

      param = types[0];
      Type rtype = ((ParameterizedType) param).getRawType();

      if(rtype instanceof Class) {
         container = (Class) rtype;
      }

      if(container == null || !Collection.class.isAssignableFrom(container)) {
         return null;
      }

      ptype = (ParameterizedType) param;
      types = ptype.getActualTypeArguments();

      if(types.length != 1) {
         return null;
      }

      Type uparam = types[0];
      // unit class, as unit, it's stored in container class
      Class unit = null;

      if(uparam instanceof Class) {
         unit = (Class) uparam;
      }
      // if required, we can go further here
      else if(uparam instanceof ParameterizedType) {
        Type utype = ((ParameterizedType) uparam).getRawType();

        if(utype instanceof Class) {
           unit = (Class) utype;
        }
      }

      if(unit == null) {
         return null;
      }

      // now we get container and unit. It's time to build type node now
      XTypeNode node = (XTypeNode) classtypes.get(unit);
      UserDefinedType unode = new UserDefinedType(name);
      unode.setMaxOccurs(XTypeNode.STAR);
      unode.setMinOccurs(0);

      // found existing type
      if(node == null) {
         node = parseUnit(unit, unit.getName());
      }

      unode.setAttribute("container_class", unit.getName());
      node.setAttribute("container_class", unit.getName());
      unode.setUserType(node);
      return unode;

   }

   /**
    * Convert a Java type to XTypeNode tree.
    */
   private XTypeNode parse0(String name, String fullname, Class<?> clazz,
                            Type type) throws Exception
   {
      XTypeNode node = (XTypeNode) classtypes.get(clazz);
      UserDefinedType unode = new UserDefinedType(name);
      // Object can be null
      unode.setMinOccurs(!objectOptional || clazz.isPrimitive() ||
         (clazz.getPackage() != null &&
            clazz.getPackage().getName().equals("java.lang")) ? 1 : 0);

      // if array, use element type
      if(clazz.isArray()) {
         clazz = clazz.getComponentType();
         node = (XTypeNode) classtypes.get(clazz);
         unode.setMaxOccurs(XTypeNode.STAR);
         unode.setMinOccurs(0);
      }
      else if(Collection.class.isAssignableFrom(clazz)) {
         if(resolver != null) {
            clazz = resolver.getType(fullname);
         }
         else if(type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) type;
            Type[] targs = ptype.getActualTypeArguments();

            if(targs != null && targs.length > 0 &&
               targs[0] instanceof Class<?>)
            {
               clazz = (Class<?>) targs[0];
            }
         }
         else {
            clazz = null;
         }

         if(clazz == null) {
            clazz = String.class;
         }

         node = (XTypeNode) classtypes.get(clazz);
         unode.setMaxOccurs(XTypeNode.STAR);
         unode.setMinOccurs(0);
      }

      // found existing type
      if(node == null) {
         node = parseUnit(clazz, fullname);
      }

      unode.setUserType(node);
      return unode;
   }

   /**
    * Parse one unit.
    */
   private XTypeNode parseUnit(Class<?> clazz, String fullname) throws Exception
   {
      XTypeNode node = new XTypeNode(clazz.getName());
      // @by billh, fix bug bug1301299254079
      // Class definition might contain recursion
      classtypes.put(clazz, node);

      if((parsetype & GETTER) != 0) {
         Method[] fields = clazz.getMethods();

         for(int i = 0; i < fields.length; i++) {
            String clsname = fields[i].getDeclaringClass().getName();

            if((fields[i].getName().startsWith("get") ||
                fields[i].getName().startsWith("is")) &&
               !clsname.startsWith("java.") && !clsname.startsWith("javax.") &&
               fields[i].getParameterTypes().length == 0) {
               // @by jasons don't add duplicate fields for getters of public
               // fields (e.g. Name and getName), instead the getter should
               // override the public field
               String pname = getPropertyName(fields[i].getName());

               if(fields[i].getReturnType() == clazz) {
                  continue;
               }

               XNode child = node.getChild(pname);

               if(child != null) {
                  node.removeChild(child);
               }

               fields[i].getGenericReturnType();
               node.addChild(parse0(pname, fullname + "." + fields[i].getName(),
                                    fields[i].getReturnType(),
                                    fields[i].getGenericReturnType()));
            }
         }
      }
      else if((parsetype & SETTER) != 0) {
         Method[] fields = clazz.getMethods();

         for(int i = 0; i < fields.length; i++) {
            String clsname = fields[i].getDeclaringClass().getName();

            if(fields[i].getName().startsWith("set") &&
               !clsname.startsWith("java.") &&
               !clsname.startsWith("javax."))
            {
               Class<?>[] pclasses = fields[i].getParameterTypes();
               Type[] ptypes = fields[i].getGenericParameterTypes();

               if(pclasses[0] != clazz && pclasses.length == 1) {
                  node.addChild(parse0(getPropertyName(fields[i].getName()),
                                       fullname + "." + fields[i].getName(),
                                       pclasses[0], ptypes[0]));
               }
            }
         }
      }

      if((parsetype & FIELD) != 0) {
         Field[] fields = clazz.getFields();
         StringBuilder enums = new StringBuilder();

         for(int i = 0; i < fields.length; i++) {
            // @by jasons don't allow duplicate fields
            XNode child = node.getChild(fields[i].getName());

            if(Tool.equals(fields[i].getType(), clazz) || child != null) {
               continue;
            }

            if(Modifier.isStatic(fields[i].getModifiers()) ||
               Modifier.isFinal(fields[i].getModifiers())) {
               // if enum (corba) constants
               if(fields[i].getType().equals(clazz)) {
                  if(enums.length() > 0) {
                     enums.append(",");
                  }

                  enums.append(fields[i].getName());
               }

               continue;
            }

            node.addChild(parse0(fields[i].getName(),
                                 fullname + "." + fields[i].getName(),
                                 fields[i].getType(),
                                 fields[i].getGenericType()));
         }

         // enum type, treat as string
         if(enums.length() > 0 && node.getChildCount() == 0) {
            node = node.clone(XSchema.ENUM);
            ((EnumType) node).setEnums(Tool.split(enums.toString(), ','));
         }
      }

      return node;
   }

   /**
    * Get the property name from a method name.
    */
   private String getPropertyName(String mname) {
      if(mname.startsWith("get")) {
         mname = mname.substring(3);
      }
      else if(mname.startsWith("set")) {
         mname = mname.substring(3);
      }
      else if(mname.startsWith("is")) {
         mname = mname.substring(2);
      }

      if(decapitalize) {
         return Introspector.decapitalize(mname);
      }

      return mname;
   }

   private int parsetype = FIELD;
   private boolean objectOptional = true;
   private TypeResolver resolver = null;
   private boolean decapitalize = false;

   Hashtable<Class<?>, XTypeNode> classtypes = new Hashtable<>();
   {
      classtypes.put(boolean.class, new BooleanType());
      classtypes.put(java.lang.Boolean.class, new BooleanType());
      classtypes.put(char.class, new CharacterType());
      classtypes.put(java.lang.Character.class, new CharacterType());
      classtypes.put(byte.class, new ByteType());
      classtypes.put(java.lang.Byte.class, new ByteType());
      classtypes.put(short.class, new ShortType());
      classtypes.put(java.lang.Short.class, new ShortType());
      classtypes.put(int.class, new IntegerType());
      classtypes.put(java.lang.Integer.class, new IntegerType());
      classtypes.put(long.class, new LongType());
      classtypes.put(java.lang.Long.class, new LongType());
      classtypes.put(float.class, new FloatType());
      classtypes.put(java.lang.Float.class, new FloatType());
      classtypes.put(double.class, new DoubleType());
      classtypes.put(java.lang.Double.class, new DoubleType());
      classtypes.put(void.class, new XTypeNode("(void)"));
      classtypes.put(java.lang.Void.class, new XTypeNode("(void)"));
      classtypes.put(String.class, new StringType());
      classtypes.put(Date.class, new TimeInstantType());
      classtypes.put(XMLGregorianCalendar.class, new TimeInstantType());
      classtypes.put(java.sql.Date.class, new DateType());
      classtypes.put(java.sql.Time.class, new TimeType());
      classtypes.put(java.sql.Timestamp.class, new TimeInstantType());
   }
}

