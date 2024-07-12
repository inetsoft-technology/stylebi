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
package inetsoft.graph.internal;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.CompositeVisualFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.scale.Scale;

import java.text.Format;
import java.util.HashMap;
import java.util.Map;

/**
 * This class extracts all formats defined in a egraph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GraphFormats {
   public GraphFormats(EGraph egraph) {
      this.egraph = egraph;
   }

   /**
    * Get the format of the specified field.
    */
   public Format getFormat(String fld) {
      Object fmt = fmtmap.get(fld);

      if(fmt == null) {
         fmt = initFormat(fld);

         if(fmt == null) {
            fmt = "_NULL_";
         }

         fmtmap.put(fld, fmt);
      }

      return "_NULL_".equals(fmt) ? null : (Format) fmt;
   }

   /**
    * Initialize the format of the specified field.
    */
   private Format initFormat(String fld) {
      int count = egraph.getElementCount();

      for(int i = 0; i < count; i++) {
         GraphElement elem = egraph.getElement(i);

         // try element
         for(int j = 0; j < elem.getVarCount(); j++) {
            String var = elem.getVar(j);

            if(fld.equals(var)) {
               TextSpec tspec = elem.getLabelTextSpec(var);
               Format fmt = tspec == null ? null : tspec.getFormat();

               if(fmt != null) {
                  return fmt;
               }
            }
         }

         VisualFrame[] frames = elem.getVisualFrames();

         // try aesthetic
         for(int j = 0; j < frames.length; j++) {
            VisualFrame frame = frames[j];

            if(frame instanceof CompositeVisualFrame) {
               frame = ((CompositeVisualFrame) frame).getGuideFrame();
            }

            String fld2 = frame == null ? null : frame.getField();

            if(fld.equals(fld2)) {
               TextSpec tspec = frame.getLegendSpec().getTextSpec();
               Format fmt = tspec == null ? null : tspec.getFormat();

               if(fmt != null) {
                  return fmt;
               }
            }
         }
      }

      // try axis. we give lower priority to scale since it's more likely to set
      // a format at less precision on a scale than the text element. For
      // example, we may set axis to percentage (#,##0%) while set element text
      // to include decimal places (#,###.##%).
      Scale scale = egraph.getScale(fld);

      if(scale != null) {
         AxisSpec aspec = scale.getAxisSpec();
         TextSpec tspec = aspec == null ? null : aspec.getTextSpec();
         Format fmt = tspec == null ? null : tspec.getFormat();

         if(fmt != null) {
            return fmt;
         }
      }

      return null;
   }

   private EGraph egraph;
   private transient Map fmtmap = new HashMap();
}
