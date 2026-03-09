package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageBinding extends OutputBinding {
   public String getImage() {
      return image;
   }

   public void setImage(String image) {
      this.image = image;
   }

   private String image;
}
