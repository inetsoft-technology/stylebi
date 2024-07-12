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
package inetsoft.uql.viewsheet;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.*;
import java.text.Format;
import java.util.function.Function;

/**
 * SelectionValue stores basic selection value information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionValue extends VSValue {
   /**
    * Item selected state.
    */
   public static final int STATE_SELECTED = 1;
   /**
    * Item associated with other selection state.
    */
   public static final int STATE_INCLUDED = 2;
   /**
    * Item unassociated with other selection state.
    */
   public static final int STATE_EXCLUDED = 4;
   /**
    * Item is compatible with other selections.
    */
   public static final int STATE_COMPATIBLE = 8;

   /**
    * Constructor.
    */
   public SelectionValue() {
      super();
   }

   /**
    * Constructor.
    */
   public SelectionValue(String label, String value) {
      super(label, value);
      this.originalLabel = label;
   }

   /**
    * Get the state of selection value.
    * @return the state of selection value.
    */
   public int getState() {
      return state;
   }

   /**
    * Set the state of selection value.
    * @param state the state of selection value.
    */
   public void setState(int state) {
      this.state = (short) state;
   }

   /**
    * Set whether is selected.
    * @param selected <tt>true</tt> if selected, <tt>false</tt> otherwise.
    */
   public void setSelected(boolean selected) {
      if(selected) {
         state = (short) (state | STATE_SELECTED);
      }
      else {
         state = (short) (state & ~STATE_SELECTED);
      }
   }

   /**
    * Check if is selected.
    * @return <tt>true</tt> if selected, <tt>false</tt> otherwise.
    */
   public boolean isSelected() {
      return (state & STATE_SELECTED) != 0;
   }

   /**
    * Set whether this value is excluded.
    */
   public void setExcluded(boolean excluded) {
      if(excluded) {
         state = (short) (state | STATE_EXCLUDED);
      }
      else {
         state = (short) (state & ~STATE_EXCLUDED);
      }
   }

   /**
    * Check if is excluded.
    * @return <tt>true</tt> if excluded, <tt>false</tt> otherwise.
    */
   public boolean isExcluded() {
      return (state & STATE_EXCLUDED) != 0;
   }

   /**
    * Check if is included.
    * @return <tt>true</tt> if included, <tt>false</tt> otherwise.
    */
   public boolean isIncluded() {
      return (state & STATE_INCLUDED) != 0;
   }

   /**
    * Get the level of the selection value.
    * @return the level of the selection value.
    */
   public int getLevel() {
      return this.level;
   }

   /**
    * Set the level of the selection value.
    * @param level the level of the selection value.
    */
   public void setLevel(int level) {
      this.level = (short) level;
   }

   /**
    * Get the string representation.
    * @return the string representaion.
    */
   public String toString() {
      return "SelectionValue("+ getLabel() + ", " + state + ", " + level +
         ", " + getValue() + ")";
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(DataOutputStream output, int levels,
                                SelectionList list) throws IOException
   {
      writeContents(output, levels, list, true);
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(DataOutputStream output, int levels,
                                SelectionList list, boolean containsFormat)
                                throws IOException
   {
      super.writeContents(output, levels, list, containsFormat);

      output.writeBoolean(mlabel == null);

      if(mlabel != null) {
         output.writeUTF(mlabel);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    * @param levels the number of levels of nodes to write.
    * @param list the list this value is on.
    */
   @Override
   protected void writeContents(PrintWriter writer, int levels,
                                SelectionList list)
   {
      super.writeContents(writer, levels, list);

      if(fmt != null && list == null) {
         fmt.writeXML(writer);
      }

      if(mlabel != null) {
         writer.print("<mlabel>");
         writer.print("<![CDATA[" + mlabel + "]]>");
         writer.println("</mlabel>");
      }
   }

   /**
    * Write attributes.
    */
   @Override
   protected void writeAttributes(DataOutputStream output, SelectionList list)
      throws IOException
   {
      super.writeAttributes(output, list);
      output.writeInt(state);
      output.writeInt(level);

      output.writeBoolean(fmt == null || list == null);

      if(fmt != null && list != null) {
         int idx = list.getFormatIndex(fmt, level);
         output.writeInt(idx);
      }

      output.writeDouble(mvalue);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer, SelectionList list) {
      super.writeAttributes(writer, list);

      writer.print(" state=\"" + state + "\"");
      writer.print(" level=\"" + level + "\"");
      writer.print(" mvalue=\"" + mvalue + "\"");

      if(fmt != null && list != null) {
         int idx = getFormatIndex(list);
         writer.println(" format=\"" + idx + "\"");
      }
   }

   /**
    * Get format index.
    */
   protected int getFormatIndex(SelectionList list) {
      if(fmt == null) {
         return -1;
      }

      return list.getFormatIndex(fmt, level);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String prop;

      if((prop = Tool.getAttribute(elem, "state")) != null) {
         state = (short) Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(elem, "level")) != null) {
         level = (short) Integer.parseInt(prop);
      }
   }

   /**
    * Check if equals another object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof SelectionValue)) {
         return false;
      }

      SelectionValue sval = (SelectionValue) obj;
      return state == sval.state && level == sval.level;
   }

   /**
    * Check if the value part is equal.
    * @reurn <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equalsValue(Object obj) {
      return super.equals(obj);
   }

   /**
    * Get the <tt>VSCompositeFormat</tt> of this selection value.
    * @return the <tt>VSFormat</tt> of this selection value.
    */
   public VSCompositeFormat getFormat() {
      return fmt;
   }

   /**
    * Set the <tt>VSCompositeFormat</tt> to this selection value.
    * @param fmt the specified <tt>VSFormat</tt>.
    */
   public void setFormat(VSCompositeFormat fmt) {
      this.fmt = fmt;
   }

   /**
    * Check if requires reset.
    * @return <tt>true</tt> if requires reset, <tt>false</tt> otherwise.
    */
   public boolean requiresReset() {
      return isSelected() && (state & STATE_COMPATIBLE) == 0 &&
         // @by larryl, if a value is excluded, it's grayed out and not
         // included in filtering, so it should not impact other selections
         (state & STATE_EXCLUDED) == 0;
   }

   /**
    * Get the default format.
    * @return the default format.
    */
   public Format getDefaultFormat() {
      return defaultFormat;
   }

   /**
    * Set the default format
    * @param format the specified default format.
    */
   public void setDefaultFormat(Format format) {
      this.defaultFormat = format;
   }

   /**
    * Get the measure value label. This is the text or the tooltip for bar.
    */
   public String getMeasureLabel() {
      return mlabel;
   }

   /**
    * Set the measure value label. This is the text or the tooltip for bar.
    */
   public void setMeasureLabel(String label) {
      this.mlabel = label;
   }

   /**
    * Get the measure value. This is the percentage of the value within the
    * measure value range.
    */
   public double getMeasureValue() {
      return mvalue;
   }

   /**
    * Set the measuer value percentage within the range (0 - 1).
    */
   public void setMeasureValue(double value) {
      this.mvalue = value;
   }

   /**
    * Get the original Label for selection tree which mode is "Parent/Child IDs".
    * @by Sabolei, fix Bug #31310.
    * this label is only for selection tree which mode is "Parent/Child IDs",
    * because In the original logic, after multiple refreshes, the format label of selection cell
    * will be processed multiple times. this is wrong.
    * So here need a pure label
    */
   public String getOriginalLabel() {
      return this.originalLabel;
   }

   @Override
   public void setLabel(String label) {
      super.setLabel(label);

      if(this.originalLabel == null) {
         this.originalLabel = label;
      }
   }

   /**
    * Check if the string matches.
    */
   public boolean match(String str, boolean recursive) {
      return getLabel() == null || str == null ||
         getLabel().toLowerCase().contains(str.toLowerCase());
   }

   /**
    * Check if the string matches.
    */
   public boolean contains(String str, boolean recursive) {
      return Tool.equals(getValue(), str);
   }

   public static class UpperExclusiveEndValue extends SelectionValue {
      public UpperExclusiveEndValue() {
         super("End", "End");
      }
   }

   public void writeObject(Kryo kryo, Output out, Function<Format, Integer> defFmtMapper,
                           Function<VSCompositeFormat, Integer> fmtMapper) throws IOException
   {
      kryo.writeObjectOrNull(out, getLabel(), String.class);
      kryo.writeObjectOrNull(out, getValue(), String.class);
      out.writeShort(state);
      out.writeShort(level);
      kryo.writeObjectOrNull(out, mlabel, String.class);
      out.writeDouble(mvalue);
      kryo.writeObjectOrNull(out, getLabel() == originalLabel ? null : originalLabel, String.class);
      out.writeInt(defFmtMapper.apply(defaultFormat));
      out.writeInt(fmtMapper.apply(fmt));
   }

   public static SelectionValue readObject(Kryo kryo, Input inp,
                                           Function<Integer, Format> defFmtMapper,
                                           Function<Integer, VSCompositeFormat> fmtMapper)
      throws Exception
   {
      SelectionValue value = new SelectionValue(kryo.readObjectOrNull(inp, String.class),
                                                kryo.readObjectOrNull(inp, String.class));
      value.state = inp.readShort();
      value.level = inp.readShort();
      value.mlabel = kryo.readObjectOrNull(inp, String.class);
      value.mvalue = inp.readDouble();
      String olabel = kryo.readObjectOrNull(inp, String.class);

      if(olabel == null) {
         value.originalLabel = value.getLabel();
      }

      value.defaultFormat = defFmtMapper.apply(inp.readInt());
      value.fmt = fmtMapper.apply(inp.readInt());
      return value;
   }

   private short state;
   private short level;
   private String mlabel; // measure label
   private double mvalue = Float.NaN; // measure value
   private String originalLabel;
   private Format defaultFormat;
   private VSCompositeFormat fmt;
}
