package run.halo.wereadplugin;

import org.springframework.stereotype.Component;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import run.halo.wereadplugin.extension.WereadBook;
import run.halo.wereadplugin.extension.WereadNote;

import java.util.logging.Logger;

@Component
public class WereadPlugin extends BasePlugin {

    private static final Logger log = Logger.getLogger(WereadPlugin.class.getName());
    private final SchemeManager schemeManager;

    public WereadPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
        // 在构造函数注册以确保索引正确建立
        this.schemeManager.register(WereadBook.class);
        this.schemeManager.register(WereadNote.class);
    }

    @Override
    public void start() {
        log.info("Weread Plugin 插件已启动。");
    }

    @Override
    public void stop() {
        log.info("Weread插件停止！");
    }
}
