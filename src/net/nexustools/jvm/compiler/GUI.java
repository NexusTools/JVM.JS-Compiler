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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
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
    
    public void disableAll() {
        tabs.setSelectedIndex(0);
        
        tabs.setEnabled(false);
        btnBrowseRuntime1.setEnabled(false);
        txtRuntimeJS.setEnabled(false);
        btnCompile.setEnabled(false);
        btnBrowseProject.setEnabled(false);
        btnBrowseRuntime.setEnabled(false);
        btnExport.setEnabled(false);
        btnImport.setEnabled(false);
        btnOutputBrowse.setEnabled(false);
        txtMainClass.setEnabled(false);
        txtOutput.setEnabled(false);
        txtProject.setEnabled(false);
        txtRuntimeJava.setEnabled(false);
        cmbCompilerVersion.setEnabled(false);
    }
    
    public void enableAll() {
        tabs.setEnabled(true);
        txtRuntimeJS.setEnabled(true);
        btnCompile.setEnabled(true);
        btnBrowseRuntime1.setEnabled(false);
        btnBrowseProject.setEnabled(true);
        btnBrowseRuntime.setEnabled(true);
        btnExport.setEnabled(true);
        btnImport.setEnabled(true);
        btnOutputBrowse.setEnabled(true);
        txtMainClass.setEnabled(true);
        txtOutput.setEnabled(true);
        txtProject.setEnabled(true);
        txtRuntimeJava.setEnabled(true);
        cmbCompilerVersion.setEnabled(true);
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
        
        config.writeIndex = chkWriteIndex.isSelected();
        config.head.header = txtHeaderHeader.getText();
        config.head.footer = txtHeaderFooter.getText();
        config.body.header = txtBodyHeader.getText();
        config.body.footer = txtBodyFooter.getText();
        
        config.proguard = chkProguard.isSelected();
        return config;
    }
    
    public void loadConfig(File configFile) {
        try {
            Config config = Config.load(configFile);

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
            
            chkWriteIndex.setSelected(config.writeIndex);
            txtHeaderHeader.setText(config.head.header);
            txtHeaderFooter.setText(config.head.footer);
            txtBodyHeader.setText(config.body.header);
            txtBodyFooter.setText(config.body.footer);
        } catch (FileNotFoundException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error occured", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        btnCompile = new javax.swing.JButton();
        pgrStatus = new javax.swing.JProgressBar();
        btnExport = new javax.swing.JButton();
        btnImport = new javax.swing.JButton();
        tabs = new javax.swing.JTabbedPane();
        tabGeneral = new javax.swing.JPanel();
        jPanel5 = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        btnOutputBrowse = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        txtProject = new javax.swing.JTextField();
        txtOutput = new javax.swing.JTextField();
        btnBrowseProject = new javax.swing.JButton();
        btnBrowseRuntime = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        btnBrowseRuntime1 = new javax.swing.JButton();
        txtRuntimeJS = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        txtRuntimeJava = new javax.swing.JTextField();
        jPanel6 = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        txtMainClass = new javax.swing.JTextField();
        cmbCompilerVersion = new javax.swing.JComboBox();
        jLabel6 = new javax.swing.JLabel();
        btnMainBrowse = new javax.swing.JButton();
        tabOptimizations = new javax.swing.JPanel();
        jScrollPane6 = new javax.swing.JScrollPane();
        jTextPane4 = new javax.swing.JTextPane();
        jCheckBox1 = new javax.swing.JCheckBox();
        jCheckBox2 = new javax.swing.JCheckBox();
        jCheckBox3 = new javax.swing.JCheckBox();
        jCheckBox4 = new javax.swing.JCheckBox();
        tabLibsClasspaths = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstClasses = new javax.swing.JList();
        btnAddAdditional = new javax.swing.JButton();
        btnRemoveAdditional = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        jTextPane1 = new javax.swing.JTextPane();
        tabForceCompile = new javax.swing.JPanel();
        btnAddForceCompile = new javax.swing.JButton();
        btnRemoveForceCompile = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        lstForceCompile = new javax.swing.JList();
        jScrollPane4 = new javax.swing.JScrollPane();
        jTextPane2 = new javax.swing.JTextPane();
        tabIndexOutput = new javax.swing.JPanel();
        jPanel8 = new javax.swing.JPanel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jScrollPane9 = new javax.swing.JScrollPane();
        txtHeaderHeader = new javax.swing.JTextPane();
        jScrollPane10 = new javax.swing.JScrollPane();
        txtHeaderFooter = new javax.swing.JTextPane();
        jScrollPane11 = new javax.swing.JScrollPane();
        txtBodyHeader = new javax.swing.JTextPane();
        jScrollPane12 = new javax.swing.JScrollPane();
        txtBodyFooter = new javax.swing.JTextPane();
        jPanel10 = new javax.swing.JPanel();
        chkWriteIndex = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();
        txtScriptType = new javax.swing.JTextField();
        jPanel11 = new javax.swing.JPanel();
        jScrollPane8 = new javax.swing.JScrollPane();
        jList1 = new javax.swing.JList();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        tabProGuard = new javax.swing.JPanel();
        chkProguard = new javax.swing.JCheckBox();
        jScrollPane5 = new javax.swing.JScrollPane();
        jTextPane3 = new javax.swing.JTextPane();
        tabAbout = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("JVM.JS Compiler");

        btnCompile.setText("Compile");
        btnCompile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCompileActionPerformed(evt);
            }
        });

        pgrStatus.setString("Idle");
        pgrStatus.setStringPainted(true);

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

        tabs.setFocusable(false);

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder("Directories"));

        jLabel7.setText("JS Runtime Files Directory");

        btnOutputBrowse.setText("Browse");
        btnOutputBrowse.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOutputBrowseActionPerformed(evt);
            }
        });

        jLabel2.setText("Project Classes Directory");

        txtOutput.setText("output");

        btnBrowseProject.setText("Browse");
        btnBrowseProject.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBrowseProjectActionPerformed(evt);
            }
        });

        btnBrowseRuntime.setText("Browse");
        btnBrowseRuntime.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBrowseRuntimeActionPerformed(evt);
            }
        });

        jLabel1.setText("Java Runtime Class Directory");

        btnBrowseRuntime1.setText("Browse");
        btnBrowseRuntime1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBrowseRuntime1ActionPerformed(evt);
            }
        });

        txtRuntimeJS.setText("../Runtime/JS");

        jLabel3.setText("Output Directory");

        txtRuntimeJava.setText("../Runtime/Java/build/classes");

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(txtRuntimeJS, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnBrowseRuntime1))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtProject)
                            .addComponent(txtRuntimeJava))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(btnBrowseRuntime)
                            .addComponent(btnBrowseProject, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                        .addComponent(txtOutput, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnOutputBrowse)))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtRuntimeJava, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowseRuntime)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtRuntimeJS, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowseRuntime1)
                    .addComponent(jLabel7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnBrowseProject)
                    .addComponent(txtProject, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnOutputBrowse)
                    .addComponent(txtOutput, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)))
        );

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder("Extra"));

        jLabel5.setText("Main Class");

        cmbCompilerVersion.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Initial", "Testing" }));

        jLabel6.setText("Compiler Version");

        btnMainBrowse.setText("Browse");

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel5, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)
                    .addComponent(jLabel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cmbCompilerVersion, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(txtMainClass, javax.swing.GroupLayout.PREFERRED_SIZE, 328, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnMainBrowse)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMainClass, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5)
                    .addComponent(btnMainBrowse))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cmbCompilerVersion, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6)))
        );

        javax.swing.GroupLayout tabGeneralLayout = new javax.swing.GroupLayout(tabGeneral);
        tabGeneral.setLayout(tabGeneralLayout);
        tabGeneralLayout.setHorizontalGroup(
            tabGeneralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabGeneralLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tabGeneralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        tabGeneralLayout.setVerticalGroup(
            tabGeneralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabGeneralLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(238, Short.MAX_VALUE))
        );

        tabs.addTab("General", tabGeneral);

        jScrollPane6.setBorder(null);
        jScrollPane6.setViewportBorder(null);

        jTextPane4.setText("Optimizations can speed up the generated code, but are sometimes harder to debug");
        jTextPane4.setFocusable(false);
        jScrollPane6.setViewportView(jTextPane4);

        jCheckBox1.setSelected(true);
        jCheckBox1.setText("Optimize Math Operations");

        jCheckBox2.setSelected(true);
        jCheckBox2.setText("Skip Reference Counting");

        jCheckBox3.setSelected(true);
        jCheckBox3.setText("Unwrap Stacks");

        jCheckBox4.setSelected(true);
        jCheckBox4.setText("Enabled");

        javax.swing.GroupLayout tabOptimizationsLayout = new javax.swing.GroupLayout(tabOptimizations);
        tabOptimizations.setLayout(tabOptimizationsLayout);
        tabOptimizationsLayout.setHorizontalGroup(
            tabOptimizationsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabOptimizationsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tabOptimizationsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 630, Short.MAX_VALUE)
                    .addGroup(tabOptimizationsLayout.createSequentialGroup()
                        .addGroup(tabOptimizationsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jCheckBox1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jCheckBox2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jCheckBox3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jCheckBox4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        tabOptimizationsLayout.setVerticalGroup(
            tabOptimizationsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabOptimizationsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jCheckBox4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jCheckBox1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jCheckBox2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jCheckBox3)
                .addContainerGap(397, Short.MAX_VALUE))
        );

        tabs.addTab("Optimizations", tabOptimizations);

        jScrollPane1.setViewportView(lstClasses);

        btnAddAdditional.setText("Add");

        btnRemoveAdditional.setText("Remove");
        btnRemoveAdditional.setEnabled(false);

        jScrollPane3.setBorder(null);
        jScrollPane3.setViewportBorder(null);

        jTextPane1.setText("Add the classpaths of any additional libraries you want to include");
        jTextPane1.setFocusable(false);
        jScrollPane3.setViewportView(jTextPane1);

        javax.swing.GroupLayout tabLibsClasspathsLayout = new javax.swing.GroupLayout(tabLibsClasspaths);
        tabLibsClasspaths.setLayout(tabLibsClasspathsLayout);
        tabLibsClasspathsLayout.setHorizontalGroup(
            tabLibsClasspathsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabLibsClasspathsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tabLibsClasspathsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3)
                    .addGroup(tabLibsClasspathsLayout.createSequentialGroup()
                        .addGroup(tabLibsClasspathsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(btnRemoveAdditional, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnAddAdditional, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 547, Short.MAX_VALUE)))
                .addContainerGap())
        );
        tabLibsClasspathsLayout.setVerticalGroup(
            tabLibsClasspathsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tabLibsClasspathsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tabLibsClasspathsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tabLibsClasspathsLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnAddAdditional)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnRemoveAdditional))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 481, Short.MAX_VALUE))
                .addContainerGap())
        );

        tabs.addTab("Libs Classpaths", tabLibsClasspaths);

        btnAddForceCompile.setText("Add");
        btnAddForceCompile.setAutoscrolls(true);

        btnRemoveForceCompile.setText("Remove");
        btnRemoveForceCompile.setAutoscrolls(true);
        btnRemoveForceCompile.setEnabled(false);

        jScrollPane2.setViewportView(lstForceCompile);

        jScrollPane4.setBorder(null);
        jScrollPane4.setViewportBorder(null);

        jTextPane2.setText("Add any classes you need compiled for reflection\nAnything in META-INF/services will be automatically included");
        jTextPane2.setFocusable(false);
        jScrollPane4.setViewportView(jTextPane2);

        javax.swing.GroupLayout tabForceCompileLayout = new javax.swing.GroupLayout(tabForceCompile);
        tabForceCompile.setLayout(tabForceCompileLayout);
        tabForceCompileLayout.setHorizontalGroup(
            tabForceCompileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tabForceCompileLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tabForceCompileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane4)
                    .addGroup(tabForceCompileLayout.createSequentialGroup()
                        .addGroup(tabForceCompileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(btnRemoveForceCompile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnAddForceCompile, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 547, Short.MAX_VALUE)))
                .addContainerGap())
        );
        tabForceCompileLayout.setVerticalGroup(
            tabForceCompileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tabForceCompileLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tabForceCompileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tabForceCompileLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnAddForceCompile)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnRemoveForceCompile))
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 466, Short.MAX_VALUE))
                .addContainerGap())
        );

        tabs.addTab("Force Compile", tabForceCompile);

        jPanel8.setBorder(javax.swing.BorderFactory.createTitledBorder("Template"));

        jScrollPane9.setViewportView(txtHeaderHeader);

        jTabbedPane1.addTab("Head/Header", jScrollPane9);

        jScrollPane10.setViewportView(txtHeaderFooter);

        jTabbedPane1.addTab("Head/Footer", jScrollPane10);

        jScrollPane11.setViewportView(txtBodyHeader);

        jTabbedPane1.addTab("Body/Header", jScrollPane11);

        jScrollPane12.setViewportView(txtBodyFooter);

        jTabbedPane1.addTab("Body/Footer", jScrollPane12);

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 154, Short.MAX_VALUE)
        );

        jPanel10.setBorder(javax.swing.BorderFactory.createTitledBorder("General"));

        chkWriteIndex.setSelected(true);
        chkWriteIndex.setText("Write index.html");

        jLabel4.setText("Script Type");

        txtScriptType.setText("text/javascript");

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(chkWriteIndex, javax.swing.GroupLayout.DEFAULT_SIZE, 590, Short.MAX_VALUE))
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addGap(28, 28, 28)
                        .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 183, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtScriptType)))
                .addContainerGap())
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(chkWriteIndex)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(txtScriptType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel11.setBorder(javax.swing.BorderFactory.createTitledBorder("Template Files to Copy"));

        jList1.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jScrollPane8.setViewportView(jList1);

        jButton1.setText("Remove");
        jButton1.setEnabled(false);

        jButton2.setText("Add");
        jButton2.setEnabled(false);

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel11Layout.createSequentialGroup()
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane8)
                .addContainerGap())
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel11Layout.createSequentialGroup()
                .addComponent(jScrollPane8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel11Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButton2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton1)
                .addContainerGap())
        );

        javax.swing.GroupLayout tabIndexOutputLayout = new javax.swing.GroupLayout(tabIndexOutput);
        tabIndexOutput.setLayout(tabIndexOutputLayout);
        tabIndexOutputLayout.setHorizontalGroup(
            tabIndexOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tabIndexOutputLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tabIndexOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel10, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel8, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        tabIndexOutputLayout.setVerticalGroup(
            tabIndexOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tabIndexOutputLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        tabs.addTab("Index Output", tabIndexOutput);

        chkProguard.setText("Enabled");

        jScrollPane5.setBorder(null);
        jScrollPane5.setViewportBorder(null);

        jTextPane3.setContentType("text/html"); // NOI18N
        jTextPane3.setText("<html>\n  <head>\n\n  </head>\n  <body>\n    <p style=\"margin-top: 0\">\n      <font color=\"red\">THE JVM IS STILL EXPERIMENTAL,\n\t<br />PROGUARD MAY BREAK WHAT WORK OTHERWISE WORK</font>\n    </p>\n  </body>\n</html>\n");
        jTextPane3.setFocusable(false);
        jScrollPane5.setViewportView(jTextPane3);

        javax.swing.GroupLayout tabProGuardLayout = new javax.swing.GroupLayout(tabProGuard);
        tabProGuard.setLayout(tabProGuardLayout);
        tabProGuardLayout.setHorizontalGroup(
            tabProGuardLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabProGuardLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tabProGuardLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 630, Short.MAX_VALUE)
                    .addComponent(chkProguard, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        tabProGuardLayout.setVerticalGroup(
            tabProGuardLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabProGuardLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(chkProguard)
                .addContainerGap(454, Short.MAX_VALUE))
        );

        tabs.addTab("ProGuard", tabProGuard);

        javax.swing.GroupLayout tabAboutLayout = new javax.swing.GroupLayout(tabAbout);
        tabAbout.setLayout(tabAboutLayout);
        tabAboutLayout.setHorizontalGroup(
            tabAboutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 642, Short.MAX_VALUE)
        );
        tabAboutLayout.setVerticalGroup(
            tabAboutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 522, Short.MAX_VALUE)
        );

        tabs.addTab("About", tabAbout);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(pgrStatus, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnImport)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnExport)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnCompile)
                        .addContainerGap())
                    .addComponent(tabs)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tabs)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(btnCompile)
                        .addComponent(btnExport)
                        .addComponent(btnImport))
                    .addComponent(pgrStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

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
            loadConfig(chooser.getSelectedFile());
        }
            
    }//GEN-LAST:event_btnImportActionPerformed

    private void btnCompileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCompileActionPerformed
        try {
            final Config config = convertToConfig();
            pgrStatus.setMaximum(1000000);
            pgrStatus.setString("Starting...");
            disableAll();
            
            new Thread("ClassCompiler") {
                public void finished() {
                    pgrStatus.setIndeterminate(false);
                    pgrStatus.setString("Idle");
                    pgrStatus.setValue(0);
                    
                    enableAll();
                }
                
                @Override
                public void run() {
                    try {
                        Compiler compiler = new Compiler(config, new Compiler.ProgressListener() {

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
                        if(config.writeIndex)
                            compiler.writeIndex(compiler.copyLibraries());
                        else
                            compiler.copyLibraries();
                        
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
    public static void main(final String args[]) {
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
                    GUI gui = new GUI();
                    if(args.length > 0)
                        gui.loadConfig(new File(args[args.length-1]));
                    gui.setVisible(true);
                }
            });
        } catch(AWTError | AWTException ex) {
            CMD.main(args);
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
    private javax.swing.JButton btnMainBrowse;
    private javax.swing.JButton btnOutputBrowse;
    private javax.swing.JButton btnRemoveAdditional;
    private javax.swing.JButton btnRemoveForceCompile;
    private javax.swing.JCheckBox chkProguard;
    private javax.swing.JCheckBox chkWriteIndex;
    private javax.swing.JComboBox cmbCompilerVersion;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JCheckBox jCheckBox2;
    private javax.swing.JCheckBox jCheckBox3;
    private javax.swing.JCheckBox jCheckBox4;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JList jList1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane10;
    private javax.swing.JScrollPane jScrollPane11;
    private javax.swing.JScrollPane jScrollPane12;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JScrollPane jScrollPane9;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTextPane jTextPane1;
    private javax.swing.JTextPane jTextPane2;
    private javax.swing.JTextPane jTextPane3;
    private javax.swing.JTextPane jTextPane4;
    private javax.swing.JList lstClasses;
    private javax.swing.JList lstForceCompile;
    private javax.swing.JProgressBar pgrStatus;
    private javax.swing.JPanel tabAbout;
    private javax.swing.JPanel tabForceCompile;
    private javax.swing.JPanel tabGeneral;
    private javax.swing.JPanel tabIndexOutput;
    private javax.swing.JPanel tabLibsClasspaths;
    private javax.swing.JPanel tabOptimizations;
    private javax.swing.JPanel tabProGuard;
    private javax.swing.JTabbedPane tabs;
    private javax.swing.JTextPane txtBodyFooter;
    private javax.swing.JTextPane txtBodyHeader;
    private javax.swing.JTextPane txtHeaderFooter;
    private javax.swing.JTextPane txtHeaderHeader;
    private javax.swing.JTextField txtMainClass;
    private javax.swing.JTextField txtOutput;
    private javax.swing.JTextField txtProject;
    private javax.swing.JTextField txtRuntimeJS;
    private javax.swing.JTextField txtRuntimeJava;
    private javax.swing.JTextField txtScriptType;
    // End of variables declaration//GEN-END:variables

}
