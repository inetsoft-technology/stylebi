/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.json;

import inetsoft.uql.tabular.RestParameter;

import java.util.Objects;

public class TemplateComponent {
   public TemplateComponent(String value) {
      this.variable = false;
      this.required = false;
      this.split = false;
      this.value = value;
      this.prefix = null;
      this.extension = null;
   }

   public TemplateComponent(RestParameter parameter) {
      this(parameter, null, null);
   }

   public TemplateComponent(RestParameter parameter, String prefix, String extension) {
      this.variable = true;
      this.required = parameter.isRequired();
      this.split = parameter.isSplit();
      this.value = parameter.getName();
      this.prefix = prefix;
      this.extension = extension;
   }

   TemplateComponent(boolean variable, boolean required, boolean split, String value,
                     String prefix, String extension)
   {
      this.variable = variable;
      this.required = required;
      this.split = split;
      this.value = value;
      this.prefix = prefix;
      this.extension = extension;
   }

   public boolean isVariable() {
      return variable;
   }

   public boolean isRequired() {
      return required;
   }

   public boolean isSplit() {
      return split;
   }

   public String getValue() {
      return value;
   }

   public String getPrefix() {
      return prefix;
   }

   public String getExtension() {
      return extension;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof TemplateComponent)) {
         return false;
      }

      TemplateComponent that = (TemplateComponent) o;
      return variable == that.variable &&
         required == that.required &&
         split == that.split &&
         Objects.equals(value, that.value) &&
         Objects.equals(prefix, that.prefix) &&
         Objects.equals(extension, that.extension);
   }

   @Override
   public int hashCode() {
      return Objects.hash(variable, required, split, value, prefix, extension);
   }

   @Override
   public String toString() {
      return "TemplateComponent{" +
         "variable=" + variable +
         ", required=" + required +
         ", split=" + split +
         ", value='" + value + '\'' +
         ", prefix='" + prefix + '\'' +
         ", extension='" + extension + '\'' +
         '}';
   }

   private final boolean variable;
   private final boolean required;
   private final boolean split;
   private final String value;
   private final String prefix;
   private final String extension;
}
