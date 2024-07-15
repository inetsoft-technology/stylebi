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
package inetsoft.graph.guide.form;

import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.*;
import java.util.*;

/**
 * Generates some number of target lines at specific predefined points
 */
public class DynamicLineStrategy extends TargetStrategy {
   /**
    * Convenience empty constructor
    */
   public DynamicLineStrategy() {
      this(new TargetParameter[0]);
   }

   /**
    * Constructor taking any number of doubles
    */
   public DynamicLineStrategy(TargetParameter... parameters) {
      setParameters(parameters);
   }

   /**
    * Calculates the positions of the band boundaries based on incoming
    * post-aggregate data and some number of parameters
    */
   @Override
   protected double[] getRuntimeBoundaries(double[] data) {
      int i = 0;
      double[] boundaries = new double[reorderedLines.size()];

      // Pass values directly through from stored lines
      for(TargetParameter line : reorderedLines) {
         boundaries[i++] = line.getRuntimeValue(data);
      }

      return boundaries;
   }

   @Override
   protected void reorderValues(Integer[] indices) {
      List<TargetParameter> list = new ArrayList<>();

      for(Integer index : indices) {
         list.add(lines.get(index));
      }

      reorderedLines = list;
   }

   @Override
   protected void resetValues() {
      reorderedLines = lines;
   }

   /**
    * Set the parameters list
    */
   public void setParameters(TargetParameter... parameters) {
      lines.clear();
      addParameters(parameters);
   }

   /**
    * Add parameters to the list
    */
   public void addParameters(TargetParameter... parameters) {
      for(TargetParameter tpw : parameters) {
         if(tpw != null) {
            lines.add(tpw);
         }
      }
   }

   /**
    * Generate labels for boundary lines using the line boundaries
    */
   @Override
   protected String[] generateDefaultLabels(double[] bandBoundaries,
                                            boolean dateTarget,
                                            boolean timeTarget)
   {
      // The default label for line strategy is to show the
      // target formula for each line
      return generateLabels(bandBoundaries,
                            new MessageFormat[] {new MessageFormat("{1}")},
                            null, null, dateTarget, timeTarget);
   }

   @Override
   protected String[] generateLabels(double[] bandBoundaries,
                                     MessageFormat[] labelFormats,
                                     String fieldName,
                                     Format valueFormat,
                                     boolean dateTarget,
                                     boolean timeTarget)
   {
      String[] labels;

      if(labelFormats == null) {
         labels = generateDefaultLabels(bandBoundaries, dateTarget, timeTarget);
      }
      else {
         labels = new String[bandBoundaries.length];
         Object[] arguments = new Object[3];
         arguments[2] = fieldName == null ? "" : fieldName;

         TargetParameter[] lines = new TargetParameter[reorderedLines.size()];
         lines = reorderedLines.toArray(lines);

         // Each boundary line gets a label
         for(int i = 0; i < bandBoundaries.length; i++) {
            if(bandBoundaries[i] == -Double.MAX_VALUE) {
               continue;
            }

            Object val = dateTarget ? new Date((long) bandBoundaries[i])
               : Double.valueOf(bandBoundaries[i]);
            MessageFormat labelFormat = labelFormats[sortedIndices[i] % labelFormats.length];
            Format[] fmts = labelFormat.getFormats();
            boolean messageDateFormat = Arrays.stream(fmts).anyMatch(f -> f instanceof DateFormat);

            // Use the value for this boundary
            if(valueFormat != null) {
               if(dateTarget) {
                  // don't apply the value format if the target label also has a format that
                  // requires a Date object.
                  if(messageDateFormat) {
                     arguments[0] = val;
                  }
                  else {
                     arguments[0] = format(valueFormat, val);
                  }
               }
               else {
                  arguments[0] = format(valueFormat, val);
               }
            }
            else {
               // if message format specifies a date format, don't convert to string. (52836)
               if(timeTarget && val instanceof Date && !messageDateFormat) {
                  arguments[0] = format(Tool.getTimeFormat(), val);
               }
               else {
                  arguments[0] = val;
               }
            }

            // Bug #11413 by Klause, 2016-5-9
            // if date target, show date format value.
            if(dateTarget && lines[i].getFormula() == null && valueFormat != null) {
               val = new Date((long) lines[i].getConstantValue());
               arguments[1] = format(valueFormat, val);
            }
            else {
               arguments[1] = lines[i].toString();
            }

            // sortedIndices are used so lines match correct labels
            // Format the string and add it to the list of labels
            labels[i] = generateLabel(labelFormat, arguments);
         }
      }

      return labels;
   }

   // don't throw exception for wrong format. (52630)
   private static String format(Format fmt, Object val) {
      try {
         return fmt.format(val);
      }
      catch(Exception ex) {
         if(LOG.isDebugEnabled()) {
            LOG.debug("Failed to format value: " + val, ex);
         }

         CoreTool.addUserMessage(ex.getMessage() + ": " + val);
         return CoreTool.toString(val);
      }
   }

   /**
    * Get the length of lines
    */
   public int getSize() {
      return lines.size();
   }

   private List<TargetParameter> lines = new ArrayList<>();
   private List<TargetParameter> reorderedLines = lines;

   private static final long serialVersionUID = 1L;
   private static Logger LOG = LoggerFactory.getLogger(DynamicLineStrategy.class);
}
