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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.internal.OKLab;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.util.CoreTool;
import inetsoft.util.DefaultComparator;
import inetsoft.util.css.*;
import inetsoft.util.script.JavaScriptEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * This class defines a color frame for categorical values. Each value is
 * assigned a distinct color.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=CategoricalColorFrame")
public class CategoricalColorFrame extends ColorFrame implements CategoricalFrame {
   /**
    * The color palette.
    */
   public static final Color[] COLOR_PALETTE = new Color[] {
      new Color(0x518db9), new Color(0xb9dbf4), new Color(0x62a640),
      new Color(0xade095), new Color(0xfc8f2a), new Color(0xfde3a7),
      new Color(0xd64541), new Color(0xfda7a5), new Color(0x9368be),
      new Color(0xbe90d4), new Color(0x95a5a6), new Color(0xdadfe1),
      new Color(0x19b5fe), new Color(0xc5eff7), new Color(0x869530),
      new Color(0xc8d96f), new Color(0xa88637), new Color(0xd2b267),
      new Color(0x019875), new Color(0x68c3a3),
      new Color(0x99CCFF), new Color(0x999933), new Color(0xCC9933),
      new Color(0x006666), new Color(0x993300), new Color(0x666666),
      new Color(0x663366), new Color(0xCCCCCC), new Color(0x669999),
      new Color(0xCCCC66), new Color(0xCC6600), new Color(0x9999FF),
      new Color(0x0066CC), new Color(0xFFCC00), new Color(0x009999),
      new Color(0x99CC33), new Color(0xFF9900), new Color(0x66CCCC),
      new Color(0x339966), new Color(0xCCCC33)};

   /**
    * Create a color frame for categorical values.
    */
   public CategoricalColorFrame() {
      super();

      defaultColors = new ArrayList<>(Arrays.asList(COLOR_PALETTE));
   }

   /**
    * Create a color frame.
    * @param field field to get value to map to color.
    */
   @TernConstructor
   public CategoricalColorFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Initialize the categorical color frame with categorical values.
    */
   public void init(Object... vals) {
      CategoricalScale scale = new CategoricalScale();
      scale.init(vals);
      setScale(scale);
   }

   /**
    * Initialize the categorical color frame with categorical values and colors.
    * The value and color array must have identical length, and each value is
    * assigned the color from the color array at the same position.
    */
   public void init(Object[] vals, Color[] clrs) {
      this.defaultColors = new ArrayList<>(Arrays.asList(clrs));
      CategoricalScale scale = new CategoricalScale();
      scale.init(vals);
      setScale(scale);
   }

   /**
    * Initialize the categorical color frame with categorical values. The values
    * in the field column is used to obtain a distinct value list.
    */
   @Override
   public void init(DataSet data) {
      if(getField() == null) {
         init(getAllHeaders(data), defaultColors);
         return;
      }

      createScale(data);
      updateCSSColors();
      clearUsedColors();
   }

   // clear unassigned and unused color information. this needs to be called whenever
   // any change is made that causes the cached information to be out of sync.
   private void clearUsedColors() {
      // if sharing color across multiple chart, different charts may be different values
      // even if they are bound to the same column (e.g. due to topN). we remember what
      // colors have already been assigned, and skip them later when we find a color that
      // has not been assigned yet. this way we won't have different values assigned to
      // same color.
      // VSFrameVisitor.syncColors is called regardless of isShareColors(), so the
      // usedIdx should be updated to avoid duplicate color whether shared or not. (52385)
      //if(isShareColors()) {

      unusedLock.lock();

      // clear and create on demand
      try {
         unassignedScale = null;
         unusedColors = null;
      }
      finally {
         unusedLock.unlock();
      }
   }

   /**
    * Get the color for the chart object.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public Color getColor(DataSet data, String col, int row) {
      Object negval = null;
      Object val = null;

      if(getField() != null) {
         negval = val = data.getData(getField(), row);
      }
      else {
         val = col;

         if(negcolors.size() > 0 && data.indexOfHeader(col) >= 0) {
            negval = data.getData(col, row);
         }
      }

      if(negval instanceof Number && ((Number) negval).doubleValue() < 0) {
         return getColor(val, true);
      }

      return getColor(val);
   }

   /**
    * Get the color for the specified value.
    */
   @Override
   @TernMethod
   public Color getColor(Object val) {
      return getColor(val, false);
   }

