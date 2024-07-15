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
package inetsoft.report.script.viewsheet;

import inetsoft.report.Presenter;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.script.PropertyDescriptor;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo.PopLocation;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.util.script.JavaScriptEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.lang.reflect.Method;

/**
 * The text viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class TextVSAScriptable extends OutputVSAScriptable {
   /**
    * Create a text viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public TextVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "TextVSA";
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      OutputVSAssemblyInfo info = (OutputVSAssemblyInfo) getVSAssemblyInfo();

      addProperty("highlighted", new OutputHighlightedArray(info));

      addProperty("text", "getText", "setText", String.class,
                  info.getClass(), info);
      addProperty("wrapping", "isWrapping", "setWrapping", boolean.class,
                  getClass(), this);
      addProperty("autoSize", "isAutoSize", "setAutoSize", boolean.class,
                  info.getClass(), info);
      addProperty("scaleVertical", "isScaleVertical", "setScaleVertical", boolean.class,
                  info.getClass(), info);
      addProperty("embedAsURL", "isUrl", "setUrl", boolean.class,
                  info.getClass(), info);
      addProperty("popComponent", "getPopComponent", "setPopComponent",
                  String.class, getClass(), this);
      addProperty("popLocation", "getPopLocation", "setPopLocation",
                  String.class, getClass(), this);
      addProperty("popAlpha", "getAlpha", "setAlpha", String.class,
                  info.getClass(), info);

      try {
         addFunctionProperty(getClass(), "setPresenter", Object.class);
      }
      catch(Exception ex) {
         LOG.warn("Failed to register the text setPresenter function", ex);
      }
   }

   public void setPopComponent(String popView) {
      getInfo().setPopOption(TextVSAssemblyInfo.POP_OPTION);
      getInfo().setPopComponent(popView);
   }

   public void setPopComponentValue(String popView) {
      getInfo().setPopOptionValue(TextVSAssemblyInfo.POP_OPTION);
      getInfo().setPopComponentValue(popView);
   }

   public String getPopComponent() {
      return getInfo().getPopComponent();
   }

   public void setPopLocation(String popLocation) {

      if("CENTER".equals(popLocation)) {
         getInfo().setPopLocation(PopLocation.CENTER);
      }
      else if ("MOUSE".equals(popLocation)) {
         getInfo().setPopLocation(PopLocation.MOUSE);
      }
      else {
         throw new IllegalArgumentException("Invalid PopLocation");
      }
   }

   public String getPopLocation() { return getInfo().getPopLocation().value; }

   /**
    * Set a presenter on a cell.
    */
   public void setPresenter(Object p) {
      Object pobj = JavaScriptEngine.unwrap(p);

      try {
         pobj = JavaScriptEngine.unwrap(pobj);

         // if it is Presenter? convert to PresenterRef
         if(pobj instanceof Presenter) {
            Presenter presenter = (Presenter) pobj;
            PresenterRef pr = new PresenterRef();
            pr.setName(presenter.getClass().getName());
            Class stopCls = pr.getClass().getSuperclass();

            // find the first non-presenter as the stop class
            while(Presenter.class.isAssignableFrom(stopCls)) {
               stopCls = stopCls.getSuperclass();
            }

            BeanInfo info = Introspector.getBeanInfo(pr.getClass(), stopCls);
            java.beans.PropertyDescriptor[] descs = info.getPropertyDescriptors();

            // only read/write properties are included
            for(int i = 0; i < descs.length; i++) {
               try {
                  String name = descs[i].getName();

                  if(name == null) {
                     continue;
                  }

                  if(descs[i].getReadMethod() != null) {
                     Method m = descs[i].getReadMethod();
                     Object value = m.invoke(presenter, new Object[0]);
                     pr.setParameter(name, value);
                  }
               }
               catch(Exception ex1) {
                  // ignore it
               }
            }

            pobj = pr;
         }

         if(pobj != null && !(pobj instanceof PresenterRef)) {
            pobj = PropertyDescriptor.convert(pobj, PresenterRef.class);
         }

         getInfo().getFormat().getUserDefinedFormat().setPresenter((PresenterRef) pobj);
      }
      catch(Exception ex) {
         LOG.error("Failed to set text presenter: [" + p + "]", ex);
      }
   }

   /**
    * Get the assembly info of current text.
    */
   private TextVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof TextVSAssemblyInfo) {
         return (TextVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new TextVSAssemblyInfo();
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(TableDataVSAScriptable.class);
}
