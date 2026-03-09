package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Layer {
   public String getField() {
      return field;
   }

   public void setField(String field) {
      this.field = field;
   }

   /** "U.S" | "Asia" | "Canada" | "Europe" | "Mexico" */
   public String getMap() {
      return map;
   }

   public void setMap(String map) {
      this.map = map;
   }

   /** "State" | "City" | "Zip" | "Country" | "Province" */
   public String getLayer() {
      return layer;
   }

   public void setLayer(String layer) {
      this.layer = layer;
   }

   private String field;
   /** "U.S" | "Asia" | "Canada" | "Europe" | "Mexico" */
   private String map;
   /** "State" | "City" | "Zip" | "Country" | "Province" */
   private String layer;
}
