import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import com.sun.source.util.TreePath;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 使用 JDK 语法树检查手写 Java，字符串和注解内容按原样解析 */
public class CheckJavaStyle {
    // 从项目根目录运行时，检查后端以及游戏的手写 Java 源码和测试
    private static final List<Path> SOURCE_ROOTS = List.of(
        Path.of("java-server/java-server/src"),
        Path.of("web-vue/games"),
        Path.of("web-vue/tests/fixtures"),
        Path.of("scripts")
    );
    /** 原服务器连接文件沿用用户指定风格，目录清单是跨语言检查的共同依据 */
    private static final Path PRESERVED_FILES = Path.of("scripts/preserved-files.txt");
    private static final List<String> IGNORED_DIRECTORIES = List.of("build", "assets", "lib", "target");
    /** 先分开字符串与注释，再检查注释文字，示例代码和路径字符串保持原义 */
    private static final Pattern SOURCE_TOKENS = Pattern.compile(
        "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/");
    private static final Pattern COMMENT_END = Pattern.compile("[。.]+\\s*(?:\\*/|$)", Pattern.MULTILINE);
    private static final Pattern DEFENSIVE_COMMENT = Pattern.compile("兜底|万一|免得|不值得|不用担心|只好|不是笔误|收摊|攥着");