   // @param val the categorical value
   // @param negative true if use negative color
   private Color getColor(Object val, boolean negative) {
      Color color = null;

      if(cmap.size() > 0) {
         Object formatted = formatValue(val);
         color = cmap.get(GTool.toString(val));

         if(color == null && formatted != val) {
            color = cmap.get(formatted);
         }
      }

      if(color != null) {
         return color;
      }

      Scale scale = getScale();

      if(scale == null) {
         return defaultColor;
      }

      // ignore the explicitly assigned values since the idx will be used to fetch value
      // from the unused colors.
      Scale unassignedScale = updateUnassignedScale();
      double idx = mapValue(val, unassignedScale);
      double oidx = mapValue(val, scale);

      return Double.isNaN(idx) ? process(defaultColor, getBrightness())
         : process(getColor((int) idx, (int) oidx, negative, true), getBrightness());
   }

   private static double mapValue(Object val, Scale scale) {
      double idx = scale.map(val);

      if(Double.isNaN(idx)) {
         idx = scale.map(GTool.toString(val));
      }

      return idx;
   }

   /**
    * Set the color to be used if the value is not found in the categorical values.
    */
   @TernMethod
   public void setDefaultColor(Color color) {
      this.defaultColor = color;
   }

   /**
    * Get the color to be used if the value is not found in the categorical values.
    */
   @TernMethod
   public Color getDefaultColor() {
      return defaultColor;
   }

   @Override
   @TernMethod
   public Set<Object> getStaticValues() {
      return cmap.keySet();
   }

   @Override
   @TernMethod
   public void clearStatic() {
      cmap.clear();
      clearUsedColors();
      scripted.clear();
      derived.clear();
   }

   /**
    * Set the color for the specified value.
    */
   @TernMethod
   public void setColor(Object val, Color color) {
      if(color != null) {
         cmap.put(GTool.toString(val), color);
         // an explicit assignment supersedes one the render derived from the palette
         derived.remove(GTool.toString(val));

         if(JavaScriptEngine.isScriptThread()) {
            scripted.add(GTool.toString(val));
         }
      }
      else {
         cmap.remove(GTool.toString(val));
         scripted.remove(GTool.toString(val));
         derived.remove(GTool.toString(val));
      }

      clearUsedColors();
   }

   /**
    * Assign the color a render derived from the palette for this value. Behaves as setColor for
    * every lookup, but is not serialized: a derived color must follow the palette, and freezing it
    * per value would keep the old colors after the palette changes.
    */
   @TernMethod
   public void setDerivedColor(Object val, Color color) {
      setColor(val, color);

      if(color != null) {
         derived.add(GTool.toString(val));
      }
   }

   /**
    * Whether this value's color was derived from the palette by a render rather than assigned.
    */
   @TernMethod
   public boolean isDerived(Object val) {
      return derived.contains(GTool.toString(val));
   }

   /**
    * Drop the per-value colors a render derived from the palette, keeping the ones somebody
    * assigned. Call this when the palette changes: a per-value color takes precedence over the
    * palette, so a derived one left behind keeps rendering the old color for the rest of the
    * session. The next render derives them again from the new palette.
    */
   @TernMethod
   public void clearDerivedColors() {
      if(derived.isEmpty()) {
         return;
      }

      cmap.keySet().removeAll(derived);
      derived.clear();
      clearUsedColors();
   }

   @Override
   @TernMethod
   public boolean isStatic(Object val) {
      return cmap.get(GTool.toString(val)) != null || cmap.get(formatValue(val)) != null;
   }

   @Override
   @TernMethod
   public boolean isScripted(Object val) {
      return scripted.contains(GTool.toString(val));
   }

   /**
    * Get the color at the specified index in the palette.
    */
   @TernMethod
   public Color getColor(int index) {
      return getColor(index, index, false, false);
   }

