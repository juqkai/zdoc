package org.nutz.zdoc;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.nutz.am.AmFactory;
import org.nutz.cache.ZCache;
import org.nutz.lang.Lang;
import org.nutz.lang.Streams;
import org.nutz.lang.Strings;
import org.nutz.lang.Xmls;
import org.nutz.lang.util.Callback2;
import org.nutz.lang.util.Context;
import org.nutz.lang.util.MultiLineProperties;
import org.nutz.log.Log;
import org.nutz.log.Logs;
import org.nutz.vfs.ZDir;
import org.nutz.vfs.ZFWalker;
import org.nutz.vfs.ZFile;
import org.nutz.vfs.ZIO;
import org.nutz.zdoc.impl.ZDocParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ZDocHome {

    private static final Log log = Logs.get();

    private ZIO io;

    private ZDir src;

    private ZCache<ZDocHtmlCacheItem> libs;

    private ZCache<ZDocHtmlCacheItem> tmpl;

    private List<ZDir> rss;

    private Context vars;

    private List<ZDocRule> rules;

    private ZDocIndex index;

    // 顶层目录都有哪些文件和目录不要扫描的
    private Set<String> topIgnores;

    public ZDocHome(ZIO io) {
        this.io = io;
        this.libs = new ZCache<ZDocHtmlCacheItem>();
        this.tmpl = new ZCache<ZDocHtmlCacheItem>();
        this.rss = new ArrayList<ZDir>();
        this.topIgnores = new HashSet<String>();
        this.vars = Lang.context();
        this.rules = new ArrayList<ZDocRule>();
        this.index = new ZDocIndex();
    }

    public ZDocHome clear() {
        libs.clear();
        tmpl.clear();
        vars.clear();
        rules.clear();
        index.clear();
        topIgnores.clear();
        return this;
    }

    public ZDir src() {
        return src;
    }

    public ZDocHome src(ZDir dir) {
        if (null != dir) {
            src = dir;
            log.infof("home @ %s", src.path());
            init(src.getFile("zdoc.conf"));
        }
        return this;
    }

    public ZDocHome init(ZFile fconf) {
        // 清除
        log.info("clear caches");
        clear();

        // 如果有配置就分析一下 ...
        if (null != fconf) {
            log.infof("read conf : %s", fconf.name());

            topIgnores.add(fconf.name());

            // 读取配置文件
            MultiLineProperties pp = _read_zdoc_conf(fconf);

            // 开始分析 ...
            _set_cache_item(tmpl, pp.get("zdoc-tmpl"));
            _set_cache_item(libs, pp.get("zdoc-libs"));
            _read_rules(pp);
            _read_rss(pp);
            _read_vars(pp);

        }
        // 没有配置文件，则试图给个默认值
        else {
            _set_cache_item(tmpl, "_tmpl");
            _set_cache_item(libs, "_libs");
            for (String nm : "imgs,js,css".split(",")) {
                if (src.existsDir(nm)) {
                    rss.add(src.getDir(nm));
                    topIgnores.add(nm);
                }
            }
        }

        // 解析索引
        ZFile indexml = src.getFile("index.xml");
        // 根据原生目录结构
        if (null == indexml) {
            _read_index_by_native();
        }
        // 根据给定的 XML 文件
        else {
            _read_index_by_xml(indexml);
        }
        // 开始逐个分析文档
        log.info("walking docs ...");

        // 准备解析器
        final Parser paZDoc = new ZDocParser();
        final AmFactory fa_zdoc = new AmFactory("org/nutz/zdoc/am/zdoc.js");

        // 开始遍历，解析每个文件
        index.walk(new Callback2<ZDocIndex, ZFile>() {
            public void invoke(ZDocIndex zi, ZFile zf) {
                if (!zf.isFile())
                    return;
                String rph = src.relative(zf);

                // ZDoc
                if (zf.matchType("^zdoc|man$")) {
                    log.infof("zdoc: %s", rph);
                    Parsing ing = new Parsing(io.readString(zf));
                    ing.fa = fa_zdoc;
                    paZDoc.build(ing);
                    ing.root.normalize();
                    index.docRoot(ing.root).rawTex(ing.raw);
                }
                // Markdown
                else if (zf.matchType("^md|markdown$")) {
                    log.infof("md: %s", rph);
                    throw Lang.noImplement();
                }
                // HTML
                else if (zf.matchType("^html?$")) {
                    log.infof("html: %s", rph);
                    String html = Streams.readAndClose(io.readString(zf));
                    index.rawTex(html);
                }
            }
        });

        // 返回自身
        return this;
    }

    public ZCache<ZDocHtmlCacheItem> libs() {
        return libs;
    }

    public ZCache<ZDocHtmlCacheItem> tmpl() {
        return tmpl;
    }

    public Context vars() {
        return vars;
    }

    public List<ZDocRule> rules() {
        return rules;
    }

    public ZDocIndex index() {
        return index;
    }

    private void _read_index_by_native() {
        for (ZFile topf : src.ls(true)) {
            // 忽略第一层特殊的目录
            if (topIgnores.contains(topf.name()))
                continue;
            // 目录或者特殊的文件类型会被纳入索引
            if (topf.isDir() || topf.matchType("^zdoc|man|md|markdown|html?$")) {
                ZDocIndex topzi = new ZDocIndex().parent(index);
                _read_index_by_native(topf, topzi);
            }
        }
        _read_index_by_native(src, index);
    }

    private void _read_index_by_native(ZFile zf, ZDocIndex zi) {
        zi.file(zf);
        if (zf.isDir()) {
            for (ZFile subf : ((ZDir) zf).ls(true)) {
                // 目录或者特殊的文件类型会被纳入索引
                if (subf.isDir()
                    || subf.matchType("^zdoc|man|md|markdown|html?$")) {
                    ZDocIndex subzi = new ZDocIndex().parent(zi);
                    _read_index_by_native(subf, subzi);
                }
            }
        }
    }

    private void _read_index_by_xml(ZFile indexml) {
        try {
            Document doc = Lang.xmls().parse(io.read(indexml));
            Element root = doc.getDocumentElement();
            _read_index_by_XmlElement(src, root, index);
        }
        catch (Exception e) {
            throw Lang.wrapThrow(e);
        }
    }

    private void _read_index_by_XmlElement(ZFile zf, Element ele, ZDocIndex zi) {
        // 设置自身的值
        zi.file(zf);
        zi.author(Xmls.getAttr(ele, "author"));
        zi.title(Xmls.getAttr(ele, "title"));

        // 判断是否有子
        List<Element> subeles = Xmls.children(ele, "doc");
        if (!subeles.isEmpty() && !zf.isDir()) {
            throw Lang.makeThrow("'%s' should be a DIR!", zf.path());
        }

        // 循环子节点
        for (Element subele : subeles) {
            ZDocIndex subzi = new ZDocIndex();
            subzi.parent(zi);
            subzi.path(Xmls.getAttr(subele, "path"));
            ZFile subf = ((ZDir) zf).check(subzi.path());
            _read_index_by_XmlElement(subf, subele, subzi);
        }
    }

    private void _read_rss(MultiLineProperties pp) {
        String[] ss = Strings.splitIgnoreBlank(pp.get("zdoc-rs"));
        for (String s : ss) {
            ZDir d = src.getDir(s);
            if (null != d) {
                topIgnores.add(s);
                rss.add(d);
            }
        }
    }

    private void _read_vars(MultiLineProperties pp) {
        String[] ss = Strings.splitIgnoreBlank(pp.get("zdoc-vars"), "\n");
        for (String s : ss) {
            int pos = s.indexOf('=');
            String varName = s.substring(0, pos).trim();
            String valValue = s.substring(pos + 1).trim();
            vars.set(varName, valValue);
        }
    }

    private void _read_rules(MultiLineProperties pp) {
        String[] ss = Strings.splitIgnoreBlank(pp.get("zdoc-rules"), "\n");
        for (String s : ss) {
            int pos = s.lastIndexOf(':');
            String regex = s.substring(0, pos).trim();
            String key = s.substring(pos + 1).trim();
            ZDocRule rule = new ZDocRule();
            rules.add(rule.key(key).regex(regex));
        }
    }

    private MultiLineProperties _read_zdoc_conf(ZFile fconf) {
        BufferedReader br = Streams.buffr(io.readString(fconf));
        MultiLineProperties pp = new MultiLineProperties();
        try {
            pp.load(br);
        }
        catch (IOException e) {
            throw Lang.wrapThrow(e);
        }
        finally {
            Streams.safeClose(br);
        }
        return pp;
    }

    private void _set_cache_item(final ZCache<ZDocHtmlCacheItem> zc,
                                 String fname) {
        if (!Strings.isBlank(fname)) {
            topIgnores.add(fname);
            ZDir d = src.getDir(fname);
            if (null != d) {
                d.walk(true, new ZFWalker() {
                    public boolean invoke(int i, ZFile f) {
                        if (f.isDir())
                            return true;
                        if (!f.name().toLowerCase().matches(".*[.]html?$"))
                            return false;
                        ZDocHtmlCacheItem ci = new ZDocHtmlCacheItem();
                        ci.file(f).html(Streams.readAndClose(io.readString(f)));
                        String key = src.relative(f).replace('/', '.');
                        zc.set(key, ci);
                        return true;
                    }
                });
            }
        }
    }
}
