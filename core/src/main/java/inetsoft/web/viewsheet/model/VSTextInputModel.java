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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSTextInputModel extends VSInputModel<TextInputVSAssembly> {
   public VSTextInputModel(TextInputVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      TextInputVSAssemblyInfo assemblyInfo =
         (TextInputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      text = assemblyInfo.getText();
      ColumnOption col = assemblyInfo.getColumnOption();

      if(col instanceof TextColumnOption) {
         TextColumnOption tcol = (TextColumnOption) col;
         pattern = tcol.getPattern();
      }
      else if(col instanceof IntegerColumnOption) {
         IntegerColumnOption icol = (IntegerColumnOption) col;
         max = Integer.toString(icol.getMax());
         min = Integer.toString(icol.getMin());
      }
      else if(col instanceof FloatColumnOption) {
         FloatColumnOption fcol = (FloatColumnOption) col;
         max = fcol.getMax();
         min = fcol.getMin();
      }
      else if(col instanceof DateColumnOption) {
         DateColumnOption dcol = (DateColumnOption) col;
         max = dcol.getMax();
         min = dcol.getMin();
      }

      option = col.getType();
      multiLine = assemblyInfo.isMultiline();
      insetStyle = assemblyInfo.isInsetStyle();

      //word wrap when multiLine is true
      assemblyInfo.getFormat().getUserDefinedFormat().setWrappingValue(multiLine);

      message = assemblyInfo.getColumnOption().getMessage();
      prompt = assemblyInfo.getToolTip() == null ? "" : assemblyInfo.getToolTip();
      defaultText = assemblyInfo.getDefaultTextValue() == null ? "" : assemblyInfo.getDefaultTextValue();
   }

   public String getText() {
      return text;
   }

   public String getPattern() {
      return pattern;
   }

   public String getMessage() {
      return message;
   }

   public String getPrompt() {
      return prompt;
   }

   public boolean getMultiLine() {
      return multiLine;
   }

   public String getOption() {
      return option;
   }

   public String getMax() {
      return max;
   }

   public String getMin() {
      return min;
   }

   public boolean getInsetStyle() {
      return insetStyle;
   }

   private String text;
   private String pattern;
   private String message;
   private String prompt;
   private String defaultText;
   private boolean multiLine;
   private String option;
   private String max;
   private String min;
   private boolean insetStyle;

   @Component
   public static final class VSTextInputModelFactory
      extends VSObjectModelFactory<TextInputVSAssembly, VSTextInputModel>
   {
      public VSTextInputModelFactory() {
         super(TextInputVSAssembly.class);
      }

      @Override
      public VSTextInputModel createModel(TextInputVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSTextInputModel(assembly, rvs);
      }
   }
}
