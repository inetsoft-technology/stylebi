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
package inetsoft.web.composer.tablestyle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.composer.tablestyle.css.CSSTableStyleModel;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TableStyleModel {
   public TableStyleModel() {
   }

   public TableStyleFormatModel getStyleFormat() {
      return styleFormat;
   }

   public void setStyleFormat(TableStyleFormatModel styleFormat) {
      this.styleFormat = styleFormat;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getStyleId() {
      return styleId;
   }

   public void setStyleId(String styleId) {
      this.styleId = styleId;
   }

   public CSSTableStyleModel getCssStyleFormat() {
      return cssFormat;
   }

   public void setCssStyleFormat(CSSTableStyleModel cssFormat) {
      this.cssFormat = cssFormat;
   }

   public String getStyleName() {
      return styleName;
   }

   public void setStyleName(String styleName) {
      this.styleName = styleName;
   }

   private TableStyleFormatModel styleFormat;
   private CSSTableStyleModel cssFormat;
   private String label;
   private String id;
   private String styleId;
   private String styleName;
}
