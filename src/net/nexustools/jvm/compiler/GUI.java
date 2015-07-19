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

import java.awt.AWTError;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;

/**
 *
 * @author kate
 */
public class GUI extends javax.swing.JFrame {
    public static final FileFilter fileFilter = new FileFilter() {
        @Override
        public boolean accept(File f) {
            return f.isDirectory() || (f.isFile() && f.getName().endsWith(".wc.json"));
        }
        @Override
        public String getDescription() {
            return "WebCompiler Config (*.wc.json)";
        }
    };
    
    public static class BadConfigState extends Exception {
        public BadConfigState(String message) {
            super(message);
        }
    }
    
    public static interface ListAdder {
        public String add();
    }

    /**
     * Creates new form GUI
     */
    public GUI() {
        initComponents();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        
        bindList(lstClasses, new ListAdder() {
            @Override
            public String add() {
                JFileChooser chooser  = new JFileChooser(new File("."));

                chooser.setDialogTitle("Select Additional Classpath Directory");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);

                if (chooser.showOpenDialog(GUI.this) == JFileChooser.APPROVE_OPTION)
                    return chooser.getSelectedFile().getAbsolutePath();
                return null;
            }
        }, btnAddAdditional, btnRemoveAdditional);
        bindList(lstForceCompile, new ListAdder() {
            @Override
            public String add() {
                return JOptionPane.showInputDialog(GUI.this, "Add Class to Force Compile");
            }
        }, btnAddForceCompile, btnRemoveForceCompile);
    }
    
    
    private void bindList(final JList list, final ListAdder adder, final JButton add, final JButton remove) {
        final DefaultListModel model;
        list.setModel(model = new DefaultListModel());
        
        add.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String toAdd = adder.add();
                if(toAdd != null)
                    model.add(model.size(), toAdd);
            }
        });
        list.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                remove.setEnabled(list.getSelectedIndex() > -1);
            }
        });
        remove.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                model.remove(list.getSelectedIndex());
            }
        });
    }
    
    private void selectFile(JTextField txtFile, String what) {
        File current = new File(txtFile.getText());
        
        JFileChooser chooser;
        if(current.isDirectory())
            chooser = new JFileChooser(current.getParentFile());
        else
            chooser = new JFileChooser(new File("."));
        
        chooser.setDialogTitle("Select `" + what + "`");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            txtFile.setText(chooser.getSelectedFile().getAbsolutePath());
    }
    
    private Config convertToConfig() throws BadConfigState {
        Config config = new Config();
        
        config.runtimeDirectoryJava = txtRuntimeJava.getText().trim();
        if(config.runtimeDirectoryJava.isEmpty())
            throw new BadConfigState("Java Runtime Directory cannot be empty!");
        if(!new File(config.runtimeDirectoryJava).isDirectory())
            throw new BadConfigState("Java Runtime Directory does not exist!");
        
        config.runtimeDirectoryJS = txtRuntimeJS.getText().trim();
        if(config.runtimeDirectoryJS.isEmpty())
            throw new BadConfigState("JS Runtime Directory cannot be empty!");
        if(!new File(config.runtimeDirectoryJS).isDirectory())
            throw new BadConfigState("JS Runtime Directory does not exist!");
        
        config.projectDirectory = txtProject.getText().trim();
        if(config.projectDirectory.isEmpty())
            throw new BadConfigState("Project Directory cannot be empty!");
        if(!new File(config.projectDirectory).isDirectory())
            throw new BadConfigState("Project Directory does not exist!");
        
        config.outputDirectory = txtOutput.getText().trim();
        if(config.outputDirectory.isEmpty())
            throw new BadConfigState("Output Directory cannot be empty!");
        
        config.mainClass = txtMainClass.getText().trim();
        
        DefaultListModel model = (DefaultListModel)lstClasses.getModel();
        config.additionalClassDirectories = new String[model.size()];
        for(int i=0; i<model.size(); i++) {
            String additionalDir = config.additionalClassDirectories[i] = (String)model.elementAt(i);
            if(!new File(additionalDir).isDirectory())
                throw new BadConfigState("Additional directory `" + additionalDir + "` does not exist!");
        }
        
        model = (DefaultListModel)lstForceCompile.getModel();
        config.additionalClasses = new String[model.size()];
        for(int i=0; i<model.size(); i++)
            config.additionalClasses[i] = (String)model.elementAt(i);
        
        config.proguard = chkProguard.isSelected();
        return config;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        txtRuntimeJava = new javax.swing.JTextField();
        btnBrowseRuntime = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        btnBrowseProject = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        txtProject = new javax.swing.JTextField();
        btnOutputBrowse = new javax.swing.JButton();
        txtOutput = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        btnCompile = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstClasses = new javax.swing.JList();
        btnAddAdditional = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        btnRemoveAdditional = new javax.swing.JButton();
        chkProguard = new javax.swing.JCheckBox();
        pgrStatus = new javax.swing.JProgressBar();
        txtMainClass = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        btnAddForceCompile = new javax.swing.JButton();
        btnRemoveForceCompile = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        lstForceCompile = new javax.swing.JList();
        jLabel6 = new javax.swing.JLabel();
        btnExport = new javax.swing.JButton();
        btnImport = new javax.swing.JButton();
        jLabel7 = new javax.swing.JLabel();
        txtRuntimeJS = new javax.swing.JTextField();
        btnBrowseRuntime1 = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("JVM.JS Compiler");

        txtRuntimeJava.setText("../Runtime/Java/build/classes");

        btnBrowseRuntime.setText("Browse");
        btnBrowseRuntime.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBrowseRuntimeActionPerformed(evt);
            }
        });

        jLabel1.setText("Java Runtime Class Directory");

        btnBrowseProject.setText("Browse");
        btnBrowseProject.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBrowseProjectActionPerformed(evt);
            }
        });

        jLabel2.setText("Project Classes Directory");

        btnOutputBrowse.setText("Browse");
        btnOutputBrowse.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOutputBrowseActionPerformed(evt);
            }
        });

        txtOutput.setText("output");

        jLabel3.setText("Output Directory");

        btnCompile.setText("Compile");
        btnCompile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCompileActionPerformed(evt);
            }
        });

        jScrollPane1.setViewportView(lstClasses);

        btnAddAdditional.setText("Add");

        jLabel4.setText("Additional Class Directories");

        btnRemoveAdditional.setText("Remove");
        btnRemoveAdditional.setEnabled(false);

        chkProguard.setText("Proguard");
        chkProguard.setEnabled(false);
        chkProguard.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkProguardActionPerformed(evt);
            }
        });

        pgrStatus.setString("Idle");
        pgrStatus.setStringPainted(true);

        jLabel5.setText("Main Class");

        btnAddForceCompile.setText("Add");
        btnAddForceCompile.setAutoscrolls(true);

        btnRemoveForceCompile.setText("Remove");
        btnRemoveForceCompile.setAutoscrolls(true);
        btnRemoveForceCompile.setEnabled(false);

        jScrollPane2.setViewportView(lstForceCompile);

        jLabel6.setText("Force Compile Classes");

        btnExport.setText("Export");
        btnExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExportActionPerformed(evt);
            }
        });

        btnImport.setText("Import");
        btnImport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnImportActionPerformed(evt);
            }
        });

        jLabel7.setText("JS Runtime Files Directory");

        txtRuntimeJS.setText("../Runtime/JS");

        btnBrowseRuntime1.setText("Browse");
        btnBrowseRuntime1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBrowseRuntime1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(chkProguard)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pgrStatus, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnImport)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnExport)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnCompile))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(btnRemoveAdditional, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnAddAdditional, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(btnRemoveForceCompile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnAddForceCompile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane2))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel5, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(txtRuntimeJS)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnBrowseRuntime1))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(txtProject)
                                    .addComponent(txtRuntimeJava, javax.swing.GroupLayout.DEFAULT_SIZE, 440, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(btnBrowseRuntime)
                                    .addComponent(btnBrowseProject, javax.swing.GroupLayout.Alignment.TRAILING)))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(txtOutput)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnOutputBrowse))
                            .addComponent(txtMainClass))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtRuntimeJava, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowseRuntime)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtRuntimeJS, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowseRuntime1)
                    .addComponent(jLabel7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnBrowseProject)
                    .addComponent(txtProject, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnOutputBrowse)
                    .addComponent(txtOutput, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMainClass, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 243, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btnAddAdditional)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnRemoveAdditional)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel6)
                .addGap(15, 15, 15)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 242, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btnAddForceCompile)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnRemoveForceCompile)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(btnCompile)
                        .addComponent(btnExport)
                        .addComponent(btnImport))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(pgrStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(chkProguard)))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void chkProguardActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkProguardActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_chkProguardActionPerformed

    private void btnBrowseRuntimeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBrowseRuntimeActionPerformed
        selectFile(txtRuntimeJava, "Runtime Class Directory");
    }//GEN-LAST:event_btnBrowseRuntimeActionPerformed

    private void btnBrowseProjectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBrowseProjectActionPerformed
        selectFile(txtProject, "Project Class Directory");
    }//GEN-LAST:event_btnBrowseProjectActionPerformed

    private void btnOutputBrowseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOutputBrowseActionPerformed
        selectFile(txtOutput, "Compiled Output Directory");
    }//GEN-LAST:event_btnOutputBrowseActionPerformed

    private void btnExportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExportActionPerformed
        try {
            Config config = convertToConfig();
            JFileChooser chooser = new JFileChooser();
            chooser.addChoosableFileFilter(fileFilter);
            chooser.setFileFilter(fileFilter);
            
            if(chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                String filePath = chooser.getSelectedFile().getAbsolutePath();
                if(!filePath.contains("."))
                    filePath += ".wc.json";
                config.save(new File(filePath));
            }
        } catch (BadConfigState | IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error occured", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_btnExportActionPerformed

    private void btnImportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnImportActionPerformed
            JFileChooser chooser = new JFileChooser();
            chooser.addChoosableFileFilter(fileFilter);
            chooser.setFileFilter(fileFilter);
            
            if(chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    Config config = Config.load(chooser.getSelectedFile());
                    
                    txtRuntimeJava.setText(config.runtimeDirectoryJava);
                    txtRuntimeJS.setText(config.runtimeDirectoryJS);
                    txtProject.setText(config.projectDirectory);
                    txtOutput.setText(config.outputDirectory);
                    txtMainClass.setText(config.mainClass);
                    
                    DefaultListModel model = (DefaultListModel)lstClasses.getModel();
                    model.clear();
                    
                    for(int i=0; i<config.additionalClassDirectories.length; i++)
                        model.add(i, config.additionalClassDirectories[i]);
                    
                    
                    model = (DefaultListModel)lstForceCompile.getModel();
                    model.clear();
                    
                    for(int i=0; i<config.additionalClasses.length; i++)
                        model.add(i, config.additionalClasses[i]);
                    
                    chkProguard.setSelected(config.proguard);
                } catch (FileNotFoundException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error occured", JOptionPane.ERROR_MESSAGE);
                }
            }
            
    }//GEN-LAST:event_btnImportActionPerformed

    private void btnCompileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCompileActionPerformed
        try {
            final Config config = convertToConfig();
            pgrStatus.setMaximum(1000000);
            pgrStatus.setString("Starting...");
            btnCompile.setEnabled(false);
            btnAddAdditional.setEnabled(false);
            btnAddForceCompile.setEnabled(false);
            btnBrowseProject.setEnabled(false);
            btnBrowseRuntime.setEnabled(false);
            btnExport.setEnabled(false);
            btnImport.setEnabled(false);
            btnOutputBrowse.setEnabled(false);
            btnRemoveAdditional.setEnabled(false);
            btnRemoveForceCompile.setEnabled(false);
            txtMainClass.setEnabled(false);
            txtOutput.setEnabled(false);
            txtProject.setEnabled(false);
            txtRuntimeJava.setEnabled(false);
            
            new Thread("ClassCompiler") {
                public void finished() {
                    pgrStatus.setIndeterminate(false);
                    pgrStatus.setString("Idle");
                    pgrStatus.setValue(0);
                    
                    btnCompile.setEnabled(true);
                    btnAddAdditional.setEnabled(true);
                    btnAddForceCompile.setEnabled(true);
                    btnBrowseProject.setEnabled(true);
                    btnBrowseRuntime.setEnabled(true);
                    btnExport.setEnabled(true);
                    btnImport.setEnabled(true);
                    btnOutputBrowse.setEnabled(true);
                    btnRemoveAdditional.setEnabled(lstClasses.getSelectedIndex() > -1);
                    btnRemoveForceCompile.setEnabled(lstForceCompile.getSelectedIndex() > -1);
                    txtMainClass.setEnabled(true);
                    txtOutput.setEnabled(true);
                    txtProject.setEnabled(true);
                    txtRuntimeJava.setEnabled(true);
                }
                
                @Override
                public void run() {
                    try {
                        ClassCompiler compiler = new ClassCompiler(config, new ClassCompiler.ProgressListener() {

                            @Override
                            public void onProgress(final float percent) {
                                java.awt.EventQueue.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        if(percent > -1) {
                                            pgrStatus.setIndeterminate(false);
                                            pgrStatus.setValue((int)(percent*1000000));
                                        } else
                                            pgrStatus.setIndeterminate(true);
                                    }
                                });
                            }

                            @Override
                            public void onMessage(final String message) {
                                java.awt.EventQueue.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        pgrStatus.setString(message);
                                    }
                                });
                            }
                        });
                        compiler.createOutputDirectory();
                        compiler.compile();
                        compiler.writeIndex(compiler.copyLibraries());
                        
                        
                        java.awt.EventQueue.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(GUI.this, "Your files have been compiled and libs copied!", "Success", JOptionPane.INFORMATION_MESSAGE);
                                finished();
                            }
                        });
                    } catch(final Throwable t) {
                        java.awt.EventQueue.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(GUI.this, t.getMessage(), "Compiler Error", JOptionPane.ERROR_MESSAGE);
                                finished();
                                
                                t.printStackTrace();
                            }
                        });
                        
                    }
                }
            }.start();
        } catch (BadConfigState ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error occured", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_btnCompileActionPerformed

    private void btnBrowseRuntime1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBrowseRuntime1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnBrowseRuntime1ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            if(ge.isHeadlessInstance())
                throw new AWTException("Headless");
            
            /* Set the Nimbus look and feel */
            //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
            /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
             * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
             */
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException ex) {
                java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            } catch (InstantiationException ex) {
                java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            } catch (IllegalAccessException ex) {
                java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            } catch (javax.swing.UnsupportedLookAndFeelException ex) {
                java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
            //</editor-fold>

            /* Create and display the form */
            java.awt.EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    new GUI().setVisible(true);
                }
            });
        } catch(AWTError | AWTException ex) {
            if(ex.getMessage().contains("Headless")
                    || ex.getMessage().contains("headless")
                    || ex.getMessage().contains("HEADLESS"))
                CMD.main(args);
            else
                ex.printStackTrace();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddAdditional;
    private javax.swing.JButton btnAddForceCompile;
    private javax.swing.JButton btnBrowseProject;
    private javax.swing.JButton btnBrowseRuntime;
    private javax.swing.JButton btnBrowseRuntime1;
    private javax.swing.JButton btnCompile;
    private javax.swing.JButton btnExport;
    private javax.swing.JButton btnImport;
    private javax.swing.JButton btnOutputBrowse;
    private javax.swing.JButton btnRemoveAdditional;
    private javax.swing.JButton btnRemoveForceCompile;
    private javax.swing.JCheckBox chkProguard;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList lstClasses;
    private javax.swing.JList lstForceCompile;
    private javax.swing.JProgressBar pgrStatus;
    private javax.swing.JTextField txtMainClass;
    private javax.swing.JTextField txtOutput;
    private javax.swing.JTextField txtProject;
    private javax.swing.JTextField txtRuntimeJS;
    private javax.swing.JTextField txtRuntimeJava;
    // End of variables declaration//GEN-END:variables

}
