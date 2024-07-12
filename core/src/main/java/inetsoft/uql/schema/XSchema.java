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
package inetsoft.uql.schema;

import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * XSchema is a schema parser. It parses a XML representation of a
 * schema, and create one or more type nodes. It is used internally
 * to parse data meta data.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XSchema implements java.io.Serializable {
   /**
    * Null type.
    */
   public static final String NULL = Tool.NULL;
   /**
    * String type.
    */
   public static final String STRING = Tool.STRING;
   /**
    * Boolean type.
    */
   public static final String BOOLEAN = Tool.BOOLEAN;
   /**
    * Float type.
    */
   public static final String FLOAT = Tool.FLOAT;
   /**
    * Double type.
    */
   public static final String DOUBLE = Tool.DOUBLE;
   /**
    * Decimal type.
    */
   public static final String DECIMAL = Tool.DECIMAL;
   /**
    * Character type.
    */
   public static final String CHAR = Tool.CHAR;
   /**
    * Character type.
    */
   public static final String CHARACTER = Tool.CHARACTER;
   /**
    * Byte type.
    */
   public static final String BYTE = Tool.BYTE;
   /**
    * Short type.
    */
   public static final String SHORT = Tool.SHORT;
   /**
    * Integer type.
    */
   public static final String INTEGER = Tool.INTEGER;
   /**
    * Long type.
    */
   public static final String LONG = Tool.LONG;
   /**
    * Time instant type.
    */
   public static final String TIME_INSTANT = Tool.TIME_INSTANT;
   /**
    * Date type.
    */
   public static final String DATE = Tool.DATE;
   /**
    * Time type.
    */
   public static final String TIME = Tool.TIME;
   /**
    * Enum type.
    */
   public static final String ENUM = Tool.ENUM;
   /**
    * User defined type.
    */
   public static final String USER_DEFINED = Tool.USER_DEFINED;
   /**
    * Role type.
    */
   public static final String ROLE = Tool.ROLE;
   /**
    * User type.
    */
   public static final String USER = Tool.USER;
   /**
    * Color type.
    */
   public static final String COLOR = Tool.COLOR;
   /**
    * Unknown type.
    */
   public static final String UNKNOWN = Tool.UNKNOWN;

   /**
    * Check if is a numeric type.
    * @param type the specified data type.
    * @return <tt>true</tt> if numeric, <tt>false</tt> otherwise.
    */
   public static boolean isNumericType(String type) {
      return BYTE.equals(type) || SHORT.equals(type) || INTEGER.equals(type) ||
         LONG.equals(type) || FLOAT.equals(type) || DOUBLE.equals(type);
   }

   /**
    * Check if is a date type.
    * @param type the specified data type.
    * @return <tt>true</tt> if date, <tt>false</tt> otherwise.
    */
   public static boolean isDateType(String type) {
      return DATE.equals(type) || TIME.equals(type) ||
             TIME_INSTANT.equals(type);
   }

   /**
    * Check if is a boolean type.
    * @param type the specified data type.
    * @return <tt>true</tt> if boolean, <tt>false</tt> otherwise.
    */
   public static boolean isBooleanType(String type) {
      return BOOLEAN.equals(type);
   }

   /**
    * Merge two numeric types.
    * @param type1 the specified numeric type a.
    * @param type2 the specified numeric type b.
    * @return the merged numeric type.
    */
   public static String mergeNumericType(String type1, String type2) {
      if(!isNumericType(type1) || !isNumericType(type2)) {
         return type1;
      }

      int p1 = numerics.get(type1);
      int p2 = numerics.get(type2);

      return p1 >= p2 ? type1 : type2;
   }

   /**
    * Create a schema parser.
    * @param root XML root of the schema definition.
    */
   public XSchema(Element root) {
      NodeList nlist = Tool.getChildNodesByTagName(root, "type");

      for(int i = 0; i < nlist.getLength(); i++) {
         Element typenode = (Element) nlist.item(i);
         String name = typenode.getAttribute("name");
         // handle user defined type
         String subtype = Tool.getAttribute(typenode, "type");

         if(subtype != null) {
            XTypeNode xtype = createTypeNode(subtype);

            xtype.setName(name);
            xtype.parseXML(typenode);
            typemap.put(name, xtype);
         }
         // regular type
         else {
            XTypeNode xtype = typemap.get(name);

            if(xtype == null) {
               typemap.put(name, xtype = new XTypeNode(name));
            }

            parseType(xtype, typenode);
            types.addElement(xtype);
         }
      }
   }

   /**
    * Get the type node (type definition) of the specified type.
    * @param type type name.
    * @return type definition.
    */
   public XTypeNode getTypeNode(String type) {
      return typemap.get(type);
   }

   /**
    * Write the types in XML format.
    */
   public void writeXML(PrintWriter writer) {
      for(int i = 0; i < types.size(); i++) {
         (types.elementAt(i)).writeXML(writer);
      }
   }

   /**
    * Parse a type definition.
    */
   private void parseType(XTypeNode xtype, Element typenode) {
      xtype.parseXML(typenode);

      // find all attributes in the type
      NodeList nlist = Tool.getChildNodesByTagName(typenode, "attribute");

      for(int i = 0; i < nlist.getLength(); i++) {
         Element attr = (Element) nlist.item(i);
         String type = Tool.getAttribute(attr, "type");
         XTypeNode attrnode = createTypeNode(type);

         if(attrnode == null) {
            LOG.warn("Attribute type not supported: " + type);
            continue;
         }

         attrnode.setName(Tool.getAttribute(attr, "name"));

         // @by mikec, add format support to attributes.
         attrnode.parseXML(attr);
         xtype.addAttribute(attrnode);
      }

      // find all elements in the type
      nlist = Tool.getChildNodesByTagName(typenode, "element");

      for(int i = 0; i < nlist.getLength(); i++) {
         Element elem = (Element) nlist.item(i);
         String type = Tool.getAttribute(elem, "type");
         XTypeNode elemnode = createTypeNode(type);

         if(elemnode == null) {
            LOG.warn("Element type not supported: " + type);
            continue;
         }

         elemnode.setName(Tool.getAttribute(elem, "name"));
         elemnode.parseXML(elem);
         xtype.addChild(elemnode);
      }
   }

   /**
    * Check if the specified type is the primitive type.
    */
   public static boolean isPrimitiveType(String type) {
      return typeclasses.get(type) != null;
   }

   /**
    * Create the type node for the specified primitive type.
    */
   public static XTypeNode createPrimitiveType(String type) {
      Class cls = typeclasses.get(type);

      if(cls != null) {
         try {
            return (XTypeNode) cls.newInstance();
         }
         catch(Exception e) {
            LOG.error("Failed to create instance of class " + cls +
               " for type " + type, e);
         }
      }

      return null;
   }

   /**
    * Create the type node for the specified type.
    */
   public XTypeNode createTypeNode(String type) {
      Class cls = typeclasses.get(type);

      if(cls != null) {
         try {
            return (XTypeNode) cls.newInstance();
         }
         catch(Exception e) {
            LOG.error(e.getMessage(), e);
         }
      }

      XTypeNode typenode = typemap.get(type);

      if(typenode == null) {
         typemap.put(type, typenode = new XTypeNode(type));
      }

      UserDefinedType user = new UserDefinedType();

      user.setUserType(typenode);
      return user;
   }

   /**
    * Create a subclass of the XValueNode for the specified type.
    * @param type one of the type constant.
    */
   public static XValueNode createValueNode(String type) {
      String cls = valueclasses.get(type);

      try {
         return (XValueNode) Class.forName(cls).newInstance();
      }
      catch(Exception e) {
         LOG.error("Failed to create instance of class " + cls +
            " for type " + type, e);
      }

      return null;
   }

   /**
    * Create primitive type node from a java class.
    * @param name the specfied name.
    * @param cls the specified java class.
    * @return corresponding primitive type node.
    */
   public static XTypeNode createPrimitiveType(String name, Class cls) {
      String type = Tool.getDataType(cls);
      XTypeNode tnode = createPrimitiveType(type);
      tnode.setName(name);
      return tnode;
   }

   /**
    * Check whether two data types are compatible with each other.
    *
    * @return true if the types are compatible, false otherwise.
    */
   public static boolean areDataTypesCompatible(String typeA, String typeB) {
      if(Objects.equals(typeA, typeB)) {
         return true;
      }

      if(typeA == null || typeB == null) {
         return false;
      }

      if(XSchema.isNumericType(typeA)) {
         return XSchema.isNumericType(typeB);
      }
      else if(XSchema.isDateType(typeA)) {
         return XSchema.isDateType(typeB);
      }

      return false;
   }

   private final Hashtable<String, XTypeNode> typemap = new Hashtable<>(); // type name -> XNode
   private final Vector<XTypeNode> types = new Vector<>(); // XTypeNode roots same as in typemap, ordering
   // type -> class name
   private static final Map<String, Class> typeclasses = new Object2ObjectOpenHashMap<>();
   private static final Map<String, String> valueclasses = new HashMap<>(); // type -> class name
   private static final Map<String, Integer> numerics = new HashMap<>();

   static {
      typeclasses.put(STRING, inetsoft.uql.schema.StringType.class);
      typeclasses.put(BOOLEAN, inetsoft.uql.schema.BooleanType.class);
      typeclasses.put(FLOAT, inetsoft.uql.schema.FloatType.class);
      typeclasses.put(DOUBLE, inetsoft.uql.schema.DoubleType.class);
      typeclasses.put(CHAR, inetsoft.uql.schema.CharacterType.class);
      typeclasses.put(CHARACTER, inetsoft.uql.schema.CharacterType.class);
      typeclasses.put(BYTE, inetsoft.uql.schema.ByteType.class);
      typeclasses.put(SHORT, inetsoft.uql.schema.ShortType.class);
      typeclasses.put(INTEGER, inetsoft.uql.schema.IntegerType.class);
      typeclasses.put(LONG, inetsoft.uql.schema.LongType.class);
      typeclasses.put(TIME_INSTANT, inetsoft.uql.schema.TimeInstantType.class);
      typeclasses.put(DATE, inetsoft.uql.schema.DateType.class);
      typeclasses.put(TIME, inetsoft.uql.schema.TimeType.class);
      typeclasses.put(ENUM, inetsoft.uql.schema.EnumType.class);
      typeclasses.put(USER_DEFINED, inetsoft.uql.schema.UserDefinedType.class);
      typeclasses.put(USER, inetsoft.uql.schema.UserType.class);
      typeclasses.put(ROLE, inetsoft.uql.schema.RoleType.class);
      typeclasses.put(DECIMAL, inetsoft.uql.schema.DoubleType.class);
      typeclasses.put("bigdecimal", inetsoft.uql.schema.DoubleType.class);

      valueclasses.put(STRING, "inetsoft.uql.schema.StringValue");
      valueclasses.put(BOOLEAN, "inetsoft.uql.schema.BooleanValue");
      valueclasses.put(FLOAT, "inetsoft.uql.schema.FloatValue");
      valueclasses.put(DOUBLE, "inetsoft.uql.schema.DoubleValue");
      valueclasses.put(CHAR, "inetsoft.uql.schema.CharacterValue");
      valueclasses.put(BYTE, "inetsoft.uql.schema.ByteValue");
      valueclasses.put(SHORT, "inetsoft.uql.schema.ShortValue");
      valueclasses.put(INTEGER, "inetsoft.uql.schema.IntegerValue");
      valueclasses.put(LONG, "inetsoft.uql.schema.LongValue");
      valueclasses.put(TIME_INSTANT, "inetsoft.uql.schema.TimeInstantValue");
      valueclasses.put(DATE, "inetsoft.uql.schema.DateValue");
      valueclasses.put(TIME, "inetsoft.uql.schema.TimeValue");
      valueclasses.put(ENUM, "inetsoft.uql.schema.EnumValue");
      valueclasses.put(USER_DEFINED, "inetsoft.uql.schema.UserDefinedValue");
      valueclasses.put(USER, "inetsoft.uql.schema.UserValue");
      valueclasses.put(ROLE, "inetsoft.uql.schema.RoleValue");

      numerics.put(BYTE, 1);
      numerics.put(SHORT, 2);
      numerics.put(INTEGER, 3);
      numerics.put(LONG, 4);
      numerics.put(FLOAT, 5);
      numerics.put(DOUBLE, 6);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XSchema.class);
}
