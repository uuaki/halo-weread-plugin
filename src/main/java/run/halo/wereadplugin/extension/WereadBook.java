package run.halo.wereadplugin.extension;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * 微信读书书籍
 * @author haike
 * @date 2026-04-23
 * @version 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(group = "run.halo.plugin.wereadplugin",
     version = "v1beta1",
     kind = "WereadBook",
     plural = "wereadbooks",
     singular = "wereadbook")
public class WereadBook extends AbstractExtension {

    private Spec spec;

    @Data
    public static class Spec {
        private String bookId;
        private String title;
        private String author;
        private String cover;
        private String pcUrl;
        private String intro;
        private String publisher;
        private String publishTime;
        private String isbn;
        private String category;
        private Integer totalWords;
        
        private Integer readInfo;
        private Double progress;
        private Integer readingTime;
        
        private Integer noteCount;
        private Integer reviewCount;
        
        private Long lastReadTime;
        private Long finishTime;

        private Boolean hidden = false;
    }
}
