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
package inetsoft.web.binding;

import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.report.script.viewsheet.SelectionVSAScriptable;
import inetsoft.util.Tool;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ScriptPropertyTool {
   public static void fixPropertyLink(Scriptable scriptable, String prefix, String name,
                                      ObjectNode node)
   {
      if(getPropertyLink(scriptable, prefix, name) != null) {
         node.put("!url", getPropertyLink(scriptable, prefix, name));

         if(getPropertyType(scriptable, prefix, name) != null) {
            node.put("!type", getPropertyType(scriptable, prefix, name));
         }
      }
   }

   public static String getPropertyLink(Scriptable scriptable, String prefix, String property) {
      init();
      String cshid = null;

      // The common property maybe in chart.colorLegend.color, these types script name is null.
      if(prefix == null && isCommonProperty(property)) {
         cshid = commonProperties.getProperty(property);
      }
      else {
         if(prefix == null) {
            prefix = getPropertyPrefix(scriptable) + ".";
         }

         if(prefix == null) {
            return null;
         }

         if(property.endsWith("()")) {
            property = property.replace("()", "");
         }

         String propertyKey = prefix + property;

         if(propertyKey == null) {
            return null;
         }

         cshid = assemblyProperties.getProperty(propertyKey);
      }

      if(cshid == null) {
         return null;
      }

      String startUrl = Tool.getHelpBaseURL() + "functions/userhelp/index.html#cshid=";
      return startUrl + cshid;
   }

   public static String getPropertyType(Scriptable scriptable, String prefix, String property) {
      init();

      if(property.endsWith("()")) {
         property = property.replace("()", "");
      }

      if(typeProperties.getProperty(property) != null) {
         return typeProperties.getProperty(property);
      }

      if(prefix == null) {
         prefix = getPropertyPrefix(scriptable) + ".";
      }

      if(prefix == null) {
         return null;
      }

      String propertyKey = prefix + property;

      if(propertyKey == null) {
         return null;
      }

      return typeProperties.getProperty(propertyKey);
   }

   private static void init() {
      if(assemblyProperties == null) {
         initAssemblyProperties();
      }

      if(commonProperties == null) {
         initCommonProperties();
      }

      if(typeProperties == null) {
         initTypeProperties();
      }
   }

   private static void initAssemblyProperties() {
      assemblyProperties = new Properties();
      InputStream in = ScriptPropertyTool.class.getResourceAsStream("/inetsoft/web/binding" +
              "/assembly" + ".properties");

      if(in != null) {
         try {
            assemblyProperties.load(in);
         }
         catch(IOException e) {
            LOG.error("Failed to load assembly properties", e);
         }
         finally {
            try {
               in.close();
            }
            catch(IOException e) {
               LOG.warn("Failed to close input stream", e);
            }
         }
      }
   }

   private static void initTypeProperties() {
      typeProperties = new Properties();
      InputStream in = ScriptPropertyTool.class.getResourceAsStream("/inetsoft/web/binding" +
              "/property-type" + ".properties");

      if(in != null) {
         try {
            typeProperties.load(in);
         }
         catch(IOException e) {
            LOG.error("Failed to load assembly properties", e);
         }
         finally {
            try {
               in.close();
            }
            catch(IOException e) {
               LOG.warn("Failed to close input stream", e);
            }
         }
      }
   }

   private static void initCommonProperties() {
      commonProperties = new Properties();
      InputStream in = ScriptPropertyTool.class.getResourceAsStream("/inetsoft/web/binding/common" +
              ".properties");

      if(in != null) {
         try {
            commonProperties.load(in);
         }
         catch(IOException e) {
            LOG.error("Failed to load common properties", e);
         }
         finally {
            try {
               in.close();
            }
            catch(IOException e) {
               LOG.warn("Failed to close input stream", e);
            }
         }
      }
   }

   private static boolean isCommonProperty(String property) {
      return COMMON_PROPERTIES.contains(property);
   }

   public static String getPropertyPrefix(Scriptable scriptable) {
      if(scriptable == null) {
         return null;
      }

      String cls = scriptable.getClassName();

      if(ASSEMBLY_NAMES.get(cls) != null) {
         return ASSEMBLY_NAMES.get(cls);
      }

      // Selection list/selection tree.
      if(scriptable instanceof SelectionVSAScriptable) {
         return "Selection";
      }

      return null;
   }

   private static Properties assemblyProperties = null;
   private static Properties typeProperties = null;
   private static Properties commonProperties = null;
   private static final Map<String, String> ASSEMBLY_NAMES = new HashMap<>();
   private static final List<String> COMMON_PROPERTIES = Arrays.asList("alignment", "alpha",
           "background", "borderColors", "borders", "dataConditions", "enabled", "exportFormat",
           "font", "foreground", "format", "formatSpec", "position", "scaledPosition",
           "selectedLabel", "selectedLables", "selectedObject", "selectedObjects", "size",
           "title", "visible", "wrapping", "titleVisible");

   static {
      ASSEMBLY_NAMES.put("ViewsheetVSA", "thisViewsheet");
      ASSEMBLY_NAMES.put("CrosstabVSA", "Crosstab");
      ASSEMBLY_NAMES.put("ChartVSA", "Chart");
      ASSEMBLY_NAMES.put("TableVSA", "Table");
      ASSEMBLY_NAMES.put("FormulaTableVSA", "CalcTable");
      ASSEMBLY_NAMES.put("CalendarVSA", "Calendar");
      ASSEMBLY_NAMES.put("GaugeVSA", "Gauge");
      ASSEMBLY_NAMES.put("ImageVSA", "Image");
      ASSEMBLY_NAMES.put("RectangleVSA", "Rectangle");
      ASSEMBLY_NAMES.put("LineVSA", "Line");
      ASSEMBLY_NAMES.put("OvalVSA", "Oval");
      ASSEMBLY_NAMES.put("UploadVSA", "Upload");
      ASSEMBLY_NAMES.put("SubmitVSA", "Submit");
      ASSEMBLY_NAMES.put("SliderVSA", "Slider");
      ASSEMBLY_NAMES.put("SpinnerVSA", "Spinner");
      ASSEMBLY_NAMES.put("TextVSA", "Text");
      ASSEMBLY_NAMES.put("TextInputVSA", "TextInput");
      ASSEMBLY_NAMES.put("TabVSA", "Tab");
      ASSEMBLY_NAMES.put("CheckBoxVSA", "Field");
      ASSEMBLY_NAMES.put("RadioButtonVSA", "Field");
      ASSEMBLY_NAMES.put("ComboBoxVSA", "Field");
      ASSEMBLY_NAMES.put("RangeSliderVSA", "RangeSlider");
      ASSEMBLY_NAMES.put("VSCrosstabInfo", "Crosstab.bindingInfo");
      ASSEMBLY_NAMES.put("VSChartInfo", "Chart.bindingInfo");
      ASSEMBLY_NAMES.put("SelectionContainerVSA", "SelectionContainer");
      ASSEMBLY_NAMES.put("TabVSA", "Tab");
      // Since GroupContainer has the same properties with Image,
      // using its property link as its own.
      ASSEMBLY_NAMES.put("GroupContainerVSA", "Image");
   }

   private static final Logger LOG = LoggerFactory.getLogger(ScriptPropertyTool.class);
}
