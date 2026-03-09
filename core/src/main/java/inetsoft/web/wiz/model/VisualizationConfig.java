package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VisualizationConfig {
   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public DataSource getData() {
      return data;
   }

   public void setData(DataSource data) {
      this.data = data;
   }

   public BindingInfo getBindingInfo() {
      return bindingInfo;
   }

   public void setBindingInfo(BindingInfo bindingInfo) {
      this.bindingInfo = bindingInfo;
   }

   public List<Layer> getLayers() {
      return layers;
   }

   public void setLayers(List<Layer> layers) {
      this.layers = layers;
   }

   private String title;
   private String description;
   private DataSource data;
   private BindingInfo bindingInfo;
   private List<Layer> layers;

   public static class DataSource {
      public String getSource() {
         return source;
      }

      public void setSource(String source) {
         this.source = source;
      }

      private String source;
   }
}
