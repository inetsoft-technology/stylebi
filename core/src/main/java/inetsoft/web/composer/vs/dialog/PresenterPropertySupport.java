/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.composer.vs.dialog;

import inetsoft.report.Presenter;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.beans.PropertyDescriptor;
import java.util.*;

/**
 * Reads and writes the bean properties of a presenter ({@link PresenterRef#getParameter}) from
 * plain JSON-style values, with the same type mapping the Presenter Properties dialog applies
 * ({@link PresenterPropertyDialogService}): booleans, integers, numbers and strings as they are,
 * colors as {@code #RRGGBB}, sizes and insets as objects. The properties are the ones the dialog
 * lists, {@link PresenterRef#getPropertyDescriptors()}, less the types a JSON value cannot carry
 * (fonts, images).
 */
public final class PresenterPropertySupport {
   private PresenterPropertySupport() {
   }

   /**
    * The dialog's color-to-text form: {@code #RRGGBB}, empty for no color.
    */
   static String colorToString(Color color) {
      return color == null ? "" : "#" + Tool.colorToHTMLString(color);
   }

   /**
    * The dialog's text-to-color form: {@code #RRGGBB} or {@code 0xRRGGBB}, null for empty text.
    */
   static Color decodeColor(String color) {
      return color == null || color.isEmpty() ? null : Color.decode(color);
   }

   /**
    * Checks {@code properties} against the properties of {@code presenter} without storing
    * anything.
    *
    * @throws IllegalArgumentException if the presenter cannot be loaded, a property name is not
    *                                  one of its settable properties, or a value does not fit the
    *                                  property's type.
    */
   public static void validate(String presenter, Map<String, Object> properties) {
      convert(presenter, descriptors(presenter), properties);
   }

   /**
    * Stores {@code properties} as parameters of {@code ref}. A null value clears that property
    * back to the presenter's default. Nothing is stored when any entry is invalid.
    *
    * @throws IllegalArgumentException as {@link #validate}.
    */
   public static void apply(PresenterRef ref, Map<String, Object> properties) {
      Map<String, Object> typed = convert(ref.getName(), descriptors(ref.getName()), properties);
      typed.forEach(ref::setParameter);
   }

   /**
    * The effective value of every settable property of the presenter {@code ref} builds (the
    * stored parameters over the presenter's defaults), as JSON-safe values keyed by property
    * name; null when the presenter has no settable property or cannot be built.
    */
   public static Map<String, Object> toWire(PresenterRef ref) {
      if(ref == null || ref.getName() == null) {
         return null;
      }

      try {
         PropertyDescriptor[] descs = ref.getPropertyDescriptors();

         if(descs == null) {
            return null;
         }

         Presenter presenter = ref.createPresenter();
         Map<String, Object> out = new LinkedHashMap<>();

         for(PropertyDescriptor desc : descs) {
            Kind kind = kindOf(desc);

            if(kind == Kind.UNSUPPORTED) {
               continue;
            }

            Object value = wireValue(kind, desc.getReadMethod().invoke(presenter));

            if(value != null) {
               out.put(desc.getName(), value);
            }
         }

         return out.isEmpty() ? null : out;
      }
      catch(Exception ex) {
         LOG.debug("Failed to read the properties of presenter '{}'", ref.getName(), ex);
         return null;
      }
   }

   /**
    * True when both names load the same presenter class, whichever way each is spelled (the
    * fully qualified class name, or the name relative to {@code inetsoft.report.painter}).
    */
   public static boolean samePresenter(String name, String other) {
      if(name == null || other == null) {
         return false;
      }

      if(name.equals(other)) {
         return true;
      }

      try {
         return PresenterRef.getPresenter(name).getClass() ==
            PresenterRef.getPresenter(other).getClass();
      }
      catch(Exception ex) {
         return false;
      }
   }

   private static PropertyDescriptor[] descriptors(String presenter) {
      try {
         PropertyDescriptor[] descs = new PresenterRef(presenter).getPropertyDescriptors();
         return descs == null ? new PropertyDescriptor[0] : descs;
      }
      catch(Exception ex) {
         throw new IllegalArgumentException(
            "presenterProperties: presenter '" + presenter + "' could not be loaded: " +
            ex.getMessage(), ex);
      }
   }

   private static Map<String, Object> convert(String presenter, PropertyDescriptor[] descs,
                                              Map<String, Object> properties)
   {
      Map<String, Object> typed = new LinkedHashMap<>();

      if(properties == null) {
         return typed;
      }

      for(Map.Entry<String, Object> entry : properties.entrySet()) {
         PropertyDescriptor desc = find(descs, entry.getKey());

         if(desc == null) {
            throw new IllegalArgumentException(
               "presenterProperties: '" + entry.getKey() + "' is not a property of presenter '" +
               presenter + "'. " + settable(descs));
         }

         typed.put(desc.getName(), toParameter(desc, entry.getValue()));
      }

      return typed;
   }

   private static PropertyDescriptor find(PropertyDescriptor[] descs, String name) {
      PropertyDescriptor found = null;

      for(PropertyDescriptor desc : descs) {
         if(kindOf(desc) == Kind.UNSUPPORTED) {
            continue;
         }

         if(desc.getName().equals(name)) {
            return desc;
         }

         if(desc.getName().equalsIgnoreCase(name)) {
            // a case-only difference is accepted when it names exactly one property
            found = found == null ? desc : null;
         }
      }

      return found;
   }