   /**
    * @param index index in the scale. if unassigned is true, the index is the index in
    *              unassigned scale/values.
    * @param oindex the index of the value in the scale, regardless of unassigned parameter.
    * @param unassigned true to use the unused colors.
    */
   private Color getColor(int index, int oindex, boolean negative, boolean unassigned) {
      Color color = userColors.get(negative ? -(oindex + 1) : oindex);

      if(parentParams != null && color == null) {
         color = cssColors.get(negative ? -(oindex + 1) : oindex);
      }

      if(color == null) {
         if(negative && negcolors.size() > 0) {
            color = negcolors.get(index % negcolors.size());
         }
         else {
            List<Color> colors = unassigned ? updateUnusedColors() : defaultColors;
            color = colors.get(index % colors.size());
         }
      }

      return color;
   }

   // return a scale with the explicitly assigned values removed.
   private Scale updateUnassignedScale() {
      unusedLock.lock();

      try {
         if(unassignedScale == null) {
            updateUsedColors();
         }

         return unassignedScale != null ? unassignedScale : getScale();
      }
      finally {
         unusedLock.unlock();
      }
   }

   // return a list of colors with explicitly assigned colors removed.
   private List<Color> updateUnusedColors() {
      unusedLock.lock();

      try {
         if(unusedColors == null) {
            updateUsedColors();
         }

         return unusedColors != null && !unusedColors.isEmpty() ? unusedColors : defaultColors;
      }
      finally {
         unusedLock.unlock();
      }
   }

   // update the values and colors with explicit assignment in cmap.
   private void updateUsedColors() {
      Scale scale = getScale();

      if(scale instanceof CategoricalScale) {
         List values = new ArrayList(Arrays.asList(scale.getValues()));
         ArrayList<Color> colors = new ArrayList<>(defaultColors);

         for(Map.Entry<Object, Color> entry : cmap.entrySet()) {
            values.remove(entry.getKey());
            colors.remove(entry.getValue());
         }

         this.unassignedScale = (CategoricalScale) scale.clone();
         unassignedScale.setValues(values.toArray());
         this.unusedColors = colors;
      }
   }

   public void clearUserColors() {
      this.userColors.clear();
   }

   /**
    * Set or append the color at the specified index.
    */
   @TernMethod
   public void setColor(int index, Color color) {
      setUserColor(index, color);
   }

   /**
    * Set or append the color at the specified index.
    * @hidden
    */
   @TernMethod
   public void setDefaultColor(int index, Color color) {
      if(defaultColors != null) {
         int size = defaultColors.size();

         if(index >= 0) {
            if(index < size) {
               defaultColors.set(index, color);
            }
            else {
               // Add default until we reach size.
               while(index > defaultColors.size()) {
                  defaultColors.add(COLOR_PALETTE[0]);
               }

               defaultColors.add(color);
            }
         }

         clearUsedColors();
      }
   }

   /**
    * Set the user color at the specified index.
    */
   @TernMethod
   public void setUserColor(int index, Color color) {
      userColors.put(index, color);
   }

   /**
    * Get the user color at the specified index.
    */
   @TernMethod
   public Color getUserColor(int index) {
      return userColors.get(index);
   }

   /**
    * Get the number of colors in the frame.
    */
   @TernMethod
   public int getColorCount() {
      return defaultColors.size();
   }

   /**
    * @hidden
    */
   public Color getDefaultColor(int index) {
      return defaultColors.get(index);
   }

