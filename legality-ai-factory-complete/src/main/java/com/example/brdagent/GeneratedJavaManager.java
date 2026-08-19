package com.example.brdagent;

import com.example.legality.runtime.LegalityRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.tools.*;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GeneratedJavaManager {
    private final ObjectMapper mapper = new ObjectMapper();

    public int materializeRuleCodingJson(Path workspace, String ruleCodingJson) throws Exception {
        JsonNode root = mapper.readTree(ruleCodingJson);
        JsonNode files = root.path("generatedFiles");
        if (!files.isArray()) throw new IllegalArgumentException("Rule Coding JSON must contain generatedFiles array");
        Path generatedRoot = workspace.resolve("generated-java");
        deleteChildren(generatedRoot); Files.createDirectories(generatedRoot);
        int count=0;
        for (JsonNode f: files) {
            String fileName = safeFileName(text(f,"fileName","GeneratedRule"+count+".java"));
            String source = normalizeSource(text(f,"sourceCode",""));
            if (!fileName.endsWith(".java") || source.isBlank()) continue;
            String pkg=packageName(source,"com.example.legality.generated");
            Path dir=generatedRoot.resolve(pkg.replace('.', File.separatorChar)); Files.createDirectories(dir);
            Files.writeString(dir.resolve(fileName),source); count++;
        }
        return count;
    }

    public CompileResult compileGeneratedRules(Path workspace) throws Exception {
        Path src=workspace.resolve("generated-java"); Path classes=workspace.resolve("compiled-rules");
        deleteChildren(classes); Files.createDirectories(classes);
        if(!Files.exists(src)) return new CompileResult(false,0,classes,"No generated-java folder found.");
        List<File> javaFiles=new ArrayList<>();
        try(var st=Files.walk(src)){ st.filter(p->p.toString().endsWith(".java")).forEach(p->javaFiles.add(p.toFile())); }
        if(javaFiles.isEmpty()) return new CompileResult(false,0,classes,"No .java files found under "+src);
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null) return new CompileResult(false,javaFiles.size(),classes,"JDK compiler unavailable. Run Eclipse/app with a JDK, not JRE.");
        DiagnosticCollector<JavaFileObject> dc=new DiagnosticCollector<>();
        try(StandardJavaFileManager fm=compiler.getStandardFileManager(dc,null,null)){
            List<String> opts=List.of("-classpath",System.getProperty("java.class.path"),"-d",classes.toString(),"-Xlint:none");
            Boolean ok=compiler.getTask(null,fm,dc,opts,null,fm.getJavaFileObjectsFromFiles(javaFiles)).call();
            StringBuilder diag=new StringBuilder();
            for(Diagnostic<? extends JavaFileObject> d:dc.getDiagnostics()){
                String source=d.getSource()==null?"?":new File(d.getSource().toUri()).getName();
                diag.append(source).append(':').append(d.getLineNumber()).append(" [").append(d.getKind()).append("] ").append(d.getMessage(null)).append('\n');
            }
            if(Boolean.TRUE.equals(ok) && diag.length()==0) diag.append("Compilation successful.");
            return new CompileResult(Boolean.TRUE.equals(ok),javaFiles.size(),classes,diag.toString());
        }
    }

    public String sourceBundle(Path workspace) throws Exception {
        Path src=workspace.resolve("generated-java"); StringBuilder b=new StringBuilder();
        if(!Files.exists(src)) return "";
        try(var st=Files.walk(src)){
            for(Path p:st.filter(x->x.toString().endsWith(".java")).sorted().toList()){
                b.append("\n===== FILE: ").append(src.relativize(p)).append(" =====\n").append(Files.readString(p)).append('\n');
            }
        }
        return b.toString();
    }

    public int applyCompileFixJson(Path workspace, String fixJson) throws Exception {
        JsonNode root=mapper.readTree(fixJson);
        if("HUMAN_REVIEW_REQUIRED".equalsIgnoreCase(text(root,"status",""))) return 0;
        JsonNode fixes=root.path("fixes"); if(!fixes.isArray()) return 0;
        Path generatedRoot=workspace.resolve("generated-java"); int count=0;
        for(JsonNode f:fixes){
            String fileName=safeFileName(text(f,"fileName","")); String source=normalizeSource(text(f,"sourceCode",""));
            if(fileName.isBlank()||source.isBlank()) continue;
            Path existing=findByFileName(generatedRoot,fileName);
            if(existing==null){ String pkg=packageName(source,"com.example.legality.generated"); existing=generatedRoot.resolve(pkg.replace('.',File.separatorChar)).resolve(fileName); Files.createDirectories(existing.getParent()); }
            Files.writeString(existing,source); count++;
        }
        return count;
    }

    public List<LegalityRule> loadCompiledRules(Path workspace) throws Exception {
        Path classes=workspace.resolve("compiled-rules"); if(!Files.exists(classes)) throw new IllegalStateException("Compile generated rules first.");
        List<LegalityRule> out=new ArrayList<>();
        try(URLClassLoader loader=new URLClassLoader(new URL[]{classes.toUri().toURL()},GeneratedJavaManager.class.getClassLoader())){
            try(var st=Files.walk(classes)){
                for(Path p:st.filter(x->x.toString().endsWith(".class")).toList()){
                    String cn=classes.relativize(p).toString().replace(File.separatorChar,'.').replaceAll("\\.class$","");
                    if(cn.contains("$")) continue;
                    Class<?> c=Class.forName(cn,false,loader);
                    if(!c.isInterface() && LegalityRule.class.isAssignableFrom(c)) out.add((LegalityRule)c.getDeclaredConstructor().newInstance());
                }
            }
        }
        return out;
    }

    private String normalizeSource(String s){
        if(s==null) return "";
        return s.replace("rule.getRuleId()","rule.ruleId()").replace("rule.getRuleName()","rule.ruleName()").replace("RuleResult.ok(rule.ruleId())","RuleResult.pass(rule.ruleId())");
    }
    private Path findByFileName(Path root,String name)throws Exception{ if(!Files.exists(root))return null; try(var st=Files.walk(root)){ return st.filter(p->p.getFileName().toString().equals(name)).findFirst().orElse(null);} }
    private void deleteChildren(Path root)throws Exception{ if(!Files.exists(root))return; try(var st=Files.walk(root)){ for(Path p:st.sorted(Comparator.reverseOrder()).toList()) if(!p.equals(root)) Files.deleteIfExists(p);} }
    private String packageName(String src,String fallback){ for(String line:src.split("\\R")){ String t=line.trim(); if(t.startsWith("package ")&&t.endsWith(";")) return t.substring(8,t.length()-1).trim(); } return fallback; }
    private String safeFileName(String name){ String c=name.replace('\\','/'); int i=c.lastIndexOf('/'); if(i>=0)c=c.substring(i+1); return c.replaceAll("[^A-Za-z0-9_.-]","_"); }
    private String text(JsonNode n,String field,String fallback){ JsonNode v=n==null?null:n.path(field); return v==null||v.isMissingNode()||v.isNull()?fallback:v.asText(); }

    public record CompileResult(boolean success,int sourceFileCount,Path classesDir,String diagnostics){}
}
