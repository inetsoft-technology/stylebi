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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo.PopLocation;
import inetsoft.util.script.JavaScriptEngine;

import java.awt.*;

/**
 * The image viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ImageVSAScriptable extends OutputVSAScriptable {
   /**
    * Create image viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public ImageVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "ImageVSA";
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) getVSAssemblyInfo();

      addProperty("highlighted", new OutputHighlightedArray(info));
      addProperty("image", "getImage", "setImage",
                  Object.class, ImageVSAScriptable.class, this);
      addProperty("popComponent", "getPopComponent", "setPopComponent",
                  String.class, getClass(), this);
      addProperty("popLocation", "getPopLocation", "setPopLocation",
                  String.class, getClass(), this);
      addProperty("maintainAspectRatio", "isMaintainAspectRatio",
                  "setMaintainAspectRatio", boolean.class,
                  info.getClass(), info);
      addProperty("scaleImage", "isScaleImage", "setScaleImage",
                  boolean.class, info.getClass(), info);
      addProperty("scale9", "getScale9", "setScale9",
                  Insets.class, info.getClass(), info);
      addProperty("animate", "isAnimateGIF", "setAnimateGIF",
              boolean.class, info.getClass(), info);
      addProperty("tile", "isTile", "setTile",
                  boolean.class, info.getClass(), info);
      addProperty("popAlpha", "getAlpha", "setAlpha", String.class,
                  info.getClass(), info);
      addProperty("imageAlpha", "getImageAlpha", "setImageAlpha", String.class,
                  info.getClass(), info);
   }

   /**
    * Get the suffix of a property, may be "" or [].
    * @param prop the property.
    */
   @Override
   public String getSuffix(Object prop) {
      if("scale9".equals(prop)) {
         return "[]";
      }

      return super.getSuffix(prop);
   }

   public Object getImage() {
      Image raw = getInfo().getRawImage();

      if(raw != null) {
         return raw;
      }

      return getInfo().getImage();
   }

   public void setImage(Object image) {
      image = JavaScriptEngine.unwrap(image);

      if(image == null) {
         getInfo().setImage(null);
         getInfo().setRawImage(null);
      }
      else if(image instanceof Image) {
         getInfo().setRawImage((Image) image);
         // place holder so it's not treated as empty
         getInfo().setImage("[rawImage]");
      }
      else {
         getInfo().setImage(image.toString());
      }
   }

   public void setImageValue(Object image) {
      setImage(image);
      image = JavaScriptEngine.unwrap(image);

      if(image != null && !(image instanceof Image)) {
         getInfo().setImageValue(image.toString());
      }
   }

   public void setPopComponent(String popView) {
      getInfo().setPopOption(ImageVSAssemblyInfo.POP_OPTION);
      getInfo().setPopComponent(popView);
   }

   public void setPopComponentValue(String popView) {
      getInfo().setPopOptionValue(ImageVSAssemblyInfo.POP_OPTION);
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
    * Get the assembly info of current image.
    */
   private ImageVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof ImageVSAssemblyInfo) {
         return (ImageVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new ImageVSAssemblyInfo();
   }
}