   private static String settable(PropertyDescriptor[] descs) {
      StringJoiner list = new StringJoiner(", ");

      for(PropertyDescriptor desc : descs) {
         Kind kind = kindOf(desc);

         if(kind != Kind.UNSUPPORTED) {
            list.add(desc.getName() + " (" + kind.label + ")");
         }
      }

      return list.length() == 0 ? "This presenter has no settable properties." :
         "Settable properties: " + list + ".";
   }

   private static Object toParameter(PropertyDescriptor desc, Object value) {
      if(value == null) {
         return null;
      }

      Kind kind = kindOf(desc);
      String name = desc.getName();

      try {
         switch(kind) {
         case BOOLEAN:
            if(value instanceof Boolean) {
               return value;
            }

            if("true".equalsIgnoreCase(String.valueOf(value))) {
               return true;
            }

            if("false".equalsIgnoreCase(String.valueOf(value))) {
               return false;
            }

            break;
         case INT:
         case LONG: {
            double num = number(value);

            if(num == Math.rint(num)) {
               if(kind == Kind.LONG) {
                  return (long) num;
               }

               if(num >= Integer.MIN_VALUE && num <= Integer.MAX_VALUE) {
                  return (int) num;
               }
            }

            break;
         }
         case DOUBLE:
            return number(value);
         case STRING:
            if(value instanceof String) {
               return value;
            }

            break;
         case COLOR:
            if(value instanceof String) {
               Color color = decodeColor(((String) value).trim());

               if(color != null) {
                  return color;
               }
            }

            break;
         case SIZE: {
            if(value instanceof Map) {
               Map<?, ?> map = (Map<?, ?>) value;
               return new Dimension((int) member(map, "width"), (int) member(map, "height"));
            }

            break;
         }
         case INSETS: {
            if(value instanceof Map) {
               Map<?, ?> map = (Map<?, ?>) value;
               return new Insets((int) member(map, "top"), (int) member(map, "left"),
                                 (int) member(map, "bottom"), (int) member(map, "right"));
            }

            break;
         }
         default:
            break;
         }
      }
      catch(IllegalArgumentException ex) {
         // fall through to the message naming the property and the expected type
      }

      throw new IllegalArgumentException(
         "presenterProperties: '" + name + "' must be " + kind.label + ", got " +
         (value instanceof String ? "\"" + value + "\"" : String.valueOf(value)) + ".");
   }

   private static double number(Object value) {
      if(value instanceof Number) {
         return ((Number) value).doubleValue();
      }

      try {
         return Double.parseDouble(String.valueOf(value).trim());
      }
      catch(NumberFormatException ex) {
         throw new IllegalArgumentException(ex);
      }
   }

   private static double member(Map<?, ?> map, String key) {
      Object value = map.get(key);

      if(value == null) {
         throw new IllegalArgumentException(key);
      }

      return number(value);
   }

   private static Object wireValue(Kind kind, Object value) {
      if(value == null) {
         return null;
      }

      switch(kind) {
      case INT:
         return ((Number) value).intValue();
      case LONG:
         return ((Number) value).longValue();
      case DOUBLE:
         return ((Number) value).doubleValue();
      case COLOR:
         return colorToString((Color) value);
      case SIZE:
         Dimension size = (Dimension) value;
         Map<String, Object> sizeMap = new LinkedHashMap<>();
         sizeMap.put("width", size.width);
         sizeMap.put("height", size.height);
         return sizeMap;
      case INSETS:
         Insets insets = (Insets) value;
         Map<String, Object> insetsMap = new LinkedHashMap<>();
         insetsMap.put("top", insets.top);
         insetsMap.put("left", insets.left);
         insetsMap.put("bottom", insets.bottom);
         insetsMap.put("right", insets.right);
         return insetsMap;
      default:
         return value;
      }
   }

   private static Kind kindOf(PropertyDescriptor desc) {
      Class<?> type = desc.getPropertyType();

      if(type == boolean.class || type == Boolean.class) {
         return Kind.BOOLEAN;
      }

      if(type == int.class || type == Integer.class || type == short.class ||
         type == Short.class || type == byte.class || type == Byte.class)
      {
         return Kind.INT;
      }

      if(type == long.class || type == Long.class) {
         return Kind.LONG;
      }

      if(type == float.class || type == double.class || Number.class.isAssignableFrom(type)) {
         return Kind.DOUBLE;
      }

      if(type == String.class) {
         return Kind.STRING;
      }

      if(Color.class.isAssignableFrom(type)) {
         return Kind.COLOR;
      }

      if(Dimension.class.isAssignableFrom(type)) {
         return Kind.SIZE;
      }

      if(Insets.class.isAssignableFrom(type)) {
         return Kind.INSETS;
      }

      return Kind.UNSUPPORTED;
   }

   private enum Kind {
      BOOLEAN("a boolean"),
      INT("a whole number"),
      LONG("a whole number"),
      DOUBLE("a number"),
      STRING("a string"),
      COLOR("a color string like \"#RRGGBB\""),
      SIZE("an object {width, height}"),
      INSETS("an object {top, left, bottom, right}"),
      UNSUPPORTED("not settable");

      Kind(String label) {
         this.label = label;
      }

      private final String label;
   }

   private static final Logger LOG = LoggerFactory.getLogger(PresenterPropertySupport.class);
}
