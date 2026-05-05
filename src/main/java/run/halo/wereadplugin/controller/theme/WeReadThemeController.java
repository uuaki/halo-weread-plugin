package run.halo.wereadplugin.controller.theme;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.wereadplugin.extension.WereadBook;

import java.util.List;

@RestController
@RequestMapping("/api/ext/halo-weread-plugin")
public class WeReadThemeController {

    private final ReactiveExtensionClient extensionClient;

    public WeReadThemeController(ReactiveExtensionClient extensionClient) {
        this.extensionClient = extensionClient;
    }

    /**
     * 供博客主页、主题前端等调用的专属 API，用于展示微信读书获取到的所有读过的书籍。
     */
    @GetMapping("/books")
    public Mono<List<WereadBook>> listBooks() {
        return extensionClient.list(WereadBook.class, e -> true, (e1, e2) -> {
            // 按照阅读更新时间倒序排列
            Long t1 = e1.getSpec().getLastReadTime();
            Long t2 = e2.getSpec().getLastReadTime();
            return Long.compare(t2 != null ? t2 : 0L, t1 != null ? t1 : 0L);
        }).collectList();
    }

    /**
     * 辅助方法：返回一个极具设计感的 HTML 片段，遵循高端前端设计规范。
     * 设计风格：现代简约 + 玻璃拟态 + 呼吸感微交互
     */
    @GetMapping("/shelf-html")
    public Mono<String> getShelfHtml() {
        return listBooks().map(books -> {
            // 按年份分组
            java.util.Map<String, List<WereadBook>> yearGroups = new java.util.TreeMap<>(java.util.Collections.reverseOrder());
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy");
            java.text.SimpleDateFormat fullSdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

            for (WereadBook book : books) {
                Long readTime = book.getSpec().getLastReadTime();
                String year = readTime != null ? sdf.format(new java.util.Date(readTime)) : "其他";
                yearGroups.computeIfAbsent(year, k -> new java.util.ArrayList<>()).add(book);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("<style>");
            sb.append("@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600&display=swap');");
            sb.append(".wr-container { font-family: 'Outfit', system-ui, sans-serif; --wr-accent: #3b82f6; --wr-gray: #f9fafb; color: #111827; }");
            sb.append(".wr-year-group { margin-bottom: 40px; }");
            sb.append(".wr-year-title { font-size: 1.5rem; font-weight: 600; margin-bottom: 20px; border-left: 4px solid var(--wr-accent); padding-left: 15px; }");
            sb.append(".wr-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }");
            sb.append(".wr-card { display: flex; background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 12px; transition: all 0.3s; cursor: pointer; position: relative; }");
            sb.append(".wr-card:hover { border-color: var(--wr-accent); box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05); transform: translateY(-2px); }");
            sb.append(".wr-card-cover { width: 85px; height: 120px; object-fit: cover; border-radius: 6px; box-shadow: 0 4px 6px rgba(0,0,0,0.05); flex-shrink: 0; }");
            sb.append(".wr-card-content { margin-left: 15px; flex-grow: 1; display: flex; flex-direction: column; justify-content: space-between; overflow: hidden; }");
            sb.append(".wr-card-title { font-weight: 600; font-size: 1.05rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: var(--wr-accent); }");
            sb.append(".wr-card-author { font-size: 0.85rem; color: #6b7280; margin: 4px 0; overflow: hidden; text-overflow: ellipsis; }");
            sb.append(".wr-tag-row { display: flex; gap: 8px; margin-top: 5px; }");
            sb.append(".wr-tag { background: #f3f4f6; color: #4b5563; font-size: 0.75rem; padding: 2px 8px; border-radius: 4px; font-weight: 500; }");
            sb.append(".wr-stats-row { font-size: 0.8rem; color: #4b5563; margin-top: 10px; }");
            sb.append(".wr-time-row { font-size: 0.8rem; color: #9ca3af; margin-top: 5px; }");
            
            // 详情弹窗样式
            sb.append(".wr-modal { display:none; position:fixed; z-index:1000; left:0; top:0; width:100%; height:100%; background: rgba(0,0,0,0.5); backdrop-filter: blur(4px); align-items:center; justify-content:center; }");
            sb.append(".wr-modal-content { background:white; width:90%; max-width:600px; max-height:80vh; border-radius:16px; position:relative; overflow-y:auto; padding:30px; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.25); }");
            sb.append(".wr-close { position:absolute; right:20px; top:15px; font-size:24px; cursor:pointer; color:#9ca3af; }");
            sb.append("</style>");

            sb.append("<div class='wr-container'>");
            for (java.util.Map.Entry<String, List<WereadBook>> entry : yearGroups.entrySet()) {
                sb.append("  <div class='wr-year-group'>");
                sb.append("    <h2 class='wr-year-title'>").append(entry.getKey()).append(" 年</h2>");
                sb.append("    <div class='wr-grid'>");
                for (WereadBook book : entry.getValue()) {
                    WereadBook.Spec spec = book.getSpec();
                    String lastReadAt = spec.getLastReadTime() != null ? fullSdf.format(new java.util.Date(spec.getLastReadTime())) : "未知";
                    sb.append("<div class='wr-card' onclick='window.showBookDetail(\"").append(book.getMetadata().getName()).append("\")'>");
                    sb.append("  <img class='wr-card-cover' src='").append(spec.getCover()).append("'>");
                    sb.append("  <div class='wr-card-content'>");
                    sb.append("    <div class='wr-card-title'>").append(spec.getTitle()).append("</div>");
                    sb.append("    <div class='wr-card-author'>").append(spec.getAuthor()).append("</div>");
                    sb.append("    <div class='wr-tag-row'>");
                    sb.append("      <span class='wr-tag'>已同步</span><span class='wr-tag'>图书</span>");
                    if (spec.getReadInfo() != null && spec.getReadInfo() == 3) sb.append("<span class='wr-tag'>已读完</span>");
                    else sb.append("<span class='wr-tag'>在读</span>");
                    sb.append("    </div>");
                    sb.append("    <div class='wr-stats-row'>划线 ").append(spec.getNoteCount() != null ? spec.getNoteCount() : 0).append(" · 想法 ").append(spec.getReviewCount() != null ? spec.getReviewCount() : 0).append("</div>");
                    sb.append("    <div class='wr-time-row'>最近阅读 ").append(lastReadAt).append("</div>");
                    sb.append("  </div>");
                    sb.append("</div>");
                }
                sb.append("    </div>");
                sb.append("  </div>");
            }
            sb.append("</div>");

            // 详情弹窗 HTML + JS
            sb.append("<div id='wrModal' class='wr-modal' onclick='if(event.target==this)this.style.display=\"none\"'>");
            sb.append("  <div class='wr-modal-content'>");
            sb.append("    <span class='wr-close' onclick='document.getElementById(\"wrModal\").style.display=\"none\"'>&times;</span>");
            sb.append("    <div id='wrModalBody'>加载中...</div>");
            sb.append("  </div>");
            sb.append("</div>");

            sb.append("<script>");
            sb.append("window.showBookDetail = function(resourceName) {");
            sb.append("  const modal = document.getElementById('wrModal');");
            sb.append("  const body = document.getElementById('wrModalBody');");
            sb.append("  modal.style.display = 'flex';");
            sb.append("  body.innerHTML = '<div style=\"text-align:center;padding:50px;\">已加载该书籍元数据，划线与评论同步功能持续开发中...</div>';");
            // 这里可以预留接口拉取具体笔记，目前先展示基本信息
            sb.append("};");
            sb.append("</script>");

            return sb.toString();
        });
    }
}
