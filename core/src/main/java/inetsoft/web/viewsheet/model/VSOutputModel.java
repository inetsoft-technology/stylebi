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
package inetsoft.web.viewsheet.model;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;

import java.text.MessageFormat;
import java.text.*;
import java.util.*;

public abstract class VSOutputModel<T extends OutputVSAssembly> extends VSObjectModel<T> {
   VSOutputModel(T assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      OutputVSAssemblyInfo info = (OutputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      final ScalarBindingInfo binfo = info.getScalarBindingInfo();
      defaultAnnotationContent = "";
      Object value = info.getValue();

      if(value instanceof String) {
         value = Tool.isEmptyString((String) value) ? null : value;
      }

      Format initialFormat = getInitialFormat(info);
      String formattedVal = Tool.toString(value);

      if(initialFormat != null && value != null) {
         try{
            formattedVal = initialFormat.format(value);
         }
         catch(IllegalArgumentException ex) {
            // ignore if not valid format
         }
      }

      if(binfo != null && value != null) {
         String binding = VSUtil.getBindingDescription(info, rvs);
         defaultAnnotationContent = Tool.isEmptyString(binding) || Tool.isEmptyString(formattedVal) ?
            defaultAnnotationContent : binding + ": " + formattedVal;
      }

      String onClick = info instanceof ClickableOutputVSAssemblyInfo
         ? ((ClickableOutputVSAssemblyInfo) info).getOnClick() : null;
      hasOnClick = onClick != null && !onClick.isEmpty();
      emptyBinding = binfo == null || binfo.isEmpty();

      tooltipVisible = info.isTooltipVisible();

      if(!Tool.isEmptyString(info.getCustomTooltipString())) {
         value = value == null ? "" : value;
         String tipStr = info.getCustomTooltipString();
         String[] dataRefList = VSUtil.getDataRefList(info, rvs);
         tipStr = VSUtil.convertTipFormatToNumeric(tipStr, Arrays.asList(dataRefList.clone()));
         MessageFormat fmt = getFormat(tipStr);

         if(fmt != null) {
            Object[] fmts = Arrays.stream(fmt.getFormats()).filter(Objects::nonNull).toArray();

            if(fmts.length > 0) {
               String tempTip = tipStr.replace("{0}", formattedVal);
               MessageFormat tempFmt = getFormat(tempTip);
               tipStr = tempFmt.format(new Object[] {value});
            }
            else {
               tipStr = fmt.format(new Object[] {formattedVal});
            }
         }

         customTooltipString = tipStr;
      }
   }

   private MessageFormat getFormat(String pattern) {
      if(Tool.isEmptyString(pattern)) {
         return null;
      }

      Locale locale = Catalog.getCatalog().getLocale();

      if(locale == null) {
         locale = Locale.getDefault();
      }

      MessageFormat fmt = null;

      try{
         fmt = new MessageFormat(pattern, locale);
         int i = 0;

         for(Format fm : fmt.getFormats()) {
            if(fm instanceof DecimalFormat) {
               String subpattern = ((DecimalFormat)fm).toPattern();
               fmt.setFormat(i, new ExtendedDecimalFormat(subpattern));
            }
            else if(fm instanceof SimpleDateFormat) {
               String subpattern = ((SimpleDateFormat)fm).toPattern();
               fmt.setFormat(i, new ExtendedDateFormat(subpattern));
            }

            i++;
         }
      }
      catch(IllegalArgumentException ex) {
         // ignore if not valid format
      }

      return fmt;
   }

   private Format getInitialFormat(OutputVSAssemblyInfo info) {
      if(info == null) {
         return null;
      }

      VSCompositeFormat vfmt = info.getFormat();
      Locale locale = Catalog.getCatalog().getLocale();
      locale = locale == null ? Locale.getDefault() : locale;
      Format dfmt = info.getDefaultFormat();
      Format fmt = vfmt == null ? null :
         TableFormat.getFormat(vfmt.getFormat(), vfmt.getFormatExtent(), locale);
      fmt = fmt == null ? dfmt : fmt;

      return fmt;
   }

   public String getDefaultAnnotationContent() {
      return defaultAnnotationContent;
   }

   public boolean isHasOnClick() {
      return hasOnClick;
   }

   public boolean isEmptyBinding() {
      return emptyBinding;
   }

   public void setEmptyBinding(boolean emptyBinding) {
      this.emptyBinding = emptyBinding;
   }

   public boolean isTooltipVisible() {
      return tooltipVisible;
   }

   public void setTooltipVisible(boolean tooltipVisible) {
      this.tooltipVisible = tooltipVisible;
   }

   public String getCustomTooltipString() {
      return customTooltipString;
   }

   public void setCustomTooltipString(String customTooltipString) {
      this.customTooltipString = customTooltipString;
   }

   private String defaultAnnotationContent;
   private boolean hasOnClick;
   private boolean emptyBinding;
   private boolean tooltipVisible;
   private String customTooltipString;
}