   /**
    * The soft companion of the base at this index: the same hue, receding toward the surface.
    * Light lifts toward the canvas; dark deepens instead, and holds chroma because low chroma and
    * low lightness disappear together on a dark surface. The darkest member of a set (the anchor)
    * is the exception in both modes - it has nowhere to deepen to, so it is sent to a fixed
    * lightness. The lightest members are the mirror of that exception, and only in light mode: a
    * lift would land past white, where no chroma is in gamut and the companion comes back as the
    * canvas itself. They recede by deepening instead. Returns null when the index carries no base
    * colour.
    */
   public Color getCompanionColor(int index, boolean dark) {
      if(index < 0 || defaultColors == null || index >= defaultColors.size()) {
         return null;
      }

      Color base = defaultColors.get(index);

      if(base == null) {
         return null;
      }

      double[] lch = OKLab.toLCH(OKLab.fromColor(base));
      double l = lch[0];
      double c = lch[1];
      double h = lch[2];
      // the anchor sits at a different lightness in each mode, so the threshold does too
      boolean anchor = dark ? l < DARK_ANCHOR_MAX_L : l < LIGHT_ANCHOR_MAX_L;

      if(dark) {
         return anchor ? OKLab.toColorInGamut(0.64, c * 0.55, h)
            : OKLab.toColorInGamut(l - 0.26, c * 1.03, h);
      }

      if(anchor) {
         return OKLab.toColorInGamut(0.855, c * 0.45, h);
      }

      double lifted = l + 0.14;

      // too light to lift: the target sits so close to white that toColorInGamut, which may only
      // reduce chroma, answers with white. deepen and desaturate instead, which is what the
      // designer did by hand for the one head colour that reaches this
      if(lifted > LIGHT_MAX_L) {
         return OKLab.toColorInGamut(0.78, c * 0.30, h);
      }

      return OKLab.toColorInGamut(lifted, c * 0.40, h);
   }

   /**
    * A band fill frame stepping from a resolved companion toward its base. Band 0 is the companion
    * itself; further bands walk lightness toward the base without reaching it, so depth reads as
    * depth and no band collides with the series colour. The companion is resolved by the caller
    * (VSChartPaletteDefaults.companionColor) so an authored override wins over the rule.
    */
   public static CategoricalColorFrame companionBands(Color base, Color companion, int bands) {
      if(base == null || companion == null) {
         return null;
      }

      double[] companionLCH = OKLab.toLCH(OKLab.fromColor(companion));
      double baseL = OKLab.toLCH(OKLab.fromColor(base))[0];
      double span = companionLCH[0] - baseL;
      int count = Math.max(1, bands);
      CategoricalColorFrame frame = new CategoricalColorFrame();

      for(int i = 0; i < count; i++) {
         double l = companionLCH[0] - i * span / count;
         frame.setDefaultColor(i, OKLab.toColorInGamut(l, companionLCH[1], companionLCH[2]));
      }

      return frame;
   }

   /**
    * @hidden
    */
   public Map<Integer, Color> getUserColors() {
      return userColors;
   }

   /**
    * @hidden
    */
   public void setUserColors(Map<Integer, Color> userColors) {
      this.userColors = userColors;
   }

   /**
    * @hidden
    */
   public Map<Integer, Color> getCSSColors() {
      return cssColors;
   }

   /**
    * @hidden
    */
   public void setCSSColors(Map<Integer, Color> cssColors) {
      this.cssColors = cssColors;
   }

   /**
    * Set the default color palette.
    */
   @TernMethod
   public void setDefaultColors(Color ...defaultColors) {
      this.defaultColors = new ArrayList<>(Arrays.asList(defaultColors));
      // invalidate the derived unusedColors/unassignedScale caches so the new palette
      // takes effect, matching setDefaultColor(int, Color)
      clearUsedColors();
   }

   /**
    * Set the color palette to use for negative values. If the categorical
    * values used in this frame is for measure column names, the measure
    * values are used to check whether negative color should be applied.
    */
   @TernMethod
   public void setNegativeColors(Color ...clrs) {
      this.negcolors = new ArrayList<>(Arrays.asList(clrs));
   }

