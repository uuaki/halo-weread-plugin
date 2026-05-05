package run.halo.wereadplugin.extension;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * 微信读书笔记
 * @author haike
 * @date 2026-04-23
 * @version 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(group = "run.halo.plugin.wereadplugin",
     version = "v1beta1",
     kind = "WereadNote",
     plural = "wereadnotes",
     singular = "wereadnote")
public class WereadNote extends AbstractExtension {
    private String bookId;
    private String content;
}
