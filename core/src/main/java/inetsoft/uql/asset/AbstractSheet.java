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
package inetsoft.uql.asset;

import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.XQueryRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.FileVersions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.WeakReference;
import java.security.Principal;
import java.util.*;
import java.util.jar.JarOutputStream;

/**
 * AbstractSheet like a spreadsheet, contains several assemblies.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractSheet implements AssetObject {
   /**
    * Sheet design mode.
    */
   public static final int SHEET_DESIGN_MODE = 1;
   /**
    * Sheet runtime mode.
    */
   public static final int SHEET_RUNTIME_MODE = 2;

   /**
    * Condition asset.
    */
   public static final int CONDITION_ASSET = 1;
   /**
    * Named group asset.
    */
   public static final int NAMED_GROUP_ASSET = 2;
   /**
    * Variable asset.
    */
   public static final int VARIABLE_ASSET = 3;
   /**
    * Table asset.
    */
   public static final int TABLE_ASSET = 4;
   /**
    * Date Condition asset.
    */
   public static final int DATE_RANGE_ASSET = 5;

   /**
    * Table view asset.
    */
   public static final int TABLE_VIEW_ASSET = 101;
   /**
    * Chart asset.
    */
   public static final int CHART_ASSET = 102;
   /**
    * Crosstab asset.
    */
   public static final int CROSSTAB_ASSET = 103;
   /**
    * Formula table asset.
    */
   public static final int FORMULA_TABLE_ASSET = 104;
   /**
    * Cube asset.
    */
   public static final int CUBE_ASSET = 105;
   /**
    * Slider asset.
    */
   public static final int SLIDER_ASSET = 106;
   /**
    * Spinner asset.
    */
   public static final int SPINNER_ASSET = 107;
   /**
    * Checkbox asset.
    */
   public static final int CHECKBOX_ASSET = 108;
   /**
    * RadioButton asset.
    */
   public static final int RADIOBUTTON_ASSET = 109;
   /**
    * ComboBox asset.
    */
   public static final int COMBOBOX_ASSET = 110;
   /**
    * Text asset.
    */
   public static final int TEXT_ASSET = 111;
   /**
    * Image asset.
    */
   public static final int IMAGE_ASSET = 112;
   /**
    * Gauge asset.
    */
   public static final int GAUGE_ASSET = 113;
   /**
    * Thermometer asset.
    */
   public static final int THERMOMETER_ASSET = 114;
   /**
    * Sliding scale asset.
    */
   public static final int SLIDING_SCALE_ASSET = 115;
   /**
    * Cylinder asset.
    */
   public static final int CYLINDER_ASSET = 116;
   /**
    * Selection list asset.
    */
   public static final int SELECTION_LIST_ASSET = 117;
   /**
    * Selection tree asset.
    */
   public static final int SELECTION_TREE_ASSET = 118;
   /**
    * Time slider asset.
    */
   public static final int TIME_SLIDER_ASSET = 119;
   /**
    * Calendar asset.
    */
   public static final int CALENDAR_ASSET = 120;
   /**
    * DrillBox asset.
    */
   public static final int DRILL_BOX_ASSET = 121;
   /**
    * Tab asset.
    */
   public static final int TAB_ASSET = 122;
   /**
    * Embedded table asset.
    */
   public static final int EMBEDDEDTABLE_VIEW_ASSET = 123;
   /**
    * Group container asset.
    */
   public static final int GROUPCONTAINER_ASSET = 124;
   /**
    * Line shape asset.
    */
   public static final int LINE_ASSET = 125;
   /**
    * Rectangle shape asset.
    */
   public static final int RECTANGLE_ASSET = 126;
   /**
    * Oval shape asset.
    */
   public static final int OVAL_ASSET = 127;
   /**
    * Group container asset.
    */
   public static final int CURRENTSELECTION_ASSET = 128;
   /**
    * TextInput asset.
    */
   public static final int TEXTINPUT_ASSET = 129;
   /**
    * Submit asset.
    */
   public static final int SUBMIT_ASSET = 130;
   /**
    * Upload asset.
    */
   public static final int UPLOAD_ASSET = 134;
   /**
    * PageBreak asset.
    */
   public static final int PAGEBREAK_ASSET = 135;
   /**
    * Annotation asset.
    */
   public static final int ANNOTATION_ASSET = 131;
   /**
    * Annotation line asset.
    */
   public static final int ANNOTATION_LINE_ASSET = 132;
   /**
    * Annotation rectangle asset.
    */
   public static final int ANNOTATION_RECTANGLE_ASSET = 133;
   /**
    * Viewsheet asset.
    */
   public static final int VIEWSHEET_ASSET = 200;
   /**
    * Viewsheet snapshot asset.
    */
   public static final int VIEWSHEET_SNAPSHOT_ASSET = 201;

   /**
    * Font family.
    */
   public static final int FONT_FAMILY = 300;

   /**
    * User format.
    */
   public static final int USER_FORMAT = 400;

   /**
    * Add assembly.
    */
   public static final int ADD_ASSEMBLY = 1;
   /**
    * Remove assembly.
    */
   public static final int REMOVE_ASSEMBLY = 2;
   /**
    * Rename assembly.
    */
   public static final int RENAME_ASSEMBLY = 3;
   /**
    * Bind assembly.
    */
   public static final int BIND_ASSEMBLY = 4;
   /**
    * Sheet is saved to permanent storage.
    */
   public static final int SHEET_SAVED = 5;
   /**
    * Sheet is to be saved with a different name.
    */
   public static final int SHEET_SAVE_AS = 6;

   /**
    * Create a sheet object.
    */
   public AbstractSheet() {
      super();

      this.event = true;
      this.listeners = new ArrayList<>();
      this.created = System.currentTimeMillis();
      this.modified = System.currentTimeMillis();
   }

   /**
    * Get the size of this sheet.
    * @return the size of this sheet.
    */
   public abstract Dimension getPixelSize();

   /**
    * Check if contains an assembly.
    * @param name the specified assembly name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public abstract boolean containsAssembly(String name);

   /**
    * Get an assembly by its entry.
    * @param entry the specified assembly entry.
    * @return the assembly, <tt>null</tt> if not found.
    */
   public abstract Assembly getAssembly(AssemblyEntry entry);

   /**
    * Get an assembly by its name.
    * @param name the specified assembly name.
    * @return the assembly, <tt>null</tt> if not found.
    */
   public abstract Assembly getAssembly(String name);

   /**
    * Get all the assemblies.
    * @return all the assemblies.
    */
   public abstract Assembly[] getAssemblies();

   /**
    * Get the gap between two assemblies.
    * @return the gap between two assemblies.
    */
   protected abstract int getGap();

   /**
    * Get the outer dependents.
    * @return the outer dependents.
    */
   public abstract AssetEntry[] getOuterDependents();

   /**
    * Rename an outer dependent.
    * @param oentry the specified old entry.
    * @param nentry the specified new entry.
    */
   public abstract void renameOuterDependent(AssetEntry oentry,
                                             AssetEntry nentry);

   /**
    * Get the outer dependencies.
    * @return the outer dependencies.
    */
   public AssetEntry[] getOuterDependencies() {
      return getOuterDependencies(false);
   }

   /**
    * Get the outer dependencies.
    * @param sort sort array
    * @return the outer dependencies.
    */
   public abstract AssetEntry[] getOuterDependencies(boolean sort);

   /**
    * Add an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public abstract boolean addOuterDependency(AssetEntry entry);

   /**
    * Remove an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public abstract boolean removeOuterDependency(AssetEntry entry);

   /**
    * Remove all the outer dependencies.
    */
   public abstract void removeOuterDependencies();

   /**
    * Update this sheet.
    * @param rep the specified asset repository.
    * @param entry the specified entry stored in.
    * @param user the specified principal.
    */
   public abstract boolean update(AssetRepository rep, AssetEntry entry,
                                  Principal user);

   /**
    * Check if the sheet is valid.
    */
   public void checkValidity() throws Exception {
      checkValidity(true);
   }

   /**
    * Checks if the sheet is valid.
    *
    * @param checkCrossJoins {@code true} to check if an unintended cross join is present or
    *                        {@code false} to ignore cross joins.
    *
    * @throws Exception if the sheet is invalid.
    */
   public abstract void checkValidity(boolean checkCrossJoins) throws Exception;

   /**
    * Check if the dependency is valid.
    */
   public abstract void checkDependencies() throws InvalidDependencyException;

   /**
    * Get the type of the sheet.
    * @return the type of the sheet.
    */
   public abstract int getType();

   /**
    * Reset the sheet.
    */
   public abstract void reset();

   /**
    * Get the assemblies depended on of an assembly in a sheet.
    * @param entry the specified assembly entry.
    */
   public abstract AssemblyRef[] getDependeds(AssemblyEntry entry);

   /**
    * Get the assemblies depended on of an assembly in a viewsheet.
    * @param entry the specified assembly entry.
    * @param view <tt>true</tt> to include view, <tt>false</tt> otherwise.
    * @param out <tt>out</tt> to include out, <tt>false</tt> otherwise.
    */
   public abstract AssemblyRef[] getDependeds(AssemblyEntry entry, boolean view,
                                              boolean out);

   /**
    * Set last size to the sheet.
    * @param lsize the specified last size.
    */
   public void setLastSize(Dimension lsize) {
      this.lsize = lsize;
   }

   /**
    * Get last size of the sheet.
    * @return last size of the sheet.
    */
   public Dimension getLastSize() {
      return lsize == null ? getPixelSize() : lsize;
   }

   /**
    * Layout the sheet. Any overlapping assemblies are moved.
    * @param vonly true if vertical movement only, false both directions are ok.
    * @param arr the assemblies need be layout.
    * @return the assemblies relocated.
    */
   public Assembly[] layout(boolean vonly, ArrayList arr) {
      Set<Assembly> changed = new HashSet<>();
      layoutAssemblies(false, vonly, arr, changed);
      return changed.toArray(new Assembly[0]);
   }

   /**
    * Get the pixel bounds of an embed viewsheet. Only calculate the pixel
    * bounds for embed viewsheet, and it will be override by Viewsheet.java.
    * @param assembly the specified assembly.
    * @return the layout bounds of the assembly.
    */
   protected Rectangle getActualBounds(Assembly assembly) {
      return getLayoutBounds(assembly);
   }

   /**
    * Get the layout bounds of an assembly.
    * @param assembly the specified assembly.
    * @return the layout bounds of the assembly.
    */
   protected Rectangle getLayoutBounds(Assembly assembly) {
      return assembly.getBounds();
   }

   /**
    * Check if is visible when layout.
    * @param assembly the specified assembly.
    * @return <tt>true</tt> if visible when layout, <tt>false</tt> otherwise.
    */
   protected boolean isLayoutVisible(Assembly assembly) {
      return assembly.isVisible();
   }

   /**
    * Layout the sheet. Any overlapping assemblies are moved.
    * @return the names of the assemblies relocated.
    */
   public Assembly[] layout() {
      return layout(false);
   }

   /**
    * Layout the sheet. Any overlapping assemblies are moved.
    * @param vonly true if vertical movement only, false both directions are ok.
    * @return the names of the assemblies relocated.
    */
   public Assembly[] layout(boolean vonly) {
      Set changed = new HashSet();

      // layout y direction first which seems more natural
      layout(false, vonly, changed);

      // x direction allowed? try x direction
      if(!vonly) {
         layout(true, vonly, changed);
      }

      Assembly[] arr = new Assembly[changed.size()];
      changed.toArray(arr);
      return arr;
   }

   /**
    * Lay out one dimension.
    * @param x <tt>true</tt> means x dimension, <tt>false</tt> y dimension.
    * @param vonly true if vertical movement only, false both directions are ok.
    * @param changed the set stores changed assemblies.
    */
   private void layout(boolean x, boolean vonly, Set<Assembly> changed) {
      Assembly[] arr = getAssemblies();
      ArrayList<Assembly> assemblies = new ArrayList<>();

      // ignore invisible ones
      for(Assembly anArr : arr) {
         if(isLayoutVisible(anArr)) {
            assemblies.add(anArr);
         }
      }

      layoutAssemblies(x, vonly, assemblies, changed);
   }

   /**
    * Lay out one dimension.
    * @param x <tt>true</tt> means x dimension, <tt>false</tt> y dimension.
    * @param vonly true if vertical movement only, false both directions are ok.
    * @param assemblies the assemblies need be layout.
    * @param changed the set stores changed assemblies.
    */
   private void layoutAssemblies(boolean x, boolean vonly,
                                 ArrayList assemblies, Set<Assembly> changed) {
      AssemblyComparator comparator = new AssemblyComparator(x);

      for(int start = 0; start < assemblies.size() - 1; start++) {
         int index = start;
         Object obj = assemblies.get(start);

         // find the minimum
         for(int i = start + 1; i < assemblies.size(); i++) {
            if(comparator.compare(obj, assemblies.get(i)) > 0) {
               index = i;
               obj = assemblies.get(i);
            }
         }

         // swap the two
         Object temp = assemblies.get(start);
         assemblies.set(start, obj);
         assemblies.set(index, temp);
         Assembly a = (Assembly) obj;

         while(true) {
            boolean linger = false;

            // move the assemblies
            for(int i = start + 1; i < assemblies.size(); i++) {
               Assembly b = (Assembly) assemblies.get(i);
               int moved = x ? moveX(a, b, vonly) : moveY(a, b, vonly);

               if(moved != NO_MOVEMENT) {
                  // move a itself? linger the loop
                  if(!x && moved != Y_MOVEMENT) {
                     changed.add(a);
                     linger = true;
                  }
                  // move b? needn't linger the loop
                  else {
                     changed.add(b);
                  }
               }
            }

            if(!linger) {
               break;
            }
         }
      }
   }

   /**
    * Move one assembly in x dimension.
    * @param a the specified assembly a.
    * @param b the specified assembly b to move.
    * @param vonly true if vertical movement only, false both directions are ok.
    * @return the move result.
    */
   private int moveX(Assembly a, Assembly b, boolean vonly) {
      if(vonly) {
         return NO_MOVEMENT;
      }

      Rectangle abounds = getLayoutBounds(a);
      abounds.width += getGap() * AssetUtil.defw;
      Rectangle bbounds = getLayoutBounds(b);

      if(abounds.y < bbounds.y) {
         abounds.height += getGap() * AssetUtil.defh;
      }
      else if(abounds.y > bbounds.y) {
         bbounds.height += getGap() * AssetUtil.defh;
      }

      Rectangle ibounds = abounds.intersection(bbounds);
      int moved = NO_MOVEMENT;

      if(!ibounds.isEmpty()) {
         int x0 = abounds.x + abounds.width;
         int y0 = bbounds.y;
         b.setPixelOffset(new Point(x0, y0));
         moved = X_MOVEMENT;
      }

      return moved;
   }

   /**
    * Move one assembly in y dimension.
    * @param a the specified assembly a.
    * @param b the specified assembly b to move.
    * @param vonly true if vertical movement only, false both directions are ok.
    * @return the move result.
    */
   protected int moveY(Assembly a, Assembly b, boolean vonly) {
      Rectangle abounds = getLayoutBounds(a);
      abounds.height += getGap() * AssetUtil.defh; // at least one gap

      Rectangle bbounds = getLayoutBounds(b);
      bbounds.x -= getGap() * AssetUtil.defw; // at least one gap
      bbounds.width += 2 * getGap() * AssetUtil.defw; // at least one gap

      Rectangle ibounds = abounds.intersection(bbounds);
      int moved = NO_MOVEMENT;

      if(!ibounds.isEmpty()) {
         // vertical only? force to move assembly vertically
         if(vonly) {
            int x0 = bbounds.x + getGap() * AssetUtil.defw;
            int y0 = abounds.y + abounds.height;
            b.setPixelOffset(new Point(x0, y0));
            moved = Y_MOVEMENT;
         }
         else {
            int south = getSouth(abounds, bbounds);
            int west = getWest(abounds, bbounds);
            int east = getEast(abounds, bbounds);

            if(south <= (west * 2) && south <= (east * 2)) {
               int x0 = bbounds.x + getGap() * AssetUtil.defw;
               int y0 = abounds.y + abounds.height;
               b.setPixelOffset(new Point(x0, y0));
               moved = Y_MOVEMENT;
            }
            else if((west * 2) < south && west < east) {
               int x0 = bbounds.x + bbounds.width;
               int y0 = abounds.y;
               a.setPixelOffset(new Point(x0, y0));
               moved = X_MOVEMENT;
            }
         }
      }

      return moved;
   }

   /**
    * Get the west distance.
    * @param abounds the bounds a.
    * @param bbounds the bounds b.
    */
   private int getWest(Rectangle abounds, Rectangle bbounds) {
      return bbounds.x + bbounds.width - abounds.x;
   }

   /**
    * Get the east distance.
    * @param abounds the bounds a.
    * @param bbounds the bounds b.
    */
   private int getEast(Rectangle abounds, Rectangle bbounds) {
      return (abounds.x + abounds.width) - bbounds.x;
   }

   /**
    * Get the north distance.
    * @param abounds the bounds a.
    * @param bbounds the bounds b.
    */
   private int getNorth(Rectangle abounds, Rectangle bbounds) {
      return bbounds.y + bbounds.height - abounds.y;
   }

   /**
    * Get the sorth distance.
    * @param abounds the bounds a.
    * @param bbounds the bounds b.
    */
   private int getSouth(Rectangle abounds, Rectangle bbounds) {
      return (abounds.y + abounds.height) - bbounds.y;
   }

   /**
    * Set last modified time.
    * @param modified the specified last modified time.
    */
   public void setLastModified(long modified) {
      this.modified = modified;
   }

   /**
    * Get last modified time.
    * @return last modified time.
    */
   public long getLastModified() {
      return modified;
   }

   /**
    * Get last modified.
    * @return last modified time.
    */
   public long getLastModified(boolean recursive) {
      return getLastModified();
   }

   /**
    * Set created time.
    * @param created the specified created time.
    */
   public void setCreated(long created) {
      this.created = created;
   }

   /**
    * Get created time.
    * @return created time.
    */
   public long getCreated() {
      return created;
   }

   /**
    * Set last modified person.
    * @param modifiedBy the specified last modified person.
    */
   public void setLastModifiedBy(String modifiedBy) {
      this.modifiedBy = modifiedBy;
   }

   /**
    * Get last modified person.
    * @return last modified person.
    */
   public String getLastModifiedBy() {
      return modifiedBy;
   }

   /**
    * Set the created person
    * @param createdBy the created person.
    */
   public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
   }

   /**
    * Get the created person.
    * @return the created person.
    */
   public String getCreatedBy() {
      return createdBy;
   }

   /**
    * Add an action listener observes asset changes.
    */
   public void addActionListener(ActionListener listener) {
      synchronized(listeners) {
         for(WeakReference<ActionListener> ref : listeners) {
            ActionListener l = ref.get();

            if(listener == l) {
               return;
            }
         }

         WeakReference<ActionListener> ref = new WeakReference<>(listener);
         listeners.add(ref);
      }
   }

   /**
    * Remove an action listener registered.
    */
   public void removeActionListener(ActionListener listener) {
      synchronized(listeners) {
         for(int i = 0; i < listeners.size(); i++) {
            WeakReference<ActionListener> ref = listeners.get(i);
            Object obj = ref.get();

            if(listener == obj) {
               listeners.remove(i);
               return;
            }
         }
      }
   }

   /**
    * Remove all the action listeners.
    */
   public void removeActionListeners() {
      synchronized(listeners) {
         listeners.clear();
      }
   }

   /**
    * Check if fire event when fireEvent is called.
    * @return false if not to fire event to listeners.
    */
   public synchronized boolean isFireEvent() {
      return event;
   }

   /**
    * Set whether fire event when fireEvent is called.
    * @param event false if not to fire event to listeners.
    */
   public synchronized void setFireEvent(boolean event) {
      this.event = event;
   }

   /**
    * Fire event.
    * @param type the specified sheet type.
    * @param cmd the specified command.
    */
   public void fireEvent(int type, String cmd) {
      if(!event) {
         return;
      }

      ArrayList<WeakReference<ActionListener>> listeners;

      synchronized(this.listeners) {
         // clean up listeners
         for(int i = this.listeners.size() - 1; i>= 0; i--) {
            WeakReference<ActionListener> ref = this.listeners.get(i);

            if(ref.get() == null) {
               this.listeners.remove(i);
            }
         }

         listeners = new ArrayList<>(this.listeners);
      }

      ActionEvent event = new ActionEvent(this, type, cmd);

      // @by larryl, need to go from beginning so the viewsheet listener is
      // fired first and the resetWS is called before the sandbox
      for(WeakReference<ActionListener> ref : listeners) {
         ActionListener listener = ref.get();

         if(listener != null) {
            try {
               listener.actionPerformed(event);
            }
            catch(ConfirmException ex) {
               WorksheetEngine.ASSET_EXCEPTIONS.set(new ArrayList<>());
               WorksheetEngine.ASSET_EXCEPTIONS.get().add(ex);
            }
            catch(Exception ex) {
               LOG.error("Failed to handle action event: " + this, ex);
            }
         }
      }
   }

   /**
    * Write out data content in each assembly.
    */
   public void writeData(JarOutputStream out) {
   }

   /**
    * Get the description of the worksheet.
    * @return the description of the worksheet.
    */
   public String getDescription() {
      return null;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         AbstractSheet sheet = (AbstractSheet) super.clone();
         sheet.listeners = new ArrayList<>();

         return sheet;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Assembly comparator compares two assembly bounds.
    */
   private class AssemblyComparator implements Comparator {
      /**
       * Constructor.
       */
      public AssemblyComparator(boolean x) {
         this.x = x;
      }

      /**
       * Compare two object.
       * @param obja the object a.
       * @param objb the object b.
       */
      @Override
      public int compare(Object obja, Object objb) {
         Assembly a = (Assembly) obja;
         Assembly b = (Assembly) objb;
         Rectangle abounds = getActualBounds(a);
         Rectangle bbounds = getActualBounds(b);
         return x ? abounds.x - bbounds.x : abounds.y - bbounds.y;
      }

      private boolean x;
   }

   public String getVersion() {
      return version;
   }

   /**
    * The version value from default or parse xml. Don't modify this version.
    */
   protected void setVersion(String version) {
      if(version != null && !version.isEmpty()) {
         this.version = version;
      }
   }

   /**
    * Create a copy of the sheet to be added to checkpoints.
    * Remove any runtime information that would be recreated when it's
    * used later. This is called when swapping a sheet out for undo/redo to
    * avoid writing unnecessary data.
    */
   public AbstractSheet prepareCheckpoint() {
      return (AbstractSheet) clone();
   }

   /**
    * Get the original hash code.
    * @return the original hash code.
    */
   public int addr() {
      return System.identityHashCode(this);
   }

   protected static final Dimension MIN_SIZE = new Dimension(50 * AssetUtil.defw, 300 * AssetUtil.defh);
   protected static final int NO_MOVEMENT = 0;
   protected static final int X_MOVEMENT = 1;
   protected static final int Y_MOVEMENT = 2;
   protected static final int PREF_COL = 9;

   private long created;
   private long modified;
   private String createdBy;
   private String modifiedBy;
   private transient boolean event;
   private transient Dimension lsize;
   private transient ArrayList<WeakReference<ActionListener>> listeners;
   private transient WeakReference<XQueryRepository> rep;
   protected String version = FileVersions.ASSET; // sheet designed version.

   private static final Logger LOG = LoggerFactory.getLogger(AbstractSheet.class);
}
