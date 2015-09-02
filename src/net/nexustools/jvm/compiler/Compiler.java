/*
 * JVM.JS-Compiler
 * 
 * This code is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software.
 */
package net.nexustools.jvm.compiler;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.optimizer.ClassOptimizer;

/**
 *
 * @author kate
 */
public class Compiler {
    public static final Pattern methodSignature = Pattern.compile("^\\(([^\\)]+)?\\)(.+)$");
    public static final Pattern classSignature = Pattern.compile("L([^;]+);");
    public static final Pattern javaClass = Pattern.compile("^javax?/");
    public static final File invalidFile = new File("$$unknown!$$");
    public static final String astrixQuote = Pattern.quote("*");
    public static final String[] requiredBuiltIns = new String[]{
        "java/lang/Throwable",
        "java/lang/Exception",
        "java/lang/VirtualMachineError",
        "java/lang/UnsatisfiedLinkError",
        "java/lang/ClassNotFoundException",
        "java/lang/IllegalArgumentException",
        "java/lang/UnsupportedOperationException",
        "java/lang/NullPointerException",
        "java/lang/RuntimeException",
        "java/lang/Iterator",
        "java/lang/Number",
        "java/lang/Class"
    };
    
    private static final Map<Integer, String> opcodeMap = new HashMap();
    private static final Map<String, Integer> accessModes = new HashMap();
    static {
        Class<?> opcode = Opcodes.class;
        for(Field field : opcode.getDeclaredFields()) {
            if(!Modifier.isStatic(field.getModifiers()))
                continue;
            
            String name = field.getName();
            if(name.startsWith("ACC_"))
                try {
                    accessModes.put(name.substring(4), (Integer)(Number)field.get(null));
                } catch (ReflectiveOperationException ex) {
                    throw new RuntimeException(ex);
                }
            else
                try {
                    opcodeMap.put((Integer)(Number)field.get(null), name);
                } catch (ReflectiveOperationException ex) {
                    throw new RuntimeException(ex);
                }
        }
    }
    
