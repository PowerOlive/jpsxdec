/*
 * jPSXdec: Playstation 1 Media Decoder/Converter in Java
 * Copyright (C) 2007  Michael Sabin
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,   
 * Boston, MA  02110-1301, USA.
 *
 */

/*
 * Gui.java
 */

package jpsxdec;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import jpsxdec.Progress.SimpleWorker.TaskInfo;
import jpsxdec.cdreaders.CDSectorReader;
import jpsxdec.media.MediaHandler;
import jpsxdec.media.PSXMedia;
import jpsxdec.nativeclass.VideoForWindows;
import jpsxdec.util.IProgressCallback;

public class Gui extends javax.swing.JFrame {
    private final File INI_FILE = new File("jpsxdec.ini");
    
    private CDSectorReader m_oCD;
    private MediaHandler m_oMediaList;
    private DefaultListModel m_oListItems = new DefaultListModel();
    private DefaultComboBoxModel m_oOutputFormatItems;
    private String m_sIndexFile;
    private File m_oLastBrowseFolder;
    private File m_oLastSaveFolder;
   
    /** Creates new form Gui */
    public Gui() {
        initComponents();
        
        // use the Windows L&F if it exists (for great justice!)
        UIManager.LookAndFeelInfo looks[] = UIManager.getInstalledLookAndFeels();
        for (UIManager.LookAndFeelInfo laf : looks) {
            if (laf.getName().equals("Windows"))  {
                try {
                    UIManager.setLookAndFeel(laf.getClassName());
                    SwingUtilities.updateComponentTreeUI(this);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                break;
            }
        }
        guiMediaList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        guiMediaList.setModel(m_oListItems);
        
        Vector<String> oImgFrmts = jpsxdec.util.Misc.GetJavaImageFormats();
        //{"yuv", "y4m", "0rlc", "demux"}
        oImgFrmts.add("demux");
        oImgFrmts.add("0rlc");
        m_oOutputFormatItems = new DefaultComboBoxModel(oImgFrmts);
        guiOutputFormat.setModel(m_oOutputFormatItems);
        guiOutputFormat.setSelectedItem("png");
        if (INI_FILE.exists()) {
            try {
                BufferedReader oReader = new BufferedReader(new FileReader(INI_FILE));
                m_oLastBrowseFolder = new File(oReader.readLine());
                m_oLastSaveFolder = new File(oReader.readLine());
                oReader.close();
            } catch (IOException ex) {
                Logger.getLogger(Gui.class.getName()).log(Level.SEVERE, null, ex);
            } 
        }
    }
    
    private boolean VerifyInputFile() {
        if (new File(guiInputFile.getText()).exists()) {
            return true;
        } else {
            JOptionPane.showMessageDialog(this, "Input file does not exist");
            return false;
        }
            
    }

    private void PopulateList() {
        for (PSXMedia oMedia : m_oMediaList) {
            m_oListItems.addElement(oMedia.toString());
        }
    }

    private void DisableIndexButtons() {
        guiGenerateIdx.setEnabled(false);
        guiLoadIdx.setEnabled(false);
        guiBrowseBtn.setEnabled(false);
        guiInputFile.setEnabled(false);
    }
    
    private void PromptToSaveIndex() {
        int iOpt = JOptionPane.showConfirmDialog(this, 
                                      "Would you like to save the index?", 
                                      "Indexing complete", 
                                      JOptionPane.YES_NO_OPTION);
        if (iOpt == 0) {
            FileDialog fd = new FileDialog(this, "Save index", FileDialog.SAVE);
            if (m_oLastBrowseFolder != null && m_oLastBrowseFolder.exists())
                fd.setDirectory(m_oLastBrowseFolder.getPath());
            fd.setVisible(true);
            m_oLastBrowseFolder = new File(fd.getDirectory());
            if (fd.getFile() != null) {
                m_sIndexFile = new File(fd.getDirectory(), fd.getFile()).getPath();
                // save the index
                try {
                    PrintStream oPrinter = new PrintStream(m_sIndexFile);
                    m_oMediaList.SerializeMediaList(oPrinter);
                    oPrinter.close();
                    guiIndexFile.setText(m_sIndexFile);
                } catch (IOException ex) {
                    Logger.getLogger(Gui.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    
    
    private void DecodeMediaItem(final PSXMedia oMedia, String sFile) {
        /*
        String sNameEnd = "";
        if ((oMedia.getMediaType() & PSXMedia.MEDIA_TYPE_AUDIO) > 0)
            sNameEnd = "";
        else if ((oMedia.getMediaType() & PSXMedia.MEDIA_TYPE_IMAGE) > 0)
            sNameEnd = "_p%d";
        else if ((oMedia.getMediaType() & PSXMedia.MEDIA_TYPE_VIDEO) > 0)
            sNameEnd = "_f%04d";
        else if ((oMedia.getMediaType() & PSXMedia.MEDIA_TYPE_XA) > 0)
            sNameEnd = "_c%02d";
        */
        
        final boolean blnDecodeVideo = true;
        final boolean blnDecodeAudio = true;
        final double dblAudioScale = 1.0;
        final String sOutputAudFormat = "wav";
        final int iChannel = -1;
        final long lngStartFrame = -1;
        final long lngEndFrame = -1;

        final String sFinalName = 
                String.format("%s%03d", 
                    sFile,
                    oMedia.getIndex());

        final String sOutputImgFormat = guiOutputFormat.getSelectedItem().toString();
        
        Progress oSaveTask = new Progress(this, "Saving " + oMedia.toString(), new Progress.SimpleWorker<Void>() {
            @Override
            Void task(final TaskInfo task) {

                oMedia.setCallback(new IProgressCallback.IProgressCallbackEventError() {

                    public boolean ProgressCallback(String sEvent) {
                        task.updateEvent(sEvent);
                        return !task.cancelPressed();
                    }

                    public boolean ProgressCallback(String sWhatDoing, double dblPercentComplete) {
                        task.updateNote(sWhatDoing);
                        task.updateProgress((int)(dblPercentComplete * 100));
                        return !task.cancelPressed();
                    }

                    public void ProgressCallback(Exception e) {
                        task.showError(e);
                        //JOptionPane.showMessageDialog(task.getWindow(), e.getMessage());
                    }
                });

                if (oMedia.hasAudio()) {
                    oMedia.DecodeAudio(sFinalName, "wav", dblAudioScale);
                }

                if (oMedia.hasVideo()) {
                    oMedia.DecodeVideo(sFinalName, sOutputImgFormat,
                                        null, null);
                }

                if (oMedia.hasXAChannels()) {
                    oMedia.DecodeXA(sFinalName, "wav", dblAudioScale, null);
                }

                if (oMedia.hasImage()) {
                    oMedia.DecodeImage(sFinalName, sOutputImgFormat);
                }

                oMedia.setCallback(null);

                return null;
            }
        });
            
        oSaveTask.setVisible(true);
        if (oSaveTask.threwException()) {

        } else {
            if (oSaveTask.wasCanceled()) {

            }
        }

    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        guiInputFile = new javax.swing.JTextField();
        guiMediaListPanel = new javax.swing.JScrollPane();
        guiMediaList = new javax.swing.JList();
        guiBrowseBtn = new javax.swing.JButton();
        guiGenerateIdx = new javax.swing.JButton();
        guiLoadIdx = new javax.swing.JButton();
        guiIndexFileLbl = new javax.swing.JLabel();
        guiIndexFile = new javax.swing.JLabel();
        guiOutputFormat = new javax.swing.JComboBox();
        guiSave = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("jPSXdec");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        guiMediaList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                guiMediaListValueChanged(evt);
            }
        });
        guiMediaListPanel.setViewportView(guiMediaList);

        guiBrowseBtn.setText("Browse...");
        guiBrowseBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                guiBrowseBtnActionPerformed(evt);
            }
        });

        guiGenerateIdx.setText("Generate Index");
        guiGenerateIdx.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                guiGenerateIdxActionPerformed(evt);
            }
        });

        guiLoadIdx.setText("Load Index");
        guiLoadIdx.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                guiLoadIdxActionPerformed(evt);
            }
        });

        guiIndexFileLbl.setText("Index File:");
        guiIndexFileLbl.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                guiIndexFileLblMouseClicked(evt);
            }
        });

        guiIndexFile.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        guiIndexFile.setText("None");
        guiIndexFile.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        guiIndexFile.setFocusable(false);

        guiSave.setText("Save...");
        guiSave.setEnabled(false);
        guiSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                guiSaveActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(guiMediaListPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 265, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, guiSave)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, guiLoadIdx, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, guiOutputFormat, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, guiIndexFileLbl, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(guiGenerateIdx, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, guiIndexFile, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 97, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                    .add(layout.createSequentialGroup()
                        .add(guiInputFile, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 295, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(guiBrowseBtn)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(guiBrowseBtn)
                    .add(guiInputFile, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(guiGenerateIdx)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(guiLoadIdx)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(guiIndexFileLbl)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(guiIndexFile, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 116, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(guiOutputFormat, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(guiSave))
                    .add(guiMediaListPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 249, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    
    private void guiSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guiSaveActionPerformed
        FileDialog fd = new FileDialog(this, "Save", FileDialog.SAVE);
        if (m_oLastSaveFolder != null && m_oLastSaveFolder.exists())
            fd.setDirectory(m_oLastSaveFolder.getPath());
        fd.setVisible(true);
        m_oLastSaveFolder = new File(fd.getDirectory());
        if (fd.getFile() != null && new File(fd.getDirectory()).exists()) {
            String sFile = new File(fd.getDirectory(), fd.getFile()).getPath();
            // decode and save file(s)
            
            //JOptionPane.showMessageDialog(this, sFile);
            
            final PSXMedia oMedia = m_oMediaList.getBySting(guiMediaList.getSelectedValue().toString());
            DecodeMediaItem(oMedia, sFile);
            
        } // if valid file

    }//GEN-LAST:event_guiSaveActionPerformed

    
    private void guiMediaListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_guiMediaListValueChanged
        System.out.println(evt.getFirstIndex());
        guiSave.setEnabled(true);
    }//GEN-LAST:event_guiMediaListValueChanged

    private void guiLoadIdxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guiLoadIdxActionPerformed
        if (VerifyInputFile()) {
            try {
                m_oCD = new CDSectorReader(guiInputFile.getText());
                FileDialog fd = new FileDialog(this, "Load Index", FileDialog.LOAD);
                if (m_oLastBrowseFolder != null && m_oLastBrowseFolder.exists())
                    fd.setDirectory(m_oLastBrowseFolder.getPath());
                fd.setVisible(true);
                if (fd.getFile() != null) {
                    m_sIndexFile = new File(fd.getDirectory(), fd.getFile()).getPath();
                    if (new File(m_sIndexFile).exists()) {
                        // if everything went swimmingly
                        // load index
                        m_oMediaList = new MediaHandler(m_oCD, m_sIndexFile);
                        if (m_oMediaList.size() > 0) {
                            DisableIndexButtons();
                            PopulateList();
                            guiIndexFile.setText(m_sIndexFile);
                        } else {
                            JOptionPane.showMessageDialog(this, "No data found. Are you sure this is an index file?");
                            m_oMediaList = null;
                        }
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(Gui.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_guiLoadIdxActionPerformed

    private void guiGenerateIdxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guiGenerateIdxActionPerformed
        if (VerifyInputFile()) {
            try {

                m_oCD = new CDSectorReader(guiInputFile.getText());
                if (!m_oCD.HasSectorHeader())
                    JOptionPane.showMessageDialog(this, 
                            "This file does not contain entire raw CD sectors.\n" +
                            "Audio cannot be decoded.");
                // generate index
                //"This could take a long time";
                Progress oIndexTask = new Progress(this, "Indexing " + m_oCD.getSourceFile(), new Progress.SimpleWorker<Void>() {

                    @Override
                    Void task(final TaskInfo task) {
                        task.updateEvent("This could take a very long time.");
                        try {
                            m_oMediaList = new MediaHandler(m_oCD, new IProgressCallback.IProgressCallbackEventError() {

                                public boolean ProgressCallback(String sEvent) {
                                    task.updateEvent(sEvent);
                                    return !task.cancelPressed();
                                }

                                public boolean ProgressCallback(String sWhatDoing, double dblPercentComplete) {
                                    task.updateProgress((int) (dblPercentComplete*100));
                                    task.updateNote(sWhatDoing);
                                    return !task.cancelPressed();
                                }

                                public void ProgressCallback(Exception e) {
                                    task.showError(e);
                                }
                            });
                            return null;
                        } catch (IOException ex) {
                            task.showError(ex);
                        }
                        return null;
                    }
                });
                oIndexTask.setVisible(true);
                if (!oIndexTask.wasCanceled() && !oIndexTask.threwException() && m_oMediaList.size() > 0) {
                    this.DisableIndexButtons();
                    this.PopulateList();
                    PromptToSaveIndex();
                }
                
            } catch (IOException ex) {
                Logger.getLogger(Gui.class.getName()).log(Level.SEVERE, null, ex);
            }
                
                
        }
    }//GEN-LAST:event_guiGenerateIdxActionPerformed

    
    
    
    private void guiBrowseBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guiBrowseBtnActionPerformed
        FileDialog fd = new FileDialog(this, "Open", FileDialog.LOAD);
        if (m_oLastBrowseFolder != null)
            fd.setDirectory(m_oLastBrowseFolder.getPath());
        fd.setVisible(true);
        m_oLastBrowseFolder = new File(fd.getDirectory());
        if (fd.getFile() != null)
            guiInputFile.setText(new File(fd.getDirectory(), fd.getFile()).getPath());
    }//GEN-LAST:event_guiBrowseBtnActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        try {
            if (m_oCD != null) m_oCD.close();
            PrintWriter oFOS = new PrintWriter(INI_FILE);
            oFOS.println(m_oLastBrowseFolder != null ? m_oLastBrowseFolder.toString() : "");
            oFOS.println(m_oLastSaveFolder != null ? m_oLastSaveFolder.toString() : "");
            oFOS.close();
        } catch (IOException ex) {
            Logger.getLogger(Gui.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }//GEN-LAST:event_formWindowClosing

    private void guiIndexFileLblMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_guiIndexFileLblMouseClicked
        
        /*
        PSXMedia oMedia = m_oMediaList.getBySting(guiMediaList.getSelectedValue().toString());
            
        if (oMedia instanceof PSXMediaSTR) {
            PSXMediaSTR oStrMedia = (PSXMediaSTR)oMedia;

            //JOptionPane.showMessageDialog(this, oStrMedia.CalculateFrameLength());
            JOptionPane.showMessageDialog(this, oStrMedia.CalculateFrameRateBase() + "\n" + oStrMedia.CalculateFrameRateWacked());
        }
        */  
        
        //JOptionPane.showMessageDialog(this, this.getClass().getName() +"\n"+ this.getTitle());
        
        VideoForWindows ovfw = new VideoForWindows();
        /*
        JOptionPane.showMessageDialog(this, 
                ovfw.FindWindow(this.getClass().getName(), this.getTitle())
        );
        */

        int i = ovfw.Init("\u3053\u3093FISH.avi", 320, 240, 15, 1, 0, 37000);
        System.out.println("" + i);
        
        int hWnd = ovfw.FindWindow(this.getClass().getName(), this.getTitle());
        System.out.println("hWnd=" + hWnd);
        
        i = ovfw.PromptForCompression(hWnd);
        System.out.println("" + i);
        
        i = ovfw.Close();
        System.out.println("" + i);
    }//GEN-LAST:event_guiIndexFileLblMouseClicked
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton guiBrowseBtn;
    private javax.swing.JButton guiGenerateIdx;
    private javax.swing.JLabel guiIndexFile;
    private javax.swing.JLabel guiIndexFileLbl;
    private javax.swing.JTextField guiInputFile;
    private javax.swing.JButton guiLoadIdx;
    private javax.swing.JList guiMediaList;
    private javax.swing.JScrollPane guiMediaListPanel;
    private javax.swing.JComboBox guiOutputFormat;
    private javax.swing.JButton guiSave;
    // End of variables declaration//GEN-END:variables
    
    

}