   /**
    * Updates css colors for this frame.
    */
   @Override
   protected void updateCSSColors() {
      // If no parent parameter is set then don't get css colors for this frame.
      // Default palettes that can be chosen from palette dialog have no parent
      // and thus are not styleable.
      if(parentParams != null) {
         cssColors = new HashMap<>();
         CSSDictionary cssDictionary = getCSSDictionary();
         Scale scale = getScale();
         Object[] vals = scale == null ? new Object[0] : scale.getValues();

         for(int i = 0; i < getColorCount(); i++) {
            CSSParameter cssParam = new CSSParameter(CSSConstants.CHART_PALETTE, null, null,
                                                     new CSSAttr("index", i + 1 + ""));
            Color color = cssDictionary.getForeground(CSSParameter.getAllCSSParams(parentParams, cssParam));

            if(color != null) {
               cssColors.put(i, color);

               // clear color set by sync so css color would apply
               if(i < vals.length) {
                  if(cmap.remove(GTool.toString(vals[i])) == null) {
                     cmap.remove(formatValue(vals[i]));
                  }
               }
            }
         }
      }
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      CategoricalColorFrame frame2 = (CategoricalColorFrame) obj;
      ArrayList<Color> defaultColors2 = frame2.defaultColors;

      if(useGlobal != frame2.useGlobal) {
         return false;
      }

      if(shareColors != frame2.shareColors) {
         return false;
      }

      if(defaultColors.size() != defaultColors2.size()) {
         return false;
      }

      for(int i = 0; i < defaultColors.size(); i++) {
         if(!CoreTool.equals(defaultColors.get(i), defaultColors2.get(i))) {
            return false;
         }
      }

      if(!cssColors.equals(frame2.cssColors)) {
         return false;
      }

      if(!userColors.equals(frame2.userColors)) {
         return false;
      }

      if(!negcolors.equals(frame2.negcolors)) {
         return false;
      }

      if(!scripted.equals(frame2.scripted)) {
         return false;
      }

      return cmap.equals(((CategoricalColorFrame) obj).cmap);
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         CategoricalColorFrame frame = (CategoricalColorFrame) super.clone();
         frame.defaultColors = (ArrayList<Color>) defaultColors.clone();
         frame.cssColors = new HashMap<>(cssColors);
         frame.userColors = new HashMap<>(userColors);
         frame.cmap = new LinkedHashMap<>(cmap);
         frame.scripted = new HashSet<>(scripted);
         frame.derived = new HashSet<>(derived);
         frame.useGlobal = useGlobal;
         frame.shareColors = shareColors;
         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone color frame", ex);
         return null;
      }
   }

   /**
    * @return true if this frame should use the mapped colors from the viewsheet as
    * indicated in the color mapping dialog
    */
   @TernMethod
   public boolean isUseGlobal() {
      return useGlobal;
   }

   @TernMethod
   public void setUseGlobal(boolean useGlobal) {
      this.useGlobal = useGlobal;
   }

   /**
    * @return true if the frame should share/inherit from other CategoricalColorFrames on the
    * viewsheet with the same DataRef name
    */
   @TernMethod
   public boolean isShareColors() {
      return shareColors;
   }

   @TernMethod
   public void setShareColors(boolean shareColors) {
      this.shareColors = shareColors;
   }

   @Override
   @TernMethod
   public String getUniqueId() {
      String id = super.getUniqueId();

      if(!userColors.isEmpty()) {
         id += "u:" + new TreeMap(userColors);
      }

      if(!cmap.isEmpty()) {
         Object[] vals = getValues();
         Arrays.sort(vals, new DefaultComparator());
         // only include colors in the current legend (and ignore color assignments that
         // are not used in the legend. (57016)
         id += "c:" + Arrays.stream(vals)
            .map(v -> v + ":" + cmap.get(v) + (scripted.contains(v) ? "@s" : ""))
            .collect(Collectors.toList());
      }

      return id;
   }

   private ArrayList<Color> defaultColors;
   private Map<Integer, Color> cssColors = new HashMap<>();
   private Map<Integer, Color> userColors = new HashMap<>();
   private Map<Object, Color> cmap = new LinkedHashMap<>();
   private Set<Object> scripted = new HashSet<>(1);
   private Set<Object> derived = new HashSet<>(1);
   private Color defaultColor = null;
   private ArrayList<Color> negcolors = new ArrayList<>();
   private boolean useGlobal = true;
   private boolean shareColors = true;
   private transient CategoricalScale unassignedScale;
   private transient ArrayList<Color> unusedColors;
   private ReentrantLock unusedLock = new ReentrantLock();
   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(CategoricalColorFrame.class);
   private static final double LIGHT_ANCHOR_MAX_L = 0.40;
   private static final double DARK_ANCHOR_MAX_L = 0.50;
   // a companion lighter than this is indistinguishable from the canvas. expressed as a ceiling on
   // the lifted result rather than on the base, so it says what it is defending against: Amber, the
   // lightest head colour, lifts to 0.953 and stays on the lift rule by design
   private static final double LIGHT_MAX_L = 0.96;
}
