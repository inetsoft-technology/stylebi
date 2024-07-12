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
package inetsoft.graph.guide.form;

import inetsoft.util.Tool;

import java.io.Serializable;
import java.text.Format;
import java.text.MessageFormat;
import java.util.*;

/**
 * These classes are used as different strategies for calculating the boundaries
 * of graph target bands
 */
public abstract class TargetStrategy implements Serializable {
   /**
    * Calculates the positions of the band boundaries based on incoming
    * post-aggregate data and some number of parameters.
    * This also needs to re-arrange the dynamic values in the strategy
    * so that labels are generated properly later.
    *
    * @return A sorted(ascending) array of boundary values
    */
   public final double[] calculateBoundaries(double[] data) {
      resetValues();
      double[] boundaries = getRuntimeBoundaries(data);
      Integer[] sortedOrder = indexSort(boundaries);
      reorderValues(sortedOrder);

      // Now the input values are reordered, get boundaries again.
      boundaries = getRuntimeBoundaries(data);
      return boundaries;
   }

   /**
    * Generate the runtime boundary values in original order
    */
   protected abstract double[] getRuntimeBoundaries(double[] data);

   /**
    * Reorders the values so that labels will be generated properly
    */
   protected void reorderValues(Integer[] indices) {
      // default do nothing
   }

   /**
    * Reset the reordered information to default.
    */
   protected void resetValues() {
      // default do nothing
   }

   /**
    * Generate labels for boundary lines using the line boundaries
    */
   protected abstract String[] generateDefaultLabels(double[] bandBoundaries, boolean dateTarget,
                                                     boolean timeTarget);

   /**
    * Generates label strings for boundary lines
    *
    * @param bandBoundaries The already calculated positions of boundary lines
    * @param labelFormats The user provided template string
    * @param fieldName The name of the field
    * @return labels for the target lines.
    */
   protected String[] generateLabels(double[] bandBoundaries,
                                     MessageFormat[] labelFormats,
                                     String fieldName,
                                     Format valueFormat,
                                     boolean dateTarget,
                                     boolean timeTarget)
   {
      String[] labels;
      String[] defaultLabels = generateDefaultLabels(bandBoundaries, dateTarget, timeTarget);

      if(labelFormats == null) {
         labels = defaultLabels;
      }
      else {
         labels = new String[bandBoundaries.length];
         Object[] arguments = new Object[3];
         // Use the field name for argument {2}
         arguments[2] = fieldName == null ? "" : fieldName;

         // Each boundary line gets a label
         for(int i = 0; i < bandBoundaries.length; i++) {
            Object val = dateTarget ? new Date((long) bandBoundaries[i])
               : Double.valueOf(bandBoundaries[i]);

            // Use the value for this boundary for argument {0}
            if(valueFormat != null) {
               arguments[0] = valueFormat.format(val);
            }
            else {
               if(timeTarget && val instanceof Date) {
                  arguments[0] = Tool.getTimeFormat().format((Date) val);
               }
               else {
                  arguments[0] = val;
               }
            }

            // Use the default label for this boundary for argument {1}
            arguments[1] = defaultLabels[i];

            // Format the string and add it to the list of labels
            labels[i] = generateLabel(labelFormats[i % labelFormats.length], arguments);
         }
      }

      return labels;
   }

   /**
    * Utility class to generate a single label
    */
   static String generateLabel(MessageFormat fmt, Object[] arguments) {
      StringBuffer buffer = new StringBuffer();

      // Format the string and add it to the list of labels
      fmt.format(arguments, buffer, null);
      return buffer.toString();
   }

   private static class ArrayIndexComparator implements Comparator<Integer> {
      private double[] array;

      public ArrayIndexComparator(double[] array) {
         this.array = array;
      }

      @Override
      public int compare(Integer index1, Integer index2) {
         return Double.compare(array[index1], array[index2]);
      }
   }

   // Create array of indeces, to be sorted
   private static Integer[] createIndexArray(double[] array) {
      Integer[] indeces = new Integer[array.length];

      for(int i = 0; i < array.length; i++) {
         indeces[i] = i;
      }

      return indeces;
   }

   // Get the sorted indeces of the array
   private Integer[] indexSort(double[] arr) {
      Comparator<Integer> comp = new ArrayIndexComparator(arr);

      // Save in field so band target can use it to fix label ordering.
      sortedIndices = createIndexArray(arr);
      Arrays.sort(sortedIndices, comp); // efficient mergesort/insertion sort

      return sortedIndices;
   }

   // Reorders a linked list of dynamic values.  Used by child classes
   List<Double> reorderList(Integer[] indices, List<Double> list) {
      List<Double> newList = new ArrayList<>();

      for(Integer index : indices) {
         newList.add(list.get(index));
      }

      return newList;
   }

   // Only reorder for labels once.
   Integer[] sortedIndices = null;
}