    public static final FileFilter builtInFileFilter = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return pathname.isFile() && pathname.getName().endsWith(".js");
        }
    };

    public static class CompileError extends Error {
        public CompileError(String message) {
            super(message);
        }
        public CompileError(Throwable cause) {
            super(cause.toString(), cause);
        }
        public CompileError(String message, Throwable cause) {
            super(message, cause);
        }
        
    }
    
    public static interface Referencer {
        public boolean add(String reference);
    }
    public static interface SignatureConverter {
        public String convert(String input);
    }
    public static interface ProgressListener {
        public void onProgress(float percent);
        public void onMessage(String message);
    }
    
    public static final ProgressListener NullListener = new ProgressListener() {
        @Override
        public void onProgress(float percent) {}
        @Override
        public void onMessage(String message) {}
    };
    
    public static String nameForOpcode(int opcode) {
        String name = opcodeMap.get(opcode);
        if(name == null)
            throw new RuntimeException("Unknown opcode: " + opcode);
        return name;
    }
    
    public final Config config;
    public final String[] BUILT_IN;
    public final List<String> processed = new ArrayList();
    public final List<File> runtimeFiles = new ArrayList();
    public final Map<String, List<String>> referenceMap = new HashMap();
    public final List<String> compiled = new ArrayList();
    public final List<String> natives = new ArrayList();
    public final List<String> extraClasses = new ArrayList();
    public final List<String> usedbuiltins = new ArrayList();
    public final Map<String, List<String>> serviceMap = new HashMap();
    public final Map<String, File> classpathContents = new HashMap();
    private ProgressListener progressListener;
    public final File outputFolder;
    public final File[] classpath;
    public Compiler(Config config, ProgressListener listener) {
        this.config = config;
        setProgressListener(listener);
        
        outputFolder = new File(config.outputDirectory);
        
        progressListener.onProgress(-1);
        progressListener.onMessage("Scanning runtime built-in classes");
        
        List<String> detected = new ArrayList();
        for(File file : new File(config.runtimeDirectoryJS, "classes").listFiles(builtInFileFilter)) {
            String name = file.getName().replace('_', '/');
            detected.add(name.substring(0, name.length()-3));
        }
        BUILT_IN = detected.toArray(new String[detected.size()]);
        System.out.println(Arrays.toString(BUILT_IN));
        
        classpath = new File[2 + config.additionalClassDirectories.length];
        classpath[0] = new File(config.runtimeDirectoryJava);
        classpath[1] = new File(config.projectDirectory);
        for(int i=0; i<config.additionalClassDirectories.length; i++)
            classpath[i+2] = new File(config.additionalClassDirectories[i]);
        
        progressListener.onMessage("Scanning classpath contents");
        for(File path : classpath)
            scan(path, "");
    }
    
    public void createOutputDirectory() {
        if(!outputFolder.exists() && !outputFolder.mkdirs())
            throw new CompileError("Cannot create folder `" + outputFolder.getAbsolutePath() + "`");
    }
    
    public void compile() {
        List<Pattern> matchers = new ArrayList();
        if(config.mainClass != null && !config.mainClass.isEmpty())
            matchers.add(buildClassPattern(config.mainClass));
        
        for(String additional : config.additionalClasses)
            matchers.add(buildClassPattern(additional));
        
        List<String> classesToCompile = new ArrayList();
        progressListener.onMessage("Scanning classes to compile");
        for(String file : classpathContents.keySet()) {
            for(Pattern pattern : matchers) {
                if(pattern.matcher(file).matches()) {
                    classesToCompile.add(file.substring(0, file.length()-6));
                    break;
                }
            }
        }
        
        progressListener.onMessage("Beginning compile...");
        int total = classesToCompile.size() + extraClasses.size(), complete = 0;
        
        for(String compile : classesToCompile) {
            progressListener.onProgress((float)complete / (float)total);
            progressListener.onMessage(compile);
            try {
                compile(compile);
            } catch (IOException ex) {
                throw new CompileError("Error compiling `" + compile + "`", ex);
            }
            complete ++;
        }
        for(String compile : extraClasses) {
            progressListener.onProgress((float)complete / (float)total);
            progressListener.onMessage(compile);
            try {
                compile(compile);
            } catch (IOException ex) {
                throw new CompileError("Error compiling `" + compile + "`", ex);
            }
            complete ++;
        }
        
    }

    public List<String> copyLibraries() {
        List<String> copied = new ArrayList();
        
        File libDir = new File(config.runtimeDirectoryJS, "lib");
        System.out.println("Scanning lib directory" + libDir);
        for(File file : libDir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.isFile() && (pathname.getName().endsWith(".js") || pathname.getName().endsWith(".map"));
                    }
                })) {
            copied.add("jvm/lib/" + file.getName());
        }
        
        copied.add("jvm/jvm.js");
        copied.add("jvm/common.js");
        copied.add("jvm/settings.js");
        copied.add("jvm/types.js");
        copied.add("jvm/flags.js");
        copied.add("jvm/opcodes.js");
        copied.add("jvm/optimizer.js");
        copied.add("jvm/compiler-" + config.compilerVersion.replaceAll("\\s+", "-").toLowerCase() + ".js");
        copied.add("jvm/classloader.js");
        
        progressListener.onMessage("Scanning libraries to copy");
        Map<String, File> filesToCopy = new LinkedHashMap();
        for(String f : copied)
            filesToCopy.put(f, new File(config.runtimeDirectoryJS, f.substring(4)));
        
        if(!serviceMap.isEmpty()) {
            File runtimeDir = new File(config.outputDirectory, "runtime");
            if(!runtimeDir.isDirectory() && !runtimeDir.mkdirs())
                throw new CompileError("Unable to create runtime directory");
            try (FileWriter writer = new FileWriter(new File(runtimeDir, "services.js"))) {
                writer.append("(function(JVM) {\n");
                writer.append("\tObject.defineProperty(JVM, \"ServiceMap\", {\n");
                writer.append("\t\tvalue: ");
                writer.append(new Gson().toJson(serviceMap));
                writer.append("\n\t});\n");
                writer.append("})($currentJVM);");
            } catch (IOException ex) {
                throw new CompileError(ex);
            }
            copied.add("runtime/services.js");
        }
        
        for(String builtin : requiredBuiltIns) {
            if(!usedbuiltins.contains(builtin))
                usedbuiltins.add(builtin);
        }
        
        System.out.println("Processing used builtins: " + usedbuiltins);
        for(String builtin : usedbuiltins) {
            filesToCopy.put("builtin/" + builtin + ".js", new File(new File(config.runtimeDirectoryJS), "classes/" + builtin.replace("/", "_") + ".js"));
        }
        
        progressListener.onMessage("Copying libraries");
        int total = filesToCopy.size(), complete = 0;
        System.out.println("Copying: " + filesToCopy);
        
        System.out.println("Copying " + total + " files");
        
        for(Entry<String, File> copy : filesToCopy.entrySet()) {
            progressListener.onProgress((float)complete / (float)total);
            
            File outFile = new File(outputFolder, copy.getKey());
            File parentDir = outFile.getParentFile();
            if(!parentDir.isDirectory() && !parentDir.mkdirs())
                throw new CompileError("Cannot create directory `" + parentDir.getAbsolutePath() + "`");
            
            try {
                copy(new FileInputStream(copy.getValue()), new FileOutputStream(outFile));
            } catch (IOException ex) {
                throw new CompileError("Error while copying `" + copy + "`", ex);
            }
            
            if(!copied.contains(copy.getKey()) && copy.getKey().endsWith(".js"))
                copied.add(copy.getKey());
            complete ++;
        }
        
        if(!runtimeFiles.isEmpty()) {
            File libjvmruntimes = new File(outputFolder + "/runtime");
            if(!libjvmruntimes.isDirectory() && !libjvmruntimes.mkdirs())
                throw new CompileError("Cannot create directory `" + libjvmruntimes.getAbsolutePath() + "`");
            
            int i=0;
            for(File runtime : runtimeFiles) {
                try {
                    copy(new FileInputStream(runtime), new FileOutputStream(new File(libjvmruntimes, "boot" + i + ".js")));
                } catch (IOException ex) {
                    throw new CompileError("Error while copying `" + runtime + "`", ex);
                }
                copied.add("runtime/boot" + i + ".js");
                i++;
            }
        }
        
        return Collections.unmodifiableList(copied);
    }
    void writeIndex(List<String> copiedLibraries) throws IOException {
        progressListener.onMessage("Writing index.html");
        progressListener.onProgress(-1);
        
        BufferedWriter indexHtml = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(outputFolder, "index.html"))));
        indexHtml.write("<html><head>");
        indexHtml.write(config.head.header);
        indexHtml.write("\n  <title>JVM Test</title>\n");
        indexHtml.write(config.head.footer);
        indexHtml.write("</head><body>\n");
        //indexHtml.write("  <canvas id=\"canvas\" width=\"1024\", height=\"768\"></canvas><br />\n" +
        //                "  <button id=\"button\">Click Me!</button>\n");
        
        indexHtml.write(config.body.header);
        indexHtml.write("\n\n");
        
        indexHtml.write("  <script type=\"");
        indexHtml.write(config.scriptType);
        indexHtml.write("\">\n    window.$jvmErrors = [];\n    window.onerror = function(msg, url, line) {\n      window.$jvmErrors.push([msg, url, line]);\n    }\n  </script>\n");
        
        indexHtml.write("  <!-- START JVM LIBS -->\n");
        for(String lib : copiedLibraries) {
            if(!lib.startsWith("jvm/"))
                continue;
            
            indexHtml.write("    <script type=\"");
            indexHtml.write(config.scriptType);
            indexHtml.write("\" src=\"");
            indexHtml.write(lib);
            indexHtml.write("\"></script>\n");
        }
        indexHtml.write("  <!-- END JVM LIBS -->\n");
        
        indexHtml.write("  <script type=\"");
        indexHtml.write(config.scriptType);
        indexHtml.write("\">\n    var jvm = new JVM();\n    jvm.makeCurrent();\n  </script>\n");
        
        indexHtml.write("  <!-- START LIBS -->\n");
        for(String lib : copiedLibraries) {
            if(lib.startsWith("jvm/"))
                continue;
            
            indexHtml.write("    <script type=\"");
            indexHtml.write(config.scriptType);
            indexHtml.write("\" src=\"");
            indexHtml.write(lib);
            indexHtml.write("\"></script>\n");
        }
        indexHtml.write("  <!-- END LIBS -->\n");
        
        indexHtml.write("  <!-- START CLASSES -->\n");
        List<String> known = new ArrayList();
        for(String ref : compiled) {
            boolean builtin = false;
            for(String build : BUILT_IN)
                if(ref.equals(build)) {
                    builtin = true;
                    break;
                }
            if(builtin)
                continue;
            
            //ref = convertRuntime(ref);
            if(known.contains(ref))
                continue;
            known.add(ref);
            
            indexHtml.write("    <script type=\"");
            indexHtml.write(config.scriptType);
            indexHtml.write("\" src=\"");
            indexHtml.write(ref);
            indexHtml.write("\"></script>\n");
        }
        indexHtml.write("  <!-- END CLASSES -->\n");
        if(!natives.isEmpty()) {
            indexHtml.write("  <!-- START JNI -->\n");
            for(String ref : natives) {
                //ref = convertRuntime(ref);
                if(known.contains(ref))
                    continue;
                known.add(ref);

                indexHtml.write("    <script type=\"");
                indexHtml.write(config.scriptType);
                indexHtml.write("\" src=\"");
                indexHtml.write(ref);
                indexHtml.write("\"></script>\n");
            }
            indexHtml.write("  <!-- END JNI -->\n");
        }
        
        if(config.mainClass != null && !config.mainClass.isEmpty()) {
            indexHtml.write("  <script type=\"");
            indexHtml.write(config.scriptType);
            indexHtml.write("\">jvm.main(\"");
            indexHtml.write(config.mainClass.replace('.', '/'));
            indexHtml.write("\")</script>\n");
        }
        indexHtml.write(config.body.footer);
        indexHtml.write("</body></html>");
        indexHtml.flush();
        indexHtml.close();
    }
    
    
    private Pattern buildClassPattern( String classRegex) {
        classRegex = classRegex.replace('.', '/');
        if(classRegex.contains("*")) {
            String pattern = "^";
            StringBuilder buffer = new StringBuilder();
            for(char c : classRegex.toCharArray()) {
                if(c == '*') {
                    if(buffer.length() > 0)  {
                        pattern += Pattern.quote(buffer.toString());
                        buffer = new StringBuilder();
                    }
                    pattern += ".+";
                } else
                    buffer.append(c);
            }
            if(buffer.length() > 0) 
                pattern += Pattern.quote(buffer.toString());
            
            return Pattern.compile(pattern + "\\.class$");
        } else
            return Pattern.compile('^' + Pattern.quote(classRegex.replace('.', '/')).replace(astrixQuote, ".+") + "\\.class$");
        
    }
    
    public static final Pattern SERVICE_PATTERN = Pattern.compile("^META\\-INF/services/(.+)$");
    private void scan(File directory, String prefix) {
        if(!prefix.isEmpty())
            prefix += '/';
        for(File child : directory.listFiles()) {
            if(child.isHidden() || child.getName().endsWith("~"))
                continue;
            
            String childPath = prefix + child.getName();
            if(childPath.equals("META-INF/runtime.js") ||
                    childPath.equals("runtime.js")) {
                runtimeFiles.add(child);
                continue;
            }
            
            Matcher matcher = SERVICE_PATTERN.matcher(childPath);
            if(matcher.matches()) {
                System.out.println("Matches Service Pattern: " + childPath);
                StringBuilder content = new StringBuilder();
                byte[] buffer = new byte[4096];
                try {
                    int read;
                    InputStream in = new FileInputStream(child);
                    while((read = in.read(buffer)) > 0)
                        content.append(new String(buffer, 0, read));
                    
                    String implClass = content.toString().trim();
                    int dash = implClass.indexOf('#');
                    if(dash > -1)
                        implClass = implClass.substring(0, dash).trim();
                    
                    String name = child.getName();
                    List<String> implList = serviceMap.get(name);
                    if(implList == null) {
                        serviceMap.put(name, implList = new ArrayList());
                        if(!extraClasses.contains("java/lang/Iterable"))
                            extraClasses.add("java/lang/Iterable");
                        if(!extraClasses.contains("java/lang/Iterator"))
                            extraClasses.add("java/lang/Iterator");
                    }
                    
                    String implClassPath = implClass.replace(".", "/");
                    implList.add(implClassPath);
                    extraClasses.add(implClassPath);
                } catch (IOException ex) {
                    throw new CompileError("Failed to process service", ex);
                }
                continue;
            }
            
            if(child.isDirectory()) {
                if(child.getName().equals(".git"))
                    continue;
                
                scan(child, childPath);
            } else if(!classpathContents.containsKey(childPath))
                classpathContents.put(childPath, child);
        }
    }
    
    public final void setProgressListener(ProgressListener listener) {
        if(listener == null)
            listener = NullListener;
        progressListener = listener;
    }
    
    public static String convertSignature(String signature) {
        if("Z".equals(signature))
            return "JVM.Types.BOOLEAN";
        if("B".equals(signature))
            return "JVM.Types.BYTE";
        if("C".equals(signature))
            return "JVM.Types.CHAR";
        if("S".equals(signature))
            return "JVM.Types.SHORT";
        if("I".equals(signature))
            return "JVM.Types.INT";
        if("J".equals(signature))
            return "JVM.Types.LONG";
        if("F".equals(signature))
            return "JVM.Types.FLOAT";
        if("D".equals(signature))
            return "JVM.Types.DOUBLE";
        if("V".equals(signature))
            return "JVM.Types.VOID";
        //if("Ljava/lang/String;".equals(signature))
        //    return "JVM.Types.STRING";
        
        return '"' + convertRuntime(signature) + '"';
    }
    
    public static String convertRuntime(String classname) {
        return classname.replaceAll("net/nexustools/jvm/runtime/", "");
    }
    
    public static void writeAccess(int access, BufferedWriter bw) throws IOException {
        bw.append("\t\t\t\"access\": [\n");
        
        List<String> modes = new ArrayList();
        for(Entry<String, Integer> accessMode : accessModes.entrySet()) {
            if((access & accessMode.getValue()) != 0) {
                access -= accessMode.getValue();
                modes.add(accessMode.getKey());
            }
        }
        
        if(access > 0)
            throw new CompileError("Access remaining, unhandled or unknown access mode. (" + access + ")");
        
        for(int i=0; i<modes.size(); i++) {
            bw.append("\t\t\t\tJVM.Flags.");
            bw.append(modes.get(i));
            if(i < modes.size()-1)
                bw.append(',');
            bw.append('\n');
        }
        
        bw.append("\t\t\t]\n");
    }
    
    public File resolve(String file) {
        File found = classpathContents.get(file);
        return found != null ? found : invalidFile;
    }
    
    public File resolveOutput(File original, String outputPath) {
        int classpathIndex = 0;
        for(File path : classpath) {
            if(original.getPath().startsWith(path.getPath()))
                break;
            classpathIndex ++;
        }
        
        File resolved = new File(outputFolder, "classpath" + classpathIndex + "/" + outputPath);
        System.out.println("Resolving output: " + outputPath + " to " + resolved);
        return resolved;
    }
    
    public void compile(String rawClassname) throws IOException {
        if(processed.contains(rawClassname))
            return;
        processed.add(rawClassname);
        
        System.out.println("Resolving class " + rawClassname);
        
        final String classname, runtimeClassname = convertRuntime(rawClassname);
        
        for(String builtin : BUILT_IN)
            if(builtin.equals(runtimeClassname)) {
                usedbuiltins.add(builtin);
                return; // Skip
            }
        
        if(javaClass.matcher(rawClassname).find())
            classname = "net/nexustools/jvm/runtime/" + rawClassname;
        else
            classname = rawClassname;
        
        File findFile = resolve(classname + ".class");
        ClassReader reader;
        
        try {
            if(findFile.exists()) {
                if(!classname.equals(rawClassname)) {
                    if(processed.contains(classname))
                        return;
                    
                    processed.add(classname);
                }
                
                reader = new ClassReader(new FileInputStream(findFile));
            } else {
                throw new CompileError("No implementation found: " + classname);
                //reader = new ClassReader(rawClassname);
                //System.err.println("\tUsing system provided class impl");
            }
        } catch (IOException ex) {
            throw new CompileError(ex);
        }
        
        int offset = outputFolder.getPath().length()+1;
        File output = resolveOutput(findFile, runtimeClassname + ".js");
        File parentFile = output.getParentFile();
        if(!parentFile.isDirectory() && !parentFile.mkdirs())
            throw new RuntimeException("Cannot create directory: " + parentFile);
        
        File nativeFile = resolve(classname + ".native.js");
        if(nativeFile.exists()) {
            File outputResolvedPath = new File(parentFile, nativeFile.getName());
            copy(new FileInputStream(nativeFile), new FileOutputStream(outputResolvedPath));
            natives.add(outputResolvedPath.getPath().substring(offset));
        }
        compiled.add(output.getPath().substring(offset));
        
        final List<String> references = new ArrayList();
        final Referencer referencer = new Referencer() {
            @Override
            public boolean add(String reference) {
                reference = convertRuntime(reference);
                
                if(references.contains(reference))
                    return false;
                references.add(reference);
                
                return true;
            }
        };
        final SignatureConverter converter = new SignatureConverter() {
            @Override
            public String convert(String input) {
                String ref = input;
                while(ref.startsWith("["))
                    ref = ref.substring(1);
                
                if(ref.startsWith("L") && ref.endsWith(";"))
                    referencer.add(ref.substring(1, ref.length()-1));
                else if(ref.length() > 1)
                    referencer.add(ref);
                
                return convertSignature(input);
            }
        };
        
        final OutputStreamWriter oSw = new OutputStreamWriter(new FileOutputStream(output));
        
        final BufferedWriter bw = new BufferedWriter(oSw);
        
        try {
            bw.append("(function JVM_");
            bw.append(runtimeClassname.replaceAll("\\W", "_"));
            bw.append("($JVM, JVM){\n\t$JVM.ClassLoader.defineClass(\"");
            bw.append(runtimeClassname);
            bw.append("\", [");

            String[] interfaces = reader.getInterfaces();
            references.addAll(Arrays.asList(interfaces));
            for(int i=0; i<interfaces.length; i++) {
                if(i > 0)
                    bw.append(',');
                bw.append('\"');
                bw.append(convertRuntime(interfaces[i]));
                bw.append('\"');
            }

            bw.append("], ");
            String parent = reader.getSuperName();
            if(parent != null && !references.contains(parent)) {
                bw.append('\"');
                bw.append(convertRuntime(parent));
                bw.append('\"');
                references.add(parent);
            } else
                bw.append("null");
            bw.append(", [\n");
            
            final List<String> fields = new ArrayList();
            final List<String> methods = new ArrayList();
            System.out.println("\tVisiting class " + classname);
            
            final int[] methodAccess = new int[1];
            final MethodVisitor methodVisitor = new MethodVisitor(Opcodes.ASM4) {
                
                @Override
                public void visitEnd() {
                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"end\"\n");
                        bw.append("\t\t\t\t}\n");

                        bw.append("\t\t\t],\n");

                        writeAccess(methodAccess[0], bw);

                        bw.append("\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                    System.out.println("\t\t\tvisitTryCatchBlock: " + start + ", " + end + ", " + handler + ", " + type);

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"try\",\n");

                        bw.append("\t\t\t\t\t\"start\": \"");
                        bw.append(start.toString());
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"end\": \"");
                        bw.append(end.toString());
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"handler\": \"");
                        bw.append(handler.toString());
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"catch\": \"");
                        bw.append(type);
                        bw.append("\"\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    System.out.println("\t\t\tvisitMethodInsn: " + nameForOpcode(opcode) + ", " + owner + ", " + name + ", " + desc + ", " + itf);

                    /*if(name.equals("<init>") && desc.equals("()V") && runtimeClassname.equals("java/lang/Object")) {
                        try {
                            bw.append("\t\t\t\t{\n");
                            bw.append("\t\t\t\t\t\"type\": \"initobject\"\n");
                            bw.append("\t\t\t\t},\n");
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }

                        return;
                    }*/

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"method\",\n");

                        bw.append("\t\t\t\t\t\"opcode\": JVM.Opcodes.");
                        bw.append(nameForOpcode(opcode));
                        bw.append(",\n");

                        bw.append("\t\t\t\t\t\"owner\": ");
                        bw.append(converter.convert(owner));
                        bw.append(",\n");

                        bw.append("\t\t\t\t\t\"name\": \"");
                        bw.append(name);
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"signature\": {\n");
                        bw.append("\t\t\t\t\t\t\"raw\": \"");
                        bw.append(convertRuntime(desc));
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\t\"return\": ");

                        Matcher matcher = methodSignature.matcher(desc);
                        if(!matcher.matches())
                            throw new IllegalArgumentException("Corrupt or invalid method signature: " + desc);

                        bw.append(converter.convert(matcher.group(2)));
                        bw.append(",\n");

                        String args = matcher.group(1);
                        if(args != null) {
                            bw.append("\t\t\t\t\t\t\"args\": [\n");


                            String[] argsl = splitArguments(args);
                            /*matcher = classSignature.matcher(args);
                            while(matcher.find())
                                argsl.add(converter.convert(matcher.group(1)));*/

                            for(int i=0; i<argsl.length; i++) {
                                bw.append("\t\t\t\t\t\t\t");
                                bw.append(converter.convert(argsl[i]));
                                if(i < argsl.length-1)
                                    bw.append(',');
                                bw.append('\n');
                            }

                            bw.append("\t\t\t\t\t\t]\n");
                        } else
                            bw.append("\t\t\t\t\t\t\"args\": []\n");
                        bw.append("\t\t\t\t\t},\n");

                        bw.append("\t\t\t\t\t\"interface\": ");
                        bw.append(itf ? "true" : "false");
                        bw.append("\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                    System.out.println("\t\t\tvisitTableSwitchInsn: " + min + ", " + max + ", " + dflt + ", " + Arrays.toString(labels));

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"tableSwitch\",\n");

                        bw.append("\t\t\t\t\t\"min\": \"");
                        bw.append(String.valueOf(min));
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"max\": \"");
                        bw.append(String.valueOf(max));
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"default\": \"");
                        bw.append(dflt.toString());
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"jumps\": [\n");
                        for(int i=0; i<labels.length; i++) {
                            bw.append("\t\t\t\t\t\t\"");
                            bw.append(labels[i].toString());
                            bw.append('"');
                            if(i < labels.length-1)
                                bw.append(',');
                            bw.append('\n');
                        }
                        bw.append("\t\t\t\t\t]\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitMultiANewArrayInsn(String desc, int dims) {
                    System.out.println("\t\t\tvisitMultiANewArrayInsn: " + desc + ", " + dims);

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"array\",\n");

                        bw.append("\t\t\t\t\t\"desc\": \"");
                        bw.append(desc);
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"size\": \"");
                        bw.append(String.valueOf(dims));
                        bw.append("\"\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitIincInsn(int var, int increment) {
                    System.out.println("\t\t\tvisitIincInsn: " + var + ", " + increment);

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"iinc\",\n");

                        bw.append("\t\t\t\t\t\"index\": \"");
                        bw.append(String.valueOf(var));
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"by\": \"");
                        bw.append(String.valueOf(increment));
                        bw.append("\"\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                    System.out.println("\t\t\tvisitLookupSwitchInsn: " + dflt + ", " + Arrays.toString(keys) + ", " + Arrays.toString(labels));

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"switch\",\n");

                        if(dflt != null) {
                            bw.append("\t\t\t\t\t\"default\": \"");
                            bw.append(dflt.toString());
                            bw.append("\",\n");
                        }

                        bw.append("\t\t\t\t\t\"keys\": [\n");
                        for(int i=0; i<keys.length; i++) {
                            bw.append("\t\t\t\t\t\t");
                            bw.append(String.valueOf(keys[i]));
                            if(i < keys.length-1)
                                bw.append(',');
                            bw.append('\n');
                        }
                        bw.append("\t\t\t\t\t],\n");

                        bw.append("\t\t\t\t\t\"jumps\": [\n");
                        for(int i=0; i<labels.length; i++) {
                            bw.append("\t\t\t\t\t\t\"");
                            bw.append(labels[i].toString());
                            bw.append('"');
                            if(i < labels.length-1)
                                bw.append(',');
                            bw.append('\n');
                        }
                        bw.append("\t\t\t\t\t]\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                    System.out.println("\t\t\tvisitLocalVariable: " + name + ", " + desc + ", " + start + ", " + end + ", " + index);

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"declare\",\n");

                        bw.append("\t\t\t\t\t\"name\": \"");
                        bw.append(String.valueOf(name));
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"signature\": ");
                        bw.append(converter.convert(desc));
                        bw.append(",\n");

                        bw.append("\t\t\t\t\t\"index\": \"");
                        bw.append(String.valueOf(index));
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"start\": \"");
                        bw.append(start.toString());
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"end\": \"");
                        bw.append(end.toString());
                        bw.append("\"\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitParameter(String name, int access) {
                    System.out.println("\t\t\tvisitParameter: " + name + ", " + access);

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"arg\",\n");

                        bw.append("\t\t\t\t\t\"name\": \"");
                        bw.append(name);
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"access\": \"");
                        bw.append(String.valueOf(access));
                        bw.append("\"\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitVarInsn(int opcode, int var) {
                    System.out.println("\t\t\tvisitVarInsn: " + nameForOpcode(opcode) + ", " + var);

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"var\",\n");

                        bw.append("\t\t\t\t\t\"opcode\": JVM.Opcodes.");
                        bw.append(nameForOpcode(opcode));
                        bw.append(",\n");

                        bw.append("\t\t\t\t\t\"index\": \"");
                        bw.append(String.valueOf(var));
                        bw.append("\"\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    System.out.println("\t\t\tvisitTypeInsn: " + nameForOpcode(opcode) + ", " + type);

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"type\",\n");

                        bw.append("\t\t\t\t\t\"opcode\": JVM.Opcodes.");
                        bw.append(nameForOpcode(opcode));
                        bw.append(",\n");

                        bw.append("\t\t\t\t\t\"signature\": ");
                        bw.append(converter.convert(type));
                        bw.append('\n');
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitLdcInsn(Object cst) {
                    System.out.println("\t\t\tvisitLdcInsn: " + cst);
                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"ldc\",\n");

                        if(cst instanceof String) {
                            bw.append("\t\t\t\t\t\"stringValue\": ");
                            bw.append(new Gson().toJson((String)cst));
                            bw.append("\n");
                        } else if(cst instanceof Number) {
                            bw.append("\t\t\t\t\t\"numericValue\": ");
                            bw.append(String.valueOf((Number)cst));
                            bw.append("\n");
                        } else if(cst instanceof org.objectweb.asm.Type) {
                            org.objectweb.asm.Type type = (org.objectweb.asm.Type)cst;
                            switch(type.getSort()) {
                                case Type.OBJECT:
                                    System.out.println("OBJECT REFERENCE");
                                    String ref = type.getInternalName();
                                    bw.append("\t\t\t\t\t\"objectRef\": \"");
                                    converter.convert(ref);
                                    bw.append(ref);
                                    bw.append("\"\n");
                                    break;
                                    
                                default:
                                    throw new UnsupportedOperationException("Cannot handle type: " + type.getSort());
                            }
                            
                            
                            /*System.out.println("asm type: " + type.getInternalName());
                            System.out.println(type.getReturnType());
                            System.out.println(type.getDescriptor());
                            System.out.println(type.getElementType());
                            System.out.println(type.getDimensions());
                            System.out.println(type.getClassName());
                            
                            throw new UnsupportedOperationException("Cannot handle types yet...");*/
                        } else
                            throw new UnsupportedOperationException("Unsupported type for LDC: " + cst.getClass().getName());

                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
                    throw new UnsupportedOperationException();

                    /*System.out.println("\t\t\tvisitInvokeDynamicInsn: " + name + ", " + desc + ", " + bsm + ", " + Arrays.toString(bsmArgs));

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"invokeDynamic\",\n");

                        bw.append("\t\t\t\t\t\"name\": \"");
                        bw.append(name);
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"signature\": \"");
                        bw.append(desc);
                        bw.append("\"\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }*/
                }

                @Override
                public void visitLabel(Label label) {
                    System.out.println("\t\t\tvisitLabel: " + label.toString());

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"label\",\n");

                        bw.append("\t\t\t\t\t\"name\": \"");
                        bw.append(label.toString());
                        bw.append("\"\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitJumpInsn(int opcode, Label label) {
                    System.out.println("\t\t\tvisitJumpInsn: " + nameForOpcode(opcode) + ", " + label.toString());

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"jump\",\n");

                        bw.append("\t\t\t\t\t\"opcode\": JVM.Opcodes.");
                        bw.append(nameForOpcode(opcode));
                        bw.append(",\n");

                        bw.append("\t\t\t\t\t\"name\": \"");
                        bw.append(label.toString());
                        bw.append("\"\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitInsn(int opcode) {
                    System.out.println("\t\t\tvisitInsn: " + nameForOpcode(opcode));

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"insn\",\n");

                        bw.append("\t\t\t\t\t\"opcode\": JVM.Opcodes.");
                        bw.append(nameForOpcode(opcode));
                        bw.append("\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitIntInsn(int opcode, int operand) {
                    System.out.println("\t\t\tvisitIntInsn: " + nameForOpcode(opcode) + ", " + operand);

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"int\",\n");

                        bw.append("\t\t\t\t\t\"opcode\": JVM.Opcodes.");
                        bw.append(nameForOpcode(opcode));
                        bw.append(",\n");

                        bw.append("\t\t\t\t\t\"operand\": \"");
                        bw.append(String.valueOf(operand));
                        bw.append("\"\n");
                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    System.out.println("\t\t\tvisitFieldInsn: " + nameForOpcode(opcode) + ", " + owner + ", " + name + ", " + desc);

                    try {
                        bw.append("\t\t\t\t{\n");
                        bw.append("\t\t\t\t\t\"type\": \"field\",\n");

                        bw.append("\t\t\t\t\t\"opcode\": JVM.Opcodes.");
                        bw.append(nameForOpcode(opcode));
                        bw.append(",\n");

                        bw.append("\t\t\t\t\t\"class\": ");
                        bw.append(converter.convert(owner));
                        bw.append(",\n");

                        bw.append("\t\t\t\t\t\"name\": \"");
                        bw.append(name);
                        bw.append("\",\n");

                        bw.append("\t\t\t\t\t\"signature\": ");
                        bw.append(converter.convert(desc));
                        bw.append('\n');

                        bw.append("\t\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            };
            
            final ClassOptimizer[] classOptimizer = new ClassOptimizer[1];
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM4) {

                @Override
                public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                    System.out.println("\t\tField: " + name + ", " + desc + ", " + value + ", " + access);
                    if(!fields.contains(name)) {
                        fields.add(name);
                    }
                    
                    try {
                        bw.append("\t\t{\n");
                        
                        bw.append("\t\t\t\"type\": \"field\",\n");
                        
                        bw.append("\t\t\t\"name\": \"");
                        bw.append(name);
                        bw.append("\",\n");
                        
                        bw.append("\t\t\t\"signature\": ");
                        bw.append(converter.convert(desc));
                        bw.append(",\n");
                        
                        if(value instanceof String) {
                            bw.append("\t\t\t\"stringValue\": \"");
                            bw.append(((String)value).replace("\n", "\\n").replace("\"", "\\\""));
                            bw.append("\",\n");
                        } else if(value instanceof Number) {
                            bw.append("\t\t\t\"numericValue\": ");
                            bw.append(String.valueOf((Number)value));
                            bw.append(",\n");
                        } else if(value != null)
                            throw new RuntimeException("Unhandled initial value: " + value);
                        
                        writeAccess(access, bw);
                        bw.append("\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    
                    //if(signature != null) {
                        Matcher matcher = classSignature.matcher(desc);
                        if(matcher.matches() && !references.contains(matcher.group(1)))
                            references.add(matcher.group(1));
                    //}

                    return super.visitField(access, name, desc, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    System.out.println("\t\tMethod: " + name + ", " + desc + ", " + signature + ", " + access + ", " + Arrays.toString(exceptions));
                    if(!methods.contains(name))
                        methods.add(name);
                    
                    try {
                        bw.append("\t\t{\n");
                        
                        bw.append("\t\t\t\"type\": \"method\",\n");
                        
                        bw.append("\t\t\t\"name\": \"");
                        bw.append(name);
                        bw.append("\",\n");
                        
                        bw.append("\t\t\t\"signature\": \"");
                        bw.append(convertRuntime(desc));
                        bw.append("\",\n");
                        
                        bw.append("\t\t\t\"sigparts\": {\n");
                        bw.append("\t\t\t\t\"return\": ");

                        Matcher matcher = methodSignature.matcher(desc);
                        if(!matcher.matches())
                            throw new IllegalArgumentException("Corrupt or invalid method signature: " + desc);

                        bw.append(converter.convert(matcher.group(2)));
                        bw.append(",\n");

                        String args = matcher.group(1);
                        if(args != null) {
                            bw.append("\t\t\t\t\"args\": [\n");


                            String[] argsl = splitArguments(args);
                            /*matcher = classSignature.matcher(args);
                            while(matcher.find())
                                argsl.add(converter.convert(matcher.group(1)));*/

                            for(int i=0; i<argsl.length; i++) {
                                bw.append("\t\t\t\t\t");
                                bw.append(converter.convert(argsl[i]));
                                if(i < argsl.length-1)
                                    bw.append(',');
                                bw.append('\n');
                            }

                            bw.append("\t\t\t\t]\n");
                        } else
                            bw.append("\t\t\t\t\"args\": []\n");
                        bw.append("\t\t\t},\n");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }

                    if(exceptions != null) {
                        try {
                            bw.append("\t\t\t\"exceptions\": [\n");
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                        
                        for(int i=0; i<exceptions.length; i++) {
                            String exception = exceptions[i];
                            if(!references.contains(exception))
                                references.add(exception);
                            
                            try {
                                bw.append("\t\t\t\t\"");
                                bw.append(convertRuntime(exception));
                                bw.append('"');
                                if(i<exceptions.length-1)
                                    bw.append(',');
                                bw.append('\n');
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                        
                        try {
                            bw.append("\t\t\t],\n");
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    Matcher matcher = methodSignature.matcher(desc);
                    matcher.matches();
                    String args = matcher.group(1);
                    String ret = matcher.group(2);

                    matcher = classSignature.matcher(ret);
                    if(matcher.matches() && !references.contains(matcher.group(1)))
                        references.add(matcher.group(1));

                    if(args != null) {
                        matcher = classSignature.matcher(args);
                        while(matcher.find())
                            if(!references.contains(matcher.group(1)))
                                references.add(matcher.group(1));
                    }
                    
                    if((access & Opcodes.ACC_NATIVE) != 0) {
                        try {
                            bw.append("\t\t\t\"implementation\": \"");
                            bw.append(runtimeClassname + ".native.js");
                            bw.append("\",\n");
                            
                            writeAccess(access, bw);
                            bw.append("\t\t},\n");
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                        
                        return super.visitMethod(access, name, desc, signature, exceptions);
                    }
                    
                    try {
                        bw.append("\t\t\t\"implementation\": [\n");
                        bw.flush();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    
                    methodAccess[0] = access;
                    //return new MethodOptimizer(classOptimizer[0], access, desc, methodVisitor, new Remapper() {});
                    return methodVisitor;
                }
            };
            
            //classOptimizer[0] = new ClassOptimizer(classVisitor, new Remapper() {});
            //reader.accept(classOptimizer[0], 0);
            reader.accept(classVisitor, 0);

            bw.append("\t\t{\n");
            bw.append("\t\t\t\"type\": \"references\",\n");
            bw.append("\t\t\t\"value\": [\n");
            
            List<String> written = new ArrayList();
            for(int i=0; i<references.size(); i++) {
                String ref = convertRuntime(references.get(i));
                if(written.contains(ref))
                    continue;
                written.add(ref);
                
                bw.append("\t\t\t\t\"");
                bw.append(ref);
                bw.append('"');
                if(i < references.size()-1)
                    bw.append(',');
                bw.append('\n');
            }
            bw.append("\t\t\t]\n");
            bw.append("\t\t}\n");
            bw.append("\t]);\n");
            bw.append("})($currentJVM, JVM);");
        } finally {
            bw.close();
        }
        
        System.out.println("\tProcessing references: " + references);
        for(String ref : references)
            compile(ref);
        
        referenceMap.put(runtimeClassname, references);
    }
    
    private static String repeatArray(int arraydepth) {
        StringBuilder builder = new StringBuilder();
        for(int i=0; i<arraydepth; i++)
            builder.append('[');
        return builder.toString();
    }
    
    public static String[] splitArguments(String args) {
        int arraydepth = 0;
        List<String> split = new ArrayList();
        while(args.length() > 0) {
            char first = args.charAt(0);
            if(first == '[') {
                arraydepth ++;
                args = args.substring(1);
            } else if(first == 'L') {
                int next = args.indexOf(';')+1;
                split.add(repeatArray(arraydepth) + args.substring(0, next));
                args = args.substring(next);
                arraydepth = 0;
            } else {
                split.add(repeatArray(arraydepth) + String.valueOf(first));
                args = args.substring(1);
                arraydepth = 0;
            }
        }
        
        return split.toArray(new String[split.size()]);
    }
    
    public static byte[] buffer = new byte[1024];
    public static void copy(InputStream in, OutputStream out) throws IOException {
        int red;
        
        while((red = in.read(buffer)) > 0)
            out.write(buffer, 0, red);
    }
    
}
