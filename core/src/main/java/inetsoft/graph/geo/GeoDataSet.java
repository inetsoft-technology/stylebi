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
package inetsoft.graph.geo;

import inetsoft.graph.data.*;
import inetsoft.util.log.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This dataset adds latitude, longitude, and GeoArea columns to the base
 * dataset for use in a GeoCoord.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class GeoDataSet extends TopDataSet {
   /**
    * Creates a new instance of <tt>GeoDataSet</tt>.
    * @param data       the source data set.
    * @param gmap       the area map features.
    * @param areaFields  the source columns for the area map features.
    */
   public GeoDataSet(DataSet data, GeoMap gmap, String... areaFields) {
      this(data, gmap, areaFields, (GeoPoints) null);
   }

   /**
    * Creates a new instance of <tt>GeoDataSet</tt>.
    * @param data        the source data set.
    * @param gpoints     the point map features.
    * @param pointFields the source column for the point map features.
    */
   public GeoDataSet(DataSet data, GeoPoints gpoints, String... pointFields) {
      this(data, null, new String[0], gpoints, pointFields);
   }

   /**
    * Creates a new instance of <tt>GeoDataSet</tt>.
    * @param data        the source data set.
    * @param gmap        the area map features.
    * @param areaField   the source column for the area map features.
    * @param gpoints     the point map features.
    * @param pointFields the source column for the point map features.
    */
   public GeoDataSet(DataSet data, GeoMap gmap, String areaField,
                     GeoPoints gpoints, String... pointFields)
   {
      this(data, gmap, new String[] {areaField}, gpoints, pointFields);
   }

   /**
    * Creates a new instance of <tt>GeoDataSet</tt>.
    * @param data        the source data set.
    * @param gmap        the area map features.
    * @param areaFields  the source columns for the area map features.
    * @param gpoints     the point map features.
    * @param pointFields the source column for the point map features.
    */
   public GeoDataSet(DataSet data, GeoMap gmap, String[] areaFields,
                     GeoPoints gpoints, String... pointFields)
   {
      this(data, gmap, areaFields, new GeoPoints[] {gpoints}, pointFields);
   }

   /**
    * Creates a new instance of <tt>GeoDataSet</tt>.
    * @param data        the source data set.
    * @param gmap        the area map features.
    * @param areaFields  the source columns for the area map features.
    * @param gpoints     the point map features.
    * @param pointFields the source column for the point map features.
    */
   public GeoDataSet(DataSet data, GeoMap gmap, String[] areaFields,
                     GeoPoints[] gpoints, String... pointFields)
   {
      super(data);

      this.geomap = gmap;
      this.geopoints = gpoints;
      this.areaFields = areaFields;
      this.pointFields = pointFields;
      this.columnCount = 3 * areaFields.length + 2 * pointFields.length;
      this.headers = new String[columnCount];
      this.types = new Class[columnCount];

      int cnt = areaFields.length;

      for(int i = 0; i < cnt; i++) {
         headers[3 * i] = getLongitudeField(areaFields[i]);
         types[3 * i] = Double.class;

         headers[3 * i + 1] = getLatitudeField(areaFields[i]);
         types[3 * i + 1] = Double.class;

         headers[3 * i + 2] = getGeoAreaField(areaFields[i]);
         types[3 * i + 2] = GeoShape.class;
      }

      for(int i = 0; i < pointFields.length; i++) {
         headers[3 * cnt + i * 2] = getLongitudeField(pointFields[i]);
         types[3 * cnt + i * 2] = Double.class;

         headers[3 * cnt + i * 2 + 1] = getLatitudeField(pointFields[i]);
         types[3 * cnt + i * 2 + 1] = Double.class;
      }
   }

   @Override
   public DataSet wrap(DataSet base, TopDataSet proto) {
      GeoDataSet gdata = (GeoDataSet) proto;

      return new GeoDataSet(base, gdata.getGeoMap(),
                            gdata.getAreaFields(), gdata.getGeoPoints(),
                            gdata.getPointFields());
   }

   /**
    * Check if all shapes from shapefile should be loaded.
    */
   public boolean isLoadAll() {
      return loadAll;
   }

   /**
    * Set if all shapes from shapefile should be loaded.
    */
   public void setLoadAll(boolean loadAll) {
      this.loadAll = loadAll;
   }

   private synchronized void init() {
      if(inited) {
         return;
      }

      inited = true;
      DataSet data = getDataSet();
      Point2D[] points = new Point2D[pointFields.length];
      boolean fine = LogManager.getInstance().isDebugEnabled(LOG.getName());

      try {
         for(int i = 0; i < data.getRowCount() && !isDisposed(); i++) {
            GeoShape[] features = new GeoShape[areaFields.length];
            boolean found = true;

            for(int j = 0; j < areaFields.length; j++) {
               String geoCode = (String) data.getData(areaFields[j], i);

               if(geoCode == null) {
                  continue;
               }

               features[j] = geomap.getShape(geoCode);

               if(features[j] == null) {
                  // make sure gdata has same number of rows as dataset
                  gdata.add(new Object[columnCount]);

                  if(fine) {
                     LOG.debug("Shape not found: " + geoCode);
                  }

                  found = false;
                  break;
               }
            }

            if(!found) {
               continue;
            }

            for(int k = 0; k < pointFields.length; k++) {
               String geoCode = (String) data.getData(pointFields[k], i);

               if(geoCode == null) {
                  continue;
               }

               points[k] = geopoints[k % geopoints.length].getPoint(geoCode);

               if(points[k] == null && fine) {
                  LOG.debug("Point not found: " + geoCode);
               }
            }

            addRow(points, features);
            points = new Point2D[pointFields.length];
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize data set", ex);
      }
   }

   /**
    * Loads all shapes into rows.
    */
   private void loadAllShapes() throws Exception {
      if(all || !loadAll) {
         return;
      }

      Set<String> used = new HashSet<>();
      all = true;

      for(int i = 0; i < getDataSet().getRowCount(); i++) {
         for(int j = 0; j < areaFields.length; j++) {
            used.add((String) getDataSet().getData(areaFields[j], i));
         }
      }

      if(geomap != null) {
         for(String name : geomap.getNames()) {
            if(!used.contains(name) || geomap.isOverlayShape(name)) {
               addRow(null, geomap.getShape(name));
            }
         }
      }
   }

   /**
    * Adds a row to the geographic data.
    *
    * @param points   the point feature to add, may be <tt>null</tt>.
    * @param features the area feature to add.
    */
   private void addRow(Point2D[] points, GeoShape... features) {
      Object[] row = new Object[columnCount];
      int cnt = features.length;

      for(int i = 0; i < cnt; i++) {
         GeoShape feature = features[i];

         if(feature == null) {
            continue;
         }

         Point2D center = feature.getPrimaryAnchor();
         row[i * 3] = center.getX();
         row[i * 3 + 1] = center.getY();
         row[i * 3 + 2] = feature;
      }

      for(int i = 0; points != null && i < points.length; i++) {
         if(points[i] == null) {
            continue;
         }

         row[cnt * 3 + i * 2] = points[i].getX();
         row[cnt * 3 + i * 2 + 1] = points[i].getY();
      }

      gdata.add(row);
   }

   /**
    * Get the area geography column.
    */
   public String[] getAreaFields() {
      return areaFields;
   }

   /**
    * Get the area geography column.
    */
   public String getAreaField() {
      return areaFields.length > 0 ? areaFields[0] : null;
   }

   /**
    * Get the point geography column.
    */
   public String[] getPointFields() {
      return pointFields;
   }

   /**
    * Get the name of the longitude field.
    * @param field the name of the original data column.
    */
   public static String getLongitudeField(String field) {
      return field + LONGITUDE;
   }

   /**
    * Get the name of the latitude field.
    * @param field the name of the original data column.
    */
   public static String getLatitudeField(String field) {
      return field + LATITUDE;
   }

   /**
    * Get the name of the shape field.
    * @param field the name of the original data column.
    */
   public static String getGeoAreaField(String field) {
      return field + GEOAREA;
   }

   /**
    * Get the row count including any padding to include all shapes in the map.
    */
   public int getFullRowCount() {
      init();

      try {
         loadAllShapes();
      }
      catch(Exception ex) {
         LOG.warn("Failed to load shapes", ex);
      }

      return gdata.size();
   }

   /**
    * Get the map shape data.
    */
   public GeoMap getGeoMap() {
      return geomap;
   }

   /**
    * Get the map point data.
    */
   public GeoPoints[] getGeoPoints() {
      return geopoints;
   }

   // DataSet methods

   /**
    * GeoDataSet doesn't support calculated row/column.
    */
   @Override
   public Object getData(int col, int row) {
      if(col < columnCount) {
         init();
         return (row < gdata.size()) ? gdata.get(row)[col] : null;
      }

      return row < getRowCount() ? super.getData(col, row) : null;
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   protected Object getData0(int col, int row) {
      return (row < getDataSet().getRowCount()) ?
         getDataSet().getData(col - columnCount, row) : null;
   }

   /**
    * Return the column header at the specified column.
    * @param col the specified column index.
    * @return the column header at the specified column.
    */
   @Override
   protected String getHeader0(int col) {
      return (col < columnCount) ?
         headers[col] : getDataSet().getHeader(col - columnCount);
   }

   /**
    * Get the data type of the column.
    */
   @Override
   protected Class<?> getType0(String col) {
      for(int i = 0; i < headers.length; i++) {
         if(headers[i].equals(col)) {
            return types[i];
         }
      }

      return getDataSet().getType(col);
   }

   @Override
   protected int indexOfHeader0(String col, boolean all) {
      for(int i = 0; i < headers.length; i++) {
         if(headers[i].equals(col)) {
            return i;
         }
      }

      int idx;

      if(getDataSet() instanceof AbstractDataSet) {
         idx = ((AbstractDataSet) getDataSet()).indexOfHeader(col, all);
      }
      else {
         idx = getDataSet().indexOfHeader(col);
      }

      return idx == -1 ? -1 : idx + columnCount;
   }

   /**
    * Return the number of rows in the chart lens.
    * @return number of rows in the chart lens.
    */
   @Override
   protected int getRowCount0() {
      return getDataSet().getRowCount();
   }

   /**
    * Return the number of columns in the chart lens.
    * @return number of columns in the chart lens.
    */
   @Override
   protected int getColCount0() {
      return getDataSet().getColCount() + columnCount;
   }

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    */
   @Override
   protected boolean isMeasure0(String col) {
      int idx = indexOfHeader0(col);

      if(idx < 0) {
         return false;
      }

      return idx >= columnCount && getDataSet().isMeasure(col);
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    */
   @Override
   protected Comparator<?> getComparator0(String col) {
      int idx = indexOfHeader0(col);
      return (idx < columnCount) ? null : getDataSet().getComparator(col);
   }

   /**
    * Get the base column index on its base data set of the specified column.
    * @param c the specified column index.
    * @return the base column index on base data set, -1 if no base column.
    */
   @Override
   public int getBaseCol(int c) {
      return c - columnCount;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DataSet clone(boolean shallow) {
      GeoDataSet obj = (GeoDataSet) super.clone(shallow);

      obj.areaFields = this.areaFields.clone();
      obj.pointFields = this.pointFields.clone();
      obj.geopoints = this.geopoints.clone();
      obj.gdata = (ArrayList<Object[]>) this.gdata.clone();

      return obj;
   }

   @Override
   public void dispose() {
      super.dispose();
      disposed.set(true);
   }

   @Override
   public boolean isDisposed() {
      return disposed.get() || super.isDisposed();
   }

   /**
    * The longitude column postfix.
    */
   private static final String LONGITUDE = "_longitude_";

   /**
    * The latitude column postfix.
    */
   private static final String LATITUDE = "_latitude_";

   /**
    * The Geoarea column postfix.
    */
   private static final String GEOAREA = "_geoarea_";

   private boolean all = false;
   private boolean loadAll = true;
   private int columnCount = 0;
   private String[] headers = {};
   private Class<?>[] types = {};
   private String[] areaFields;
   private String[] pointFields;
   private GeoMap geomap;
   private GeoPoints[] geopoints;
   private ArrayList<Object[]> gdata = new ArrayList<>();
   private boolean inited = false;
   private final AtomicBoolean disposed = new AtomicBoolean();

   private static final Logger LOG = LoggerFactory.getLogger(GeoDataSet.class);
}
