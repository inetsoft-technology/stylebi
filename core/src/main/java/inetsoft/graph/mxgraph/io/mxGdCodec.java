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
package inetsoft.graph.mxgraph.io;

import inetsoft.graph.mxgraph.model.mxGraphModel;
import inetsoft.graph.mxgraph.view.mxGraph;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses a GD .txt file and imports it in the given graph.<br/>
 * This class depends from the classes contained in
 * inetsoft.graph.mxgraph.io.gd.
 */
public class mxGdCodec {
   private static final Logger log = Logger.getLogger(mxGdCodec.class.getName());
   /**
    * Map with the vertex cells added in the addNode method.
    */
   protected static HashMap<String, Object> cellsMap = new HashMap<String, Object>();

   /**
    * Parses simple GD format and populate the specified graph
    *
    * @param input GD file to be parsed
    * @param graph Graph where the parsed graph is included.
    */
   public static void decode(String input, mxGraph graph)
   {
      BufferedReader br = new BufferedReader(new StringReader(input));
      mxGDParseState state = mxGDParseState.START;
      Object parent = graph.getDefaultParent();

      graph.getModel().beginUpdate();

      try {
         String line = br.readLine().trim();
         while(line != null) {
            switch(state) {
            case START: {
               if(!line.startsWith("#")) {
                  state = mxGDParseState.NUM_NODES;
               }
               else {
                  break;
               }
            }
            case NUM_NODES: {
               if(!line.startsWith("#")) {
                  int numVertices = Integer.valueOf(line);

                  for(int i = 0; i < numVertices; i++) {
                     String label = String.valueOf(i);
                     Object vertex = graph.insertVertex(parent, label, label,
                                                        0, 0, 10, 10);

                     cellsMap.put(label, vertex);
                  }
               }
               else {
                  state = mxGDParseState.PARSING_EDGES;
               }

               break;
            }
            case PARSING_NODES: {
               if(line.startsWith("# Edges")) {
                  state = mxGDParseState.PARSING_EDGES;
               }
               else if(!line.equals("")) {
                  String[] items = line.split(",");
                  if(items.length != 5) {
                     throw new Exception("Error in parsing");
                  }
                  else {
                     double x = Double.valueOf(items[1]);
                     double y = Double.valueOf(items[2]);
                     double width = Double.valueOf(items[3]);
                     double height = Double.valueOf(items[4]);


                     //Set the node name as label.
                     String label = items[0];

                     //Insert a new vertex in the graph
                     Object vertex = graph.insertVertex(parent, label, label,
                                                        x - width / 2.0, y - height / 2.0, width,
                                                        height);

                     cellsMap.put(label, vertex);
                  }
               }
               break;
            }
            case PARSING_EDGES: {
               if(!line.equals("")) {
                  String[] items = line.split(" ");
                  if(items.length != 2) {
                     throw new Exception("Error in parsing");
                  }
                  else {
                     Object source = cellsMap.get(items[0]);
                     Object target = cellsMap.get(items[1]);

                     graph.insertEdge(parent, null, "", source, target);
                  }
               }
               break;
            }
            }

            line = br.readLine();
         }
      }

      catch(Exception e) {
         log.log(Level.FINEST, "Failed to decode", e);
      }
      finally {
         graph.getModel().endUpdate();
      }
   }

   /**
    * Generates a GD text output with the cells in the graph.
    * The implementation only uses the cells located in the default parent.
    *
    * @param graph Graph with the cells.
    *
    * @return The GD document generated.
    */
   public static String encode(mxGraph graph)
   {
      StringBuilder builder = new StringBuilder();

      Object parent = graph.getDefaultParent();
      Object[] vertices = mxGraphModel.getChildCells(graph.getModel(), parent, true, false);

      builder.append("# Number of Nodes (0-" + (vertices.length - 1) + ")");
      builder.append(vertices.length);

      // TODO

      return builder.toString();
   }

   /**
    * Represents the different states in the parse of a file.
    */
   public enum mxGDParseState {
      START, NUM_NODES, PARSING_NODES, PARSING_EDGES
   }
}
