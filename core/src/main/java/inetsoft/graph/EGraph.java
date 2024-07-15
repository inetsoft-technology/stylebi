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
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.guide.form.GraphForm;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.Scale;
import inetsoft.util.CoreTool;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Stream;

/**
 * An EGraph is a graph definition. A graph is constructed by
 * specifying the scales, coordinates, and aesthetics. If scale or coordinate
 * are not specified, a default will be created from the graph element
 * definition.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=EGraph")
public class EGraph implements Cloneable, Serializable {
   /**
    * Create an instance of EGraph.
    */
   public EGraph() {
      super();
      clear();
   }

   /**
    * Add a graph element to this graph. Element classes are defined in
    * inetsoft.graph.element package. At least one graph element must be
    * added to a graph.
    */
   @TernMethod
   public void addElement(GraphElement elem) {
      elems.add(elem);
   }

   /**
    * Get the specified graph element.
    */
   @TernMethod
   public GraphElement getElement(int idx) {
      return elems.get(idx);
   }

   /**
    * Get the number of graph elements defined in this graph.
    */
   @TernMethod
   public int getElementCount() {
      return elems.size();
   }

   /**
    * Remove the graph element at the specified position.
    */
   @TernMethod
   public void removeElement(int idx) {
      elems.remove(idx);
   }

   /**
    * Remove all graph elements.
    */
   @TernMethod
   public void clearElements() {
      elems.clear();
   }

   /**
    * Get GraphElements in a stream.
    */
   public Stream<GraphElement> stream() {
      return elems.stream();
   }

   /**
    * Set the coordinate to be used for this graph.
    */
   @TernMethod
   public void setCoordinate(Coordinate coord) {
      this.coord = coord;

      if(coord != null) {
         Scale[] scales = coord.getScales();

         for(int i = 0; i < scales.length; i++) {
            String[] flds = scales[i].getFields();

            for(int j = 0; j < flds.length; j++) {
               setScale(flds[j], scales[i]);
            }
         }
      }
   }

   /**
    * Get the coordinate to be used for this graph. If the coordinate is not
    * explicitly set, a default coordinate will be created.
    */
   @TernMethod
   public Coordinate getCoordinate() {
      return coord;
   }

   /**
    * Set the scale used for mapping values for a column.
    * @param col the column identifier of a dataset.
    * @param scale the scale applied to the column.
    */
   @TernMethod
   public void setScale(String col, Scale scale) {
      scalemap.put(col, scale);

      // make sure col is on the scale field list
      List<String> fields = Arrays.asList(scale.getFields());

      if(!fields.contains(col)) {
         fields = new ArrayList<>(fields);
         fields.add(col);
         scale.setFields(fields.toArray(new String[0]));
      }
   }

   /**
    * Get the scale used for mapping values for a column.
    * @param col the column identifier of a dataset.
    * @return scale the scale applied to the column.
    */
   @TernMethod(url = "#ProductDocs/chartAPI/html/Common_Chart_EGraph_getScale.htm?Highlight=getScale")
   public Scale getScale(String col) {
      if(scalemap.containsKey(col)) {
         return scalemap.get(col);
      }

      // @by jerry feature1369779450952, to perform a fuzzy search when
      // the exact name is not found (using "(col)")
      String partialName = "(" + col + ")";

      for(String key : scalemap.keySet()) {
         if(key.contains(partialName)) {
            return scalemap.get(key);
         }
      }

      return null;
   }

   /**
    * Add a form guide to this graph. Form classes are defined in
    * inetsoft.graph.guide.form package.
    */
   @TernMethod
   public void addForm(GraphForm form) {
      forms.add(form);
   }

   /**
    * Get the specified form guide.
    */
   @TernMethod
   public GraphForm getForm(int idx) {
      return forms.get(idx);
   }

   /**
    * Get the number of forms defined in this graph.
    */
   @TernMethod
   public int getFormCount() {
      return forms.size();
   }

   /**
    * Remove the form at the specified position.
    */
   @TernMethod
   public void removeForm(int idx) {
      forms.remove(idx);
   }

   /**
    * Remove all form guides.
    */
   @TernMethod
   public void clearForms() {
      forms.clear();
   }

   /**
    * Get the layout position option of the legends.
    */
   @TernMethod
   public int getLegendLayout() {
      return legendLayout;
   }

   /**
    * Set the layout position option for the legends.
    * @param option one of GraphConstants.TOP, LEFT, BOTTOM, RIGHT, IN_PLACE.
    */
   @TernMethod
   public void setLegendLayout(int option) {
      this.legendLayout = option;
   }

   /**
    * Get the preferred width or height of the legends.
    */
   @TernMethod
   public double getLegendPreferredSize() {
      return (legendPreferredSize == null) ? 0 : legendPreferredSize;
   }

   /**
    * Set the preferred size for the legends.
    */
   @TernMethod
   public void setLegendPreferredSize(double preferredSize) {
      this.legendPreferredSize = preferredSize;
   }

   /**
    * Set the x title specification.
    */
   @TernMethod
   public void setXTitleSpec(TitleSpec tspec) {
      this.xTitleSpec = tspec;
   }

   /**
    * Get the x title specification.
    */
   @TernMethod
   public TitleSpec getXTitleSpec() {
      return xTitleSpec;
   }

   /**
    * Set the secondary x title specification.
    */
   @TernMethod
   public void setX2TitleSpec(TitleSpec tspec) {
      this.x2TitleSpec = tspec;
   }

   /**
    * Get the secondary x title specification.
    */
   @TernMethod
   public TitleSpec getX2TitleSpec() {
      return x2TitleSpec;
   }

   /**
    * Set the y title specification.
    */
   @TernMethod
   public void setYTitleSpec(TitleSpec tspec) {
      this.yTitleSpec = tspec;
   }

   /**
    * Get the y title specification.
    */
   @TernMethod
   public TitleSpec getYTitleSpec() {
      return yTitleSpec;
   }

   /**
    * Set the secondary y title specification.
    */
   @TernMethod
   public void setY2TitleSpec(TitleSpec tspec) {
      this.y2TitleSpec = tspec;
   }

   /**
    * Get the secondary y title specification.
    */
   @TernMethod
   public TitleSpec getY2TitleSpec() {
      return y2TitleSpec;
   }

   /**
    * Check if this is the inner most facet coordinate for scatter plot matrix.
    */
   @TernMethod
   public boolean isScatterMatrix() {
      return scatterMatrix;
   }

   /**
    * Set if this is the inner most facet coordinate for scatter plot matrix.
    * This means this is a facet graph, and the inner dimension of the facet is
    * actually the measure name, which is used with PairsDataSet to create a
    * crosstab of scatter plots.
    */
   @TernMethod
   public void setScatterMatrix(boolean flag) {
      this.scatterMatrix = flag;
   }

   /**
    * Get all aesthetic frames in the graph.
    */
   @TernMethod
   public VisualFrame[] getAllVisualFrames() {
      List<VisualFrame> all = new ArrayList<>();

      for(int i = 0; i < getElementCount(); i++) {
         GraphElement elem = getElement(i);
         VisualFrame[] frames = {
            elem.getColorFrame(), elem.getSizeFrame(), elem.getShapeFrame(),
            elem.getLineFrame(), elem.getTextureFrame()
         };

         for(VisualFrame frame : frames) {
            if(elem.supportsFrame(frame)) {
               all.add(frame);
            }
         }
      }

      return all.stream().filter(f -> f != null).toArray(VisualFrame[]::new);
   }

   /**
    * Get all aesthetic frames in the graph that are candidates for legend.
    */
   @TernMethod
   public VisualFrame[] getVisualFrames() {
      // vector[frame, shared color frame] -> frame
      Map<Object, VisualFrame> vmap = new HashMap<>();
      Set<String> otherIds = new HashSet<>(); // non-color-frame
      List<VisualFrame> sharedColors = new ArrayList<>();
      Map<Integer, Integer> frameElemIdx = new HashMap<>();

      for(int i = 0; i < getElementCount(); i++) {
         Set<VisualFrame> set = new HashSet<>();
         GraphElement elem = getElement(i);
         VisualFrame[] frames = elem.getVisualFrames();

         for(int j = 0; j < frames.length; j++) {
            if(frames[j] == null) {
               continue;
            }

            frames[j].setLegendFrame(null);
            frames[j] = getGuideFrame(frames[j]);
            frameElemIdx.put(System.identityHashCode(frames[j]), i);

            if(frames[j] == null || !frames[j].isVisible() || !elem.supportsFrame(frames[j])) {
               continue;
            }

            if(frames[j] instanceof CategoricalFrame || frames[j] instanceof StackedMeasuresFrame) {
               ColorFrame shared = elem.getSharedColorFrame(frames[j]);

               if(shared != null) {
                  sharedColors.add(getGuideFrame(shared));
               }

               if(!(frames[j] instanceof ColorFrame)) {
                  otherIds.add(frames[j].getShareId());
               }
            }

            set.add(frames[j]);
         }

         // don't show legend if color is already painted by other legends.
         for(VisualFrame sharedColor : sharedColors) {
            if(sharedColor != null && otherIds.contains(sharedColor.getShareId())) {
               set.remove(sharedColor);
            }
         }

         for(VisualFrame frame : set) {
            List<VisualFrame> key = new ArrayList<>();
            key.add(frame);
            VisualFrame sharedColor = null;

            for(VisualFrame shared : sharedColors) {
               if(shared != null && frame instanceof CategoricalFrame &&
                  CoreTool.equals(frame.getShareId(), shared.getShareId()))
               {
                  key.add(shared);
                  sharedColor = shared;
               }
            }

            VisualFrame legend = vmap.get(key);

            if(legend == null) {
               vmap.put(key, frame);

               // @by jerry, fixed bug1354875598523, can be shared only when
               // the dataId equal
               if(sharedColor != null && key.size() > 1) {
                  sharedColor.setLegendFrame(frame);
               }
            }
            else {
               frame.setLegendFrame(legend);
            }
         }
      }

      List<VisualFrame> frames = new ArrayList<>(vmap.values());
      frames.sort(createComparator(frameElemIdx));

      // remove identical (===) frames (brushed & all) but allow equal frames
      // in case they share different colors
      for(int i = frames.size() - 1; i > 0; i--) {
         for(int j = i - 1; j >= 0; j--) {
            if(frames.get(j) == frames.get(i) || isIdenticalStackedMeasuresFrame(frames, j, i)) {
               frames.remove(i);
               break;
            }
            else if(isIdenticalStackedMeasuresFrame(frames, i, j)) {
               frames.remove(j);
               break;
            }
         }
      }

      return frames.toArray(new VisualFrame[0]);
   }

   /**
    * Create comparator to sort legend frames.
    */
   private static Comparator<VisualFrame> createComparator(Map<Integer, Integer> frameElemIdx) {
      return new Comparator<VisualFrame>() {
         @Override
         public int compare(VisualFrame frame1, VisualFrame frame2) {
            int val1 = rankType(frame1);
            int val2 = rankType(frame2);

            if(val1 < val2) {
               return -1;
            }
            else if(val1 > val2) {
               return 1;
            }

            String title1 = frame1.getTitle();
            String title2 = frame2.getTitle();
            int rc = title1.compareTo(title2);

            if(rc == 0) {
               // legend for stacked bar layout items in reverse order. this makes sure the
               // order for the frame is deterministic so the legend won't change randomly.
               rc = frameElemIdx.get(System.identityHashCode(frame1)).compareTo(
                  frameElemIdx.get(System.identityHashCode(frame2)));
            }

            return rc;
         }

         private int rankType(VisualFrame frame) {
            if(frame instanceof ColorFrame) {
               return 1;
            }
            else if(frame instanceof SizeFrame) {
               return 2;
            }
            else if(frame instanceof ShapeFrame) {
               return 3;
            }
            else if(frame instanceof TextureFrame) {
               return 4;
            }
            else if(frame instanceof LineFrame) {
               return 5;
            }
            else {
               throw new RuntimeException("Unsupported frame found: " + frame);
            }
         }
      };
   }

   private VisualFrame getGuideFrame(VisualFrame frame) {
      while(frame instanceof CompositeVisualFrame) {
         frame = ((CompositeVisualFrame) frame).getGuideFrame();
      }

      return frame;
   }

   private boolean isIdenticalStackedMeasuresFrame(List<VisualFrame> frames, int i, int j) {
      if(frames.get(i) instanceof StackedMeasuresFrame) {
         StackedMeasuresFrame<?> stackedFrame = (StackedMeasuresFrame<?>) frames.get(i);
         VisualFrame defFrame = stackedFrame.getDefaultFrame();

         return defFrame != null && defFrame.getShareId().equals(frames.get(j).getShareId());
      }

      return false;
   }

   /**
    * Clear all settings and restore to the initial mode.
    */
   @TernMethod
   public void clear() {
      elems = new Vector<>();
      forms = new Vector<>();
      coord = null;
      graphCount = 0;
      scalemap = new HashMap<>();
      legendLayout = GraphConstants.RIGHT;
      legendPreferredSize = null;
      xTitleSpec = new TitleSpec();
      yTitleSpec = new TitleSpec();
      x2TitleSpec = new TitleSpec();
      y2TitleSpec = new TitleSpec();

      yTitleSpec.getTextSpec().setRotation(90);
      y2TitleSpec.getTextSpec().setRotation(90);
   }

   /**
    * Get the string presentation.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("EGraph:");
      sb.append(super.toString());
      sb.append('\n');
      sb.append("elements:");
      sb.append('\n');
      sb.append('\t');
      sb.append(elems);
      sb.append('\n');
      sb.append("scales:");
      sb.append('\n');
      sb.append('\t');
      sb.append(scalemap);
      return sb.toString();
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         EGraph graph = (EGraph) super.clone();

         if(coord != null) {
            graph.coord = (Coordinate) coord.clone(false);
         }

         if(elems != null) {
            graph.elems = CoreTool.deepCloneCollection(elems);
         }

         if(forms != null) {
            graph.forms = CoreTool.deepCloneCollection(forms);
         }

         if(scalemap != null) {
            graph.scalemap = CoreTool.deepCloneMap(scalemap);
         }

         return graph;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone graph", ex);
      }

      return null;
   }

   /**
    * Create a geometry graph from coordinate and data set.
    */
   public GGraph createGGraph(Coordinate coord, DataSet data) {
      graphCount++;

      // if geometry graph count exceeds defined max count,
      // throw runtime exception to avoid out-of-memory
      if(graphCount > MAX_GGRAPH_COUNT) {
         throw new MessageException(GTool.getString(
            "viewer.viewsheet.chart.ggraphMax", MAX_GGRAPH_COUNT), LogLevel.WARN, false);
      }

      return new GGraph(this, coord, data);
   }

   private static final Logger LOG = LoggerFactory.getLogger(EGraph.class);
   private static int MAX_GGRAPH_COUNT;

   static {
      String mstr = GTool.getProperty("graph.ggraph.maxcount",
                                      GDefaults.MAX_GGRAPH_COUNT + "");
      try {
         MAX_GGRAPH_COUNT = Integer.parseInt(mstr);
      }
      catch(Exception ex) {
         LOG.warn("Invalid graph count setting: " + mstr, ex);
         MAX_GGRAPH_COUNT = GDefaults.MAX_GGRAPH_COUNT;
      }
   }

   private Vector<GraphElement> elems;
   private Vector<GraphForm> forms;
   private Coordinate coord;
   private Map<String, Scale> scalemap;
   private int legendLayout;
   private Double legendPreferredSize;
   private TitleSpec xTitleSpec;
   private TitleSpec yTitleSpec;
   private TitleSpec x2TitleSpec;
   private TitleSpec y2TitleSpec;
   private int graphCount = 0;
   private boolean scatterMatrix = false;
   private static final long serialVersionUID = 1L;
}
