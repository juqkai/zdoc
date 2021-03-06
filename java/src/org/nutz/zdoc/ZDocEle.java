package org.nutz.zdoc;

import java.util.LinkedList;
import java.util.List;

import org.nutz.css.CssRule;
import org.nutz.json.Json;
import org.nutz.lang.Strings;

public class ZDocEle {

    private ZDocEleType type;

    private String text;

    private ZDocAttrs attrs;

    /**
     * 给 HTML 解析器专用的属性
     */
    private String tagName;

    private ZDocEle parent;

    private List<ZDocEle> children;

    public String toString() {
        return toString(0);
    }

    public String toBrief() {
        return String.format("{%s'%s'[%s](%s)|%d}",
                             type,
                             Strings.sNull(text(), ""),
                             Strings.sNull(href(), ""),
                             Strings.sNull(src(), ""),
                             children.size());
    }

    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        String inds = Strings.dup("    ", indent);
        sb.append(inds);
        sb.append(toBrief());
        indent++;
        for (ZDocEle child : children) {
            sb.append('\n').append(child.toString(indent));
        }
        return sb.toString();
    }

    public ZDocEle() {
        this.type = ZDocEleType.NEW;
        this.attrs = new ZDocAttrs();
        this.tagName = "";
        // this.children = new ArrayList<ZDocEle>(5);
        this.children = new LinkedList<ZDocEle>();
    }

    public ZDocEleType type() {
        return type;
    }

    public ZDocEle type(ZDocEleType type) {
        this.type = type;
        return this;
    }

    /**
     * @return 本元素是否仅仅是一组元素的包裹。<br>
     *         实际上，如果自己没有任何属性，并且文字为空，那么就是了
     */
    public boolean isWrapper() {
        return null == text && (null == attrs || attrs.isEmpty());
    }

    /**
     * 尽力减低本身树的层级
     * 
     * @return 自身
     */
    public ZDocEle normalize() {
        if (children.size() == 1) {
            ZDocEle child = children.remove(0);
            child.normalize();
            margeAttrs(child);
            if (ZDocEleType.NEW != child.type)
                this.type = child.type;
            this.text = Strings.sBlank(child.text(), this.text);
            this.children().addAll(child.children);
        } else if (!children.isEmpty()) {
            for (ZDocEle child : children) {
                child.normalize();
            }
        }
        return this;
    }

    public ZDocEle margeAttrs(ZDocEle ele) {
        this.attrs.putAll(ele.attrs);
        return this;
    }

    public Object attr(String name) {
        return attrs.get(name);
    }

    public String attrString(String name) {
        return attrs.getString(name);
    }

    public int attrInt(String name) {
        return attrs.getInt(name);
    }

    public <T> T attrAs(Class<T> type, String name) {
        return attrs.getAs(type, name);
    }

    public ZDocEle attr(String name, Object value) {
        attrs.set(name, value);
        return this;
    }

    public String attrsAsJson() {
        return Json.toJson(attrs.getInnerMap());
    }

    public String href() {
        return attrString("href");
    }

    public ZDocEle href(String href) {
        return attr("href", href);
    }

    public String src() {
        return attrString("src");
    }

    public ZDocEle src(String src) {
        return attr("src", src);
    }

    public int width() {
        return attrInt("width");
    }

    public ZDocEle width(int width) {
        return attr("width", width);
    }

    public int height() {
        return attrInt("height");
    }

    public ZDocEle height(int height) {
        return attr("height", height);
    }

    public String text() {
        return text;
    }

    public ZDocEle text(String text) {
        this.text = text;
        return this;
    }

    public CssRule style() {
        CssRule style = this.attrAs(CssRule.class, "style");
        if (null == style) {
            style = new CssRule();
            attr("style", style);
        }
        return style;
    }

    public ZDocEle style(String name, String value) {
        style().set(name, value);
        return this;
    }

    public String style(String name) {
        return style().get(name);
    }

    public ZDocEle parent() {
        return parent;
    }

    public boolean isTop() {
        return null == parent;
    }

    public ZDocEle parent(ZDocEle p) {
        parent = p;
        if (null != parent) {
            parent.children.add(this);
        }
        return this;
    }

    public List<ZDocEle> children() {
        return this.children;
    }

    public String tagName() {
        return tagName;
    }

    public ZDocEle tagName(String tagName) {
        this.tagName = tagName.toUpperCase();
        return this;
    }

    /**
     * 根据一个子节点下标路径获取某个子孙节点 <br>
     * 比如:
     * 
     * <pre>
     * tag(1,0)   得到当前节点第二个子节点的第一个子节点
     * tag()      得到当前节点
     * </pre>
     * 
     * @param iPaths
     *            子节点的 index
     * @return 某子孙节点，null 表示不存在
     */
    public ZDocEle ele(int... iPaths) {
        ZDocEle tag = this;
        if (null != iPaths && iPaths.length > 0)
            try {
                for (int i = 0; i < iPaths.length; i++) {
                    tag = tag.children().get(iPaths[i]);
                }
            }
            catch (Exception e) {
                return null;
            }
        return tag;
    }
}
