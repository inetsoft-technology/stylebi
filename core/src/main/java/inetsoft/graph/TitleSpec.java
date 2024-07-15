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
package inetsoft.graph;

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.internal.GDefaults;
import inetsoft.util.CoreTool;

import java.io.Serializable;

/**
 * This class contains the title text and formatting attributes.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=TitleSpec")
public class TitleSpec implements Cloneable, Serializable {
   /**
    * Create a title spec.
    */
   public TitleSpec() {
      spec.setFont(GDefaults.DEFAULT_TITLE_FONT);
      spec.setColor(GDefaults.DEFAULT_TITLE_COLOR);
   }

   /**
    * Get the title label text.
    */
   @TernMethod
   public String getLabel() {
      return label;
   }

   /**
    * Set the title label text.
    */
   @TernMethod
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Get the title text attributes.
    */
   @TernMethod
   public TextSpec getTextSpec() {
      return spec;
   }

   /**
    * Set the title text attributes.
    */
   @TernMethod
   public void setTextSpec(TextSpec spec) {
      this.spec = (spec == null) ? new TextSpec() : spec;
   }

   @TernMethod
   public int getLabelGap() {
      return labelGap;
   }

   @TernMethod
   public void setLabelGap(int labelGap) {
      this.labelGap = labelGap;
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof TitleSpec)) {
         return false;
      }

      TitleSpec titleSpec = (TitleSpec) obj;

      return CoreTool.equals(spec, titleSpec.spec) &&
         CoreTool.equals(label, titleSpec.label) &&
         labelGap == titleSpec.labelGap;
   }

   private TextSpec spec = new TextSpec();
   private String label = null;
   private int labelGap = 0;
   private static final long serialVersionUID = 1L;
}
