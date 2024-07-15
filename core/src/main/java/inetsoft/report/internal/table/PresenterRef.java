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
package inetsoft.report.internal.table;

import inetsoft.report.Presenter;
import inetsoft.report.bean.BeanUtil;
import inetsoft.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.beans.*;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * Presenter and its parameters.
 *
 * @version 8.0, 8/09/2005
 * @author InetSoft Technology Corp
 */
public class PresenterRef implements XMLSerializable, Serializable, Cloneable {
   /**
    * Create presenter definition. The presenter class must be set before it's
    * used.
    */
   public PresenterRef() {
   }

   /**
    * Create presenter definition.
    * @param presenter presenter name or fully quantified presenter class.
    */
   public PresenterRef(String presenter) {
      setName(presenter);
   }

   /**
    * Set the presenter class name.
    */
   public void setName(String presenter) {
      this.presenter = presenter;

      // clear cached info
      descs = null;
      presenterObj = null;
   }

   /**
    * Get the presenter class name.
    */
   public String getName() {
      return presenter;
   }

   /**
    * Set parameter value.
    */
   public void setParameter(String name, Object val) {
      if(val == null) {
         params.remove(name);
      }
      else {
         params.put(name, val);
      }
   }

   /**
    * Get parameter value.
    */
   public Object getParameter(String name) {
      return params.get(name);
   }

   /**
    * Get all parameter names.
    */
   public Enumeration getParameterNames() {
      return params.keys();
   }

   /**
    * Get the number of parameters defined in this presenter.
    */
   public int getParameterCount() {
      return params.size();
   }

   /**
    * Get the property descriptor for the presenter.
    */
   public PropertyDescriptor[] getPropertyDescriptors() throws Exception {
      if(descs == null && presenter != null && getPresenter(presenter) != null) {
         Presenter pr = getPresenter(presenter);
         Class stopCls = pr.getClass().getSuperclass();

         // find the first non-presenter as the stop class
         while(Presenter.class.isAssignableFrom(stopCls)) {
            stopCls = stopCls.getSuperclass();
         }

         BeanInfo info = Introspector.getBeanInfo(pr.getClass(), stopCls);

         descs = info.getPropertyDescriptors();
         int cnt = 0;

         // only read/write properties are included
         for(int i = 0; i < descs.length; i++) {
            if(descs[i].getPropertyType() == Font.class) {
               continue;
            }

            if("background".equals(descs[i].getName())) {
               continue;
            }

            if(descs[i].getWriteMethod() != null && descs[i].getReadMethod() != null) {
               if(cnt == i) {
                  cnt++;
               }
               else {
                  descs[cnt++] = descs[i];
               }
            }
         }

         if(cnt < descs.length) {
            PropertyDescriptor[] arr = new PropertyDescriptor[cnt];

            System.arraycopy(descs, 0, arr, 0, cnt);
            descs = arr;
         }
      }

      return descs;
   }

   /**
    * Create a presenter instance from a presenter name or class.
    */
   public static Presenter getPresenter(String presenter) throws Exception {
      if(presenter == null || Catalog.getCatalog().getString("(none)").equals(presenter)) {
         return null;
      }

      try {
         return (Presenter) Class.forName("inetsoft.report.painter." + presenter).newInstance();
      }
      catch(Throwable e) {
      }

      try {
         return (Presenter) Class.forName(presenter).newInstance();
      }
      catch(Throwable e) {
         throw new RuntimeException("Presenter class not found: " + presenter);
      }
   }

   /**
    * Create a presenter object.
    */
   public Presenter createPresenter() throws Exception {
      PreConverter converter = CONVERTER.get();
      Presenter pr = getPresenter(presenter);

      if(params.size() > 0) {
         PropertyDescriptor[] props = getPropertyDescriptors();

         for(int i = 0; i < props.length; i++) {
            String name = props[i].getName();
            Object val = getParameter(name);

            if(val != null) {
               Method setter = props[i].getWriteMethod();

               if(setter == null) {
                  throw new RuntimeException("Setter missing for property: " + name);
               }

               if(converter != null) {
                  val = converter.convert(val, props[i].getPropertyType());
               }

               val = inetsoft.report.script.PropertyDescriptor.convert(
                  val, props[i].getPropertyType());
               setter.invoke(pr, new Object[] {val});
            }
         }
      }

      return pr;
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      try {
         PresenterRef ref = (PresenterRef) super.clone();
         ref.params = (Hashtable) params.clone();
         return ref;
      }
      catch(CloneNotSupportedException ex) {
      }

      return this;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof PresenterRef)) {
         return false;
      }

      PresenterRef ref = (PresenterRef) obj;

      return Tool.equals(presenter, ref.presenter) && params.equals(ref.params);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<presenter ");

      if(presenter != null) {
         writer.print(" name=\"" + presenter + "\"");
      }

      writer.println(">");

      Enumeration names = getParameterNames();

      while(names.hasMoreElements()) {
         String name = (String) names.nextElement();
         Object val = getParameter(name);

         BeanUtil.writePropertyValue(writer, "presenterParameter", name, val);
      }

      writer.println("</presenter>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      presenter = Tool.getAttribute(tag, "name");

      NodeList list = tag.getElementsByTagName("presenterParameter");

      for(int i = 0; i < list.getLength(); i++) {
         Object[] pair = BeanUtil.readPropertyValue((Element) list.item(i));

         setParameter((String) pair[0], pair[1]);
      }
   }

   public static interface PreConverter {
      public Object convert(Object obj, Class type);
   }

   public String toString() {
      return super.toString() + "(" + presenter + ")";
   }

   public static final ThreadLocal<PreConverter> CONVERTER = new ThreadLocal<>();
   private String presenter; // presenter class name
   private Hashtable params = new Hashtable(); // parameter and values
   private transient PropertyDescriptor[] descs;
   private transient Presenter presenterObj;
}
