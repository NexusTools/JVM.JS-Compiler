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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 *
 * @author kate
 */
public class Config {

    static Config load(File file) throws FileNotFoundException {
        return new Gson().fromJson(new FileReader(file), Config.class);
    }
    
    public boolean writeIndex = true;
    public final IndexSection head = new IndexSection();
    public final IndexSection body = new IndexSection();
    public String scriptType = "text/javascript";
    
    public String runtimeDirectoryJava;
    public String runtimeDirectoryJS;
    public String projectDirectory;
    public String outputDirectory;
    public String compilerVersion = "sync";
    public String mainClass;
    
    public String[] additionalClassDirectories;
    public String[] additionalClasses;
    
    public boolean proguard;

    public void save(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            new Gson().toJson(this, writer);
        }
    }
    
    public static class IndexSection {
        public String header;
        public String footer;
    }
}
