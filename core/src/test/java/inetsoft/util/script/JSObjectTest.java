/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.util.script;

import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.*;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.text.*;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JSObjectTest {

   private Scriptable scope;
   private JSObject jsObject;

   @BeforeEach
   void setUp() {
      Context context = Context.enter();
      try {
         scope = context.initStandardObjects(); // Initialize a proper top-level scope
         jsObject = new JSObject(scope, new Object(), Object.class);
      }
      finally {
         Context.exit();
      }
   }

   @Test
   void testGet() {
      // Test prohibited methods
      assertThrows(RuntimeException.class, () -> jsObject.get("getClass", scope));
      assertThrows(RuntimeException.class, () -> jsObject.get("getClassLoader", scope));
   }

   @Test
   void testConvertToString() {
      // Test conversion to String
      assertEquals("123", JSObject.convert(123, String.class));

      // Test convert Array to String
      String[] array = { "a", "b", "c" };
      assertEquals("a,b,c", JSObject.convert(array, String.class));

      // null value
      assertNull(JSObject.convert(null, String.class));
   }

   @Test
   void testConvertToColor() {
      // Test conversion from hex string to Color
      Color color = (Color) JSObject.convert("#FF0000", Color.class);
      assertEquals(new Color(255, 0, 0), color);

      // Test conversion from RGB array to Color
      Object[] rgbArray = { 100, 100, 0 };
      color = (Color) JSObject.convert(rgbArray, Color.class);
      assertEquals(new Color(100, 100, 0), color);

      // Test with a valid integer
      color = (Color) JSObject.convert(0x00FF00, Color.class);
      assertEquals(new Color(0, 255, 0), color);

      // Test with a NativeObject containing RGB values
      NativeObject nativeObject = new NativeObject();
      nativeObject.put("r", nativeObject, 0);
      nativeObject.put("g", nativeObject, 0);
      nativeObject.put("b", nativeObject, 255);
      color = (Color) JSObject.convert(nativeObject, Color.class);
      assertEquals(new Color(0, 0, 255), color);

      // Test with Color[].class
      Color[] colors = (Color[]) JSObject.convert(
         new Object[]{ "red", new Color(0, 255, 0) }, Color[].class);
      assertEquals(2, colors.length);
      assertEquals(new Color(255, 0, 0), colors[0]);
      assertEquals(new Color(0, 255, 0), colors[1]);
   }

   @Test
   void testConvertToFont() {
      // Test with a valid font string
      String fontString = "Arial-BOLD-12";
      Font font = (Font) JSObject.convert(fontString, Font.class);
      assertNotNull(font);
      assertEquals("Arial", font.getName());
      assertEquals(Font.BOLD, font.getStyle());
      assertEquals(12, font.getSize());
   }

   @Test
   void testConvertToInsets() {
      // Test with NativeObject containing top, left, bottom, and right
      NativeObject nativeObject = new NativeObject();
      nativeObject.put("top", nativeObject, 10);
      nativeObject.put("left", nativeObject, 20);
      nativeObject.put("bottom", nativeObject, 30);
      nativeObject.put("right", nativeObject, 40);
      Insets insets = (Insets) JSObject.convert(nativeObject, Insets.class);
      assertNotNull(insets);
      assertEquals(10, insets.top);
      assertEquals(20, insets.left);
      assertEquals(30, insets.bottom);
      assertEquals(40, insets.right);

      // Test with an array containing top, left, bottom, and right
      Object[] insetsArray = { 15, 25, 35, 45 };
      insets = (Insets) JSObject.convert(insetsArray, Insets.class);
      assertNotNull(insets);
      assertEquals(15, insets.top);
      assertEquals(25, insets.left);
      assertEquals(35, insets.bottom);
      assertEquals(45, insets.right);
   }

   @Test
   void testConvertToDimension() {
      // Test with NativeObject containing width and height
      NativeObject nativeObject = new NativeObject();
      nativeObject.put("width", nativeObject, 100);
      nativeObject.put("height", nativeObject, 200);
      Dimension dimension = (Dimension) JSObject.convert(nativeObject, Dimension.class);
      assertEquals(new Dimension(100, 200), dimension);

      // Test with an array containing width and height
      Object[] dimensionArray = { 300, 400 };
      dimension = (Dimension) JSObject.convert(dimensionArray, Dimension.class);
      assertEquals(new Dimension(300, 400), dimension);
   }

   @Test
   void testConvertToPoint() {
      // Test with NativeObject containing x and y
      NativeObject nativeObject = new NativeObject();
      nativeObject.put("x", nativeObject, 10);
      nativeObject.put("y", nativeObject, 20);
      Point point = (Point) JSObject.convert(nativeObject, Point.class);
      assertNotNull(point);
      assertEquals(10, point.x);
      assertEquals(20, point.y);

      // Test with NativeObject containing row and column
      nativeObject = new NativeObject();
      nativeObject.put("row", nativeObject, 30);
      nativeObject.put("column", nativeObject, 40);
      point = (Point) JSObject.convert(nativeObject, Point.class);
      assertNotNull(point);
      assertEquals(40, point.x);
      assertEquals(30, point.y);

      // Test with array containing x and y
      Object[] pointArray = { 50, 60 };
      point = (Point) JSObject.convert(pointArray, Point.class);
      assertNotNull(point);
      assertEquals(50, point.x);
      assertEquals(60, point.y);
   }

   @Test
   void testConvertToShape() {
      // Test with NativeObject for Ellipse
      NativeObject ellipseObject = new NativeObject();
      ellipseObject.put("type", ellipseObject, "ellipse");
      ellipseObject.put("x", ellipseObject, 10);
      ellipseObject.put("y", ellipseObject, 20);
      ellipseObject.put("width", ellipseObject, 30);
      ellipseObject.put("height", ellipseObject, 40);
      Shape ellipse = (Shape) JSObject.convert(ellipseObject, Shape.class);
      assertTrue(ellipse instanceof Ellipse2D.Double);
      Ellipse2D.Double ellipse2D = (Ellipse2D.Double) ellipse;
      assertEquals(10, ellipse2D.x, 0.01);
      assertEquals(20, ellipse2D.y, 0.01);
      assertEquals(30, ellipse2D.width, 0.01);
//      assertEquals(40, ellipse2D.height, 0.01); //bug #71459

      // Test with NativeObject for Rectangle
      NativeObject rectangleObject = new NativeObject();
      rectangleObject.put("x", rectangleObject, 50);
      rectangleObject.put("y", rectangleObject, 60);
      rectangleObject.put("width", rectangleObject, 70);
      rectangleObject.put("height", rectangleObject, 80);
      Shape rectangle = (Shape) JSObject.convert(rectangleObject, Shape.class);
      assertTrue(rectangle instanceof Rectangle);
      Rectangle rect = (Rectangle) rectangle;
      assertEquals(50, rect.x);
      assertEquals(60, rect.y);
      assertEquals(70, rect.width);
//      assertEquals(80, rect.height); //bug #71459

      // Test with array for Circle
      Object[] circleArray = { 15.0, 25.0, 35.0 };
      Shape circle = (Shape) JSObject.convert(circleArray, Shape.class);
      assertTrue(circle instanceof Ellipse2D.Double);
      Ellipse2D.Double circle2D = (Ellipse2D.Double) circle;
      assertEquals(15.0, circle2D.x, 0.01);
      assertEquals(25.0, circle2D.y, 0.01);
      assertEquals(70.0, circle2D.width, 0.01); // Diameter = radius * 2
      assertEquals(70.0, circle2D.height, 0.01);

      // Test with array for Rectangle
      Object[] rectangleArray = { 5.0, 10.0, 15.0, 20.0 };
      Shape rectFromArray = (Shape) JSObject.convert(rectangleArray, Shape.class);
      assertTrue(rectFromArray instanceof Rectangle);
      Rectangle rectArray = (Rectangle) rectFromArray;
      assertEquals(5, rectArray.x);
      assertEquals(10, rectArray.y);
      assertEquals(15, rectArray.width);
      assertEquals(20, rectArray.height);

      // Test with array for Polygon
      Object[] polygonArray = { 0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0 };
      Shape polygon = (Shape) JSObject.convert(polygonArray, Shape.class);
      assertTrue(polygon instanceof Polygon);
      Polygon poly = (Polygon) polygon;
      assertEquals(4, poly.npoints);
      assertArrayEquals(new int[]{ 0, 10, 10, 0 }, poly.xpoints);
      assertArrayEquals(new int[]{ 0, 0, 10, 10 }, poly.ypoints);
   }

   @Test
   void testConvertToNumbers() {
      // Test conversion to DecimalFormat
      DecimalFormat format = (DecimalFormat) JSObject.convert("#,###.##", NumberFormat.class);
      assertNotNull(format);
      assertEquals("#,##0.##", format.toPattern());

      // Test conversion to int
      assertEquals(123, JSObject.convert(123, int.class));
      assertEquals(456, JSObject.convert("456", Integer.class));

      // Test conversion to float
      assertEquals(123.45f, JSObject.convert(123.45, Float.class));
      assertEquals(678.9f, JSObject.convert("678.9", float.class));

      // Test conversion to double
      assertEquals(123.45, JSObject.convert(123.45, Double.class));
      assertEquals(678.9, JSObject.convert("678.9", double.class));
      assertEquals(100.0, JSObject.convert((Number) 100, double.class));
      assertEquals(110.0, JSObject.convert("110", Number.class));

      // Test conversion to int[]
      Object[] intArray = { 1, 2, 3 };
      int[] expectedArray = { 1, 2, 3 };
      assertArrayEquals(expectedArray, (int[]) JSObject.convert(intArray, int[].class));

      // Test conversion to double[]
      Object[] javaArray = new Object[]{ 4.4, 5.5, 6.6 };
      double[] result = (double[]) JSObject.convert(javaArray, double[].class);
      assertArrayEquals(new double[]{ 4.4, 5.5, 6.6 }, result, 0.01);
   }

   @Test
   void testConvertToSQLDateTypes() {
      // Test conversion to java.sql.Date
      Date utilDate = new Date();
      java.sql.Date sqlDate = (java.sql.Date) JSObject.convert(utilDate, java.sql.Date.class);
      assertNotNull(sqlDate);
      assertEquals(utilDate.getTime(), sqlDate.getTime());

      // Test conversion to java.sql.Timestamp
      java.sql.Timestamp sqlTimestamp = (java.sql.Timestamp) JSObject.convert(utilDate, java.sql.Timestamp.class);
      assertNotNull(sqlTimestamp);
      assertEquals(utilDate.getTime(), sqlTimestamp.getTime());

      // Test conversion to java.sql.Time
      java.sql.Time sqlTime = (java.sql.Time) JSObject.convert(utilDate, java.sql.Time.class);
      assertNotNull(sqlTime);
      assertEquals(utilDate.getTime(), sqlTime.getTime());
   }

   @Test
   void testConvertToOtherFormat() {
      // Test conversion to DateFormat
      DateFormat dateFormat = (DateFormat) JSObject.convert("yyyy-MM-dd", DateFormat.class);
      assertNotNull(dateFormat);
      assertEquals("yyyy-MM-dd", ((SimpleDateFormat) dateFormat).toPattern());

      // Test conversion to MessageFormat
      MessageFormat messageFormat = (MessageFormat) JSObject.convert("Hello {0}",
                                                                     java.text.MessageFormat.class);
      assertNotNull(messageFormat);
      assertEquals("Hello {0}", messageFormat.toPattern());

      // Test conversion to DecimalFormat
      Object format = JSObject.convert("#.##%", Format.class);
      assertNotNull(format);
      assertTrue(format instanceof DecimalFormat);
      assertEquals("#0.##%", ((DecimalFormat) format).toPattern());

      // Test conversion to DateFormat
      format = JSObject.convert("yyyy-MM-dd", Format.class);
      assertNotNull(format);
      assertTrue(format instanceof DateFormat);
      assertEquals("yyyy-MM-dd", ((SimpleDateFormat) format).toPattern());
   }

   @Test
   void testSplit() {
      // Test with NativeArray
      NativeArray nativeArray = new NativeArray(new Object[]{ "a", "b", "c" });
      Object[] result = JSObject.split(nativeArray);
      assertArrayEquals(new Object[]{ "a", "b", "c" }, result);

      // Test with Java array
      Object[] javaArray = new Object[]{ "x", "y", "z" };
      result = JSObject.split(javaArray);
      assertArrayEquals(new Object[]{ "x", "y", "z" }, result);

      // Test with comma-separated string
      String csv = "1,2,3";
      result = JSObject.split(csv);
      assertArrayEquals(new Object[]{ "1", "2", "3" }, result);
   }

   @Test
   void testSplitN() {
      // Test with a NativeArray
      NativeArray nativeArray = new NativeArray(new Object[]{ 1, 2.5, 3 });
      double[] result = JSObject.splitN(nativeArray);
      assertArrayEquals(new double[]{ 1.0, 2.5, 3.0 }, result);

      // Test with a Java array
      Object[] javaArray = new Object[]{ 4, 5.5, 6 };
      result = JSObject.splitN(javaArray);
      assertArrayEquals(new double[]{ 4.0, 5.5, 6.0 }, result);

      // Test with a comma-separated string
      String csv = "7,8.5,9";
      result = JSObject.splitN(csv);
      assertArrayEquals(new double[]{ 7.0, 8.5, 9.0 }, result);

      // Test with invalid input (non-numeric values)
      String invalidCsv = "10,abc,12";
      result = JSObject.splitN(invalidCsv);
      assertArrayEquals(new double[]{ 10.0, 0.0, 12.0 }, result); // Defaults to 0 for invalid values
   }

   @Test
   void testIsArray() {
      assertTrue(JSObject.isArray(new int[]{ 1, 2, 3 }));
      assertFalse(JSObject.isArray("not an array"));

      // Test with a NativeArray
      assertFalse(JSObject.isArray(new NativeObject()));
   }

   @Test
   void testConvertToVariableTable() throws Exception {
      // Case 1: Input is already a VariableTable
      VariableTable inputTable = new VariableTable();
      inputTable.put("key1", "value1");
      VariableTable result = JSObject.convertToVariableTable(inputTable, new VariableTable(), true);
      assertEquals("value1", result.get("key1"));

      // Case 2: Input is a Scriptable array of key-value pairs
      NativeArray scriptableArray = new NativeArray(2);
      NativeObject pair1 = new NativeObject();
      pair1.put(0, pair1, "key2");
      pair1.put(1, pair1, "value2");
      scriptableArray.put(0, scriptableArray, pair1);

      NativeObject pair2 = new NativeObject();
      pair2.put(0, pair2, "key3");
      pair2.put(1, pair2, null); // Null value
      scriptableArray.put(1, scriptableArray, pair2);

      result = JSObject.convertToVariableTable(scriptableArray, new VariableTable(), false);
      assertEquals("value2", result.get("key2"));
      assertNull(result.get("key3")); // Null values are not kept when keepNull is false

      // Case 3: Invalid input (non-Scriptable item in array)
      NativeArray invalidArray = new NativeArray(1);
      invalidArray.put(0, invalidArray, "invalidItem");
      VariableTable vars = new VariableTable();
      result = JSObject.convertToVariableTable(invalidArray, vars, true);
      assertEquals(0, vars.size()); // No valid key-value pairs added
   }
}