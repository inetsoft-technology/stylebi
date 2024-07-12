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
package inetsoft.web.adhoc.model.chart;

import inetsoft.uql.XFormatInfo;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.util.ExtendedDecimalFormat;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.*;

import java.util.Objects;

public class ChartFormatAttr extends FormatInfoModel {

   public ChartFormatAttr() {
      super();
   }

   public ChartFormatAttr(CompositeTextFormat fmt) {
      if(fmt == null) {
         return;
      }

      setColor(Tool.toString(fmt.getColor()));
      setBackgroundColor(Tool.toString(fmt.getBackground()));
      XFormatInfo xfmt = fmt.getFormat();
      setFormat(xfmt.getFormat());
      setFormatSpec(xfmt.getFormatSpec());
      fixDateSpec(xfmt.getFormat(), xfmt.getFormatSpec());
      setFont(new FontInfo(fmt.getFont()));
      setAlign(new AlignmentInfo(fmt.getAlignment()));
      setDecimalFmts(ExtendedDecimalFormat.getSuffix().toArray(new String[0]));
   }

   private XFormatInfo toXFormatInfo() {
      if(getFormat() == null) {
         return null;
      }

      String format = getFormat();
      String formatSpec = getDateSpec() != null &&
         !"Custom".equals(getDateSpec()) ? getDateSpec() : getFormatSpec() != null &&
         getFormatSpec().length() > 0 ? getFormatSpec() : null;
      format = FormatInfoModel.getDurationFormat(format, isDurationPadZeros());

      return new XFormatInfo(format, formatSpec);
   }

   public CompositeTextFormat toTextFormat(CompositeTextFormat fmt) {
      if(fmt == null) {
         return null;
      }

      if(!Objects.equals(fmt.getColor(), Tool.getColorFromHexString(getColor()))) {
         fmt.setColor(Tool.getColorFromHexString(getColor()));
      }

      if(!Objects.equals(fmt.getBackground(), Tool.getColorFromHexString(getBackgroundColor()))) {
         fmt.setBackground(Tool.getColorFromHexString(getBackgroundColor()));
      }

      fmt.setFont(getFont().toFont());
      fmt.setAlignment(getAlign().toAlign());
      fmt.setFormat(toXFormatInfo());

      return fmt;
   }
}