    /** 收集指定目录下的 Java 文件，子目录递归检查 */
    private static void collectFiles(Path directory, List<Path> files) throws IOException {
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    if (!IGNORED_DIRECTORIES.contains(entry.getFileName().toString())) {
                        collectFiles(entry, files);
                    }
                } else if (entry.toString().endsWith(".java")) {
                    files.add(entry);
                }
            }
        }
    }

    /** 解析全部 Java 文件并检查，失败时返回非零退出码 */
    public static void main(String[] arguments) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : SOURCE_ROOTS) {
            collectFiles(root, files);
        }
        List<String> preserved = Files.readAllLines(PRESERVED_FILES, StandardCharsets.UTF_8);
        for (int index = files.size() - 1; index >= 0; index = index - 1) {
            String file = files.get(index).toString().replace('\\', '/');
            if (preserved.contains(file)) {
                files.remove(index);
            }
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> sources = manager.getJavaFileObjectsFromPaths(files);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, null,
                    List.of("-proc:none", "--release", "21", "-encoding", "UTF-8"), null, sources);
            Iterable<? extends CompilationUnitTree> units = task.parse();
            Trees trees = Trees.instance(task);
            int errorCount = 0;
            for (CompilationUnitTree unit : units) {
                String source = unit.getSourceFile().getCharContent(true).toString();
                StyleScanner scanner = new StyleScanner(unit, trees, source);
                scanner.checkComments();
                scanner.scan(unit, null);
                errorCount = errorCount + scanner.errorCount;
            }
            if (errorCount > 0) {
                throw new IllegalStateException("Java 风格检查发现 " + errorCount + " 处问题");
            }
            System.out.println("Java 风格检查通过，共检查 " + files.size() + " 个文件");
        }
    }

    /** 按语法节点检查回调和运算符，函数说明从紧邻声明的注释读取 */
    private static class StyleScanner extends TreeScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final Trees trees;
        private final String source;
        private int errorCount;

        /** 保存当前文件、源码和位置工具，错误能够定位到具体行 */
        private StyleScanner(CompilationUnitTree unit, Trees trees, String source) {
            this.unit = unit;
            this.trees = trees;
            this.source = source;
        }

        /** 注释保留真实功能边界，句尾和自我辩护词组按同一规则检查 */
        private void checkComments() {
            Matcher tokens = SOURCE_TOKENS.matcher(source);
            while (tokens.find()) {
                String text = tokens.group();
                if (text.startsWith("//") || text.startsWith("/*")) {
                    String message = "";
                    if (COMMENT_END.matcher(text).find()) {
                        message = "注释行末不加句号";
                    } else if (DEFENSIVE_COMMENT.matcher(text).find()) {
                        message = "注释应直接说明功能、设计或边界";
                    }
                    if (!message.isEmpty()) {
                        long line = unit.getLineMap().getLineNumber(tokens.start());
                        System.out.println(unit.getSourceFile().getName() + ":" + line + " " + message);
                        errorCount = errorCount + 1;
                    }
                }
            }
        }

        /** 输出语法节点位置和说明，同时累计错误数量 */
        private void report(Tree node, String message) {
            long position = trees.getSourcePositions().getStartPosition(unit, node);
            long line = unit.getLineMap().getLineNumber(position);
            System.out.println(unit.getSourceFile().getName() + ":" + line + " " + message);
            errorCount = errorCount + 1;
        }

        /** 检查声明前的中文说明，空白和注解之间保留原有说明位置 */
        private void checkExplanation(Tree node) {
            TreePath path = TreePath.getPath(unit, node);
            String documented = trees.getDocComment(path);
            if (documented != null && documented.matches("(?s).*[\\p{IsHan}].*")) {
                return;
            }
            int position = (int) trees.getSourcePositions().getStartPosition(unit, node);
            String prefix = source.substring(0, position).stripTrailing();
            if (prefix.endsWith("*/")) {
                int start = prefix.lastIndexOf("/*");
                if (start >= 0) {
                    String comment = prefix.substring(start);
                    if (comment.matches("(?s).*[\\p{IsHan}].*")) {
                        return;
                    }
                }
            }
            int start = prefix.lastIndexOf('\n') + 1;
            String previousLine = prefix.substring(start).trim();
            if (previousLine.startsWith("//") && previousLine.matches(".*[\\p{IsHan}].*")) {
                return;
            }
            report(node, "声明上方需要中文功能与设计说明：" + node.getKind() + " " + declarationName(node));
        }

        /** 返回声明名称，检查结果可以直接对应方法或类 */
        private String declarationName(Tree node) {
            if (node instanceof MethodTree) {
                return ((MethodTree) node).getName().toString();
            }
            return ((ClassTree) node).getSimpleName().toString();
        }

        /** 检查命名类说明，匿名类另行报告 */
        @Override
        public Void visitClass(ClassTree node, Void unused) {
            if (!node.getSimpleName().toString().isEmpty()) {
                checkExplanation(node);
            }
            boolean methodsStarted = false;
            for (Tree member : node.getMembers()) {
                if (member instanceof MethodTree) {
                    methodsStarted = true;
                } else if (methodsStarted && member instanceof VariableTree) {
                    VariableTree field = (VariableTree) member;
                    if (field.getModifiers().getFlags().contains(Modifier.STATIC)
                            && field.getModifiers().getFlags().contains(Modifier.FINAL)) {
                        report(field, "可配置常量应放在所属类的方法之前");
                    }
                }
            }
            return super.visitClass(node, unused);
        }

        /** 方法和构造器说明需要交代用途及适用的边界 */
        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            checkExplanation(node);
            return super.visitMethod(node, unused);
        }

        /** 匿名回调改用命名函数或命名实现类 */
        @Override
        public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
            report(node, "请使用命名实现类处理回调");
            return super.visitLambdaExpression(node, unused);
        }

        /** 方法引用使用直接调用或命名实现类表达 */
        @Override
        public Void visitMemberReference(MemberReferenceTree node, Void unused) {
            report(node, "请使用明确的方法调用");
            return super.visitMemberReference(node, unused);
        }

        /** 匿名类使用独立名称表达职责 */
        @Override
        public Void visitNewClass(NewClassTree node, Void unused) {
            if (node.getClassBody() != null) {
                report(node, "实现类需要明确名称");
            }
            return super.visitNewClass(node, unused);
        }

        /** 三目表达式使用分支说明每种结果 */
        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree node, Void unused) {
            report(node, "请使用 if / else");
            return super.visitConditionalExpression(node, unused);
        }

        /** 复合赋值使用普通赋值表达计算过程 */
        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree node, Void unused) {
            report(node, "请使用普通赋值");
            return super.visitCompoundAssignment(node, unused);
        }

        /** 自增自减改成普通赋值，其余一元运算沿用原含义 */
        @Override
        public Void visitUnary(UnaryTree node, Void unused) {
            Tree.Kind kind = node.getKind();
            if (kind == Tree.Kind.PREFIX_INCREMENT || kind == Tree.Kind.POSTFIX_INCREMENT
                    || kind == Tree.Kind.PREFIX_DECREMENT || kind == Tree.Kind.POSTFIX_DECREMENT) {
                report(node, "请使用普通赋值");
            }
            return super.visitUnary(node, unused);
        }
    }
}
