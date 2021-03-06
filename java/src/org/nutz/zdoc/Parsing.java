package org.nutz.zdoc;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.nutz.am.Am;
import org.nutz.am.AmFactory;
import org.nutz.am.AmStack;
import org.nutz.am.AmStatus;
import org.nutz.lang.Lang;
import org.nutz.lang.Streams;
import org.nutz.zdoc.am.ZDocAmStack;
import org.nutz.zdoc.am.ZDocParallelAm;

public class Parsing {

    public BufferedReader reader;

    public List<ZDocBlock> blocks;

    public int depth;

    public ZDocNode root;

    public ZDocNode current;

    public AmFactory fa;

    public ZDocAmStack stack;

    public StringBuilder raw;

    public Parsing(Reader reader) {
        this.reader = Streams.buffr(reader);
        this.root = new ZDocNode();
        this.current = root;
        this.blocks = new ArrayList<ZDocBlock>();
        this.stack = new ZDocAmStack(10);
        this.raw = new StringBuilder();
    }

    /**
     * 根据一段字符串填充当前的节点
     * 
     * @param str
     *            字符串
     */
    public void fillCurrentEles(String str) {
        ZDocEle ele = parseString(str);
        // 这种情况需要仅仅加入所有的子 ...
        if (ele.isWrapper()) {
            current.addEles(ele.children());
        }
        // 加入自己就成
        else {
            current.addEle(ele);
        }
    }

    /**
     * 从一个字符串中解析出一个 ZDocEle 对象
     * 
     * @param str
     *            字符串对象
     * @return 节点内容元素对象
     */
    public ZDocEle parseString(String str) {
        char[] cs = str.toCharArray();
        Am<ZDocEle> am = fa.getAm(ZDocParallelAm.class, "zdocParagraph");
        // 准备堆栈
        AmStack<ZDocEle> stack = this.stack.born();
        stack.pushObj(stack.bornObj());
        // 用第一个字符测试...
        if (am.enter(stack, cs[0]) != AmStatus.CONTINUE) {
            throw Lang.impossible();
        }
        // 循环每个字符
        for (int i = 1; i < cs.length; i++) {
            char c = cs[i];
            AmStatus st = stack.eat(c);
            if (AmStatus.CONTINUE != st)
                throw Lang.makeThrow("Fail to parse :\n%s", str);
        }
        // 关闭堆栈得到对象
        return stack.close().normalize();
    }

}
