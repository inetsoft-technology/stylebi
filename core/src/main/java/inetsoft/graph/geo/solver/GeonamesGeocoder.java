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
package inetsoft.graph.geo.solver;

import inetsoft.graph.data.DataSet;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.geom.Point2D;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * <tt>Geocoder</tt> that finds the location corresponding to the input data
 * using the Geonames web service.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public class GeonamesGeocoder {
   /**
    * Creates a new instance of <tt>GeonamesGeocoder</tt>.
    *
    * @param columns the input data columns used to query the web service.
    * @param pattern the pattern used to build the query from the input data
    *                columns. This pattern should be constructed according to
    *                {@link java.util.Formatter}.
    */
   public GeonamesGeocoder(int[] columns, String pattern) {
      this.columns = columns;
      this.pattern = pattern;
      this.cache = new HashMap();
   }

   /**
    * {@inheritDoc}
    */
   public Point2D getFeature(DataSet input, int row) {
      Object[] tuple = new Object[columns.length];

      for(int i = 0; i < columns.length; i++) {
         tuple[i] = input.getData(columns[i], row);
      }

      return getFeature(String.format(pattern, tuple));
   }

   /**
    * {@inheritDoc}
    */
   public synchronized Point2D getFeature(String name) {
      Point2D point;
      String query = Tool.encodeWebURL(name);

      if((point = cache.get(query)) == null) {
         try {
            System.err.println("looking up...");
            URL url = new URL("http://ws.geonames.org/search?q=" + query +
                              "&maxRows=1&type=xml&style=SHORT");
            HttpURLConnection conn =
               (HttpURLConnection) url.openConnection();
            InputStream istream = conn.getInputStream();

            Document doc = CoreTool.parseXML(istream);
            istream.close();

            Element geonames = doc.getDocumentElement();
            Element geoname =
               CoreTool.getChildNodeByTagName(geonames, "geoname");
            Element lat = CoreTool.getChildNodeByTagName(geoname, "lat");
            Element lon = CoreTool.getChildNodeByTagName(geoname, "lng");

            double dlat = Double.parseDouble(CoreTool.getValue(lat));
            double dlon = Double.parseDouble(CoreTool.getValue(lon));

            point = new Point2D.Double(dlon, dlat);
            cache.put(query, point);
         }
         catch(Exception ce) {
            LOG.error("Failed to get location from Geonames web service", ce);
         }
      }

      return point;
   }

   /**
    * {@inheritDoc}
    */
   public String getName(DataSet input, int row) {
      Object[] tuple = new Object[columns.length];

      for(int i = 0; i < columns.length; i++) {
         tuple[i] = input.getData(columns[i], row);
      }

      return String.format(pattern, tuple);
   }

   // @by shirlyg, pass the security scanning
   /**public static void main(String[] args) {
      try {
         Object[][] data = {
            { "City", "State", "Value" },
            { "Piscataway", "NJ", Integer.valueOf(1) },
            { "East Brunswick", "NJ", Integer.valueOf(2) },
            { "New Brunswick", "NJ", Integer.valueOf(3) },
            { "Piscataway", "NJ", Integer.valueOf(4) }
         };

         String pattern = "%s,%s";
         int[] columns = { 0, 1 };
         DataSet dataset = new DefaultDataSet(data);
         GeonamesGeocoder geocoder = new GeonamesGeocoder(columns, pattern);

         for(int i = 0; i < dataset.getRowCount(); i++) {
            Point2D pt = geocoder.getFeature(dataset, i);

            if(pt == null) {
               System.err.format("%s, %s: null",
                                 data[i + 1][0], data[i + 1][1]);
            }
            else {
               System.err.format("%s, %s: %f %f",
                                 data[i + 1][0], data[i + 1][1],
                                 pt.getX(), pt.getY());
            }

            System.err.println();
         }
      }
      catch(Throwable exc) {
         exc.printStackTrace();
      }
   }
   */

   private final int[] columns;
   private final String pattern;
   private final Map<String, Point2D> cache;

   private static final Logger LOG =
      LoggerFactory.getLogger(GeonamesGeocoder.class);
}
