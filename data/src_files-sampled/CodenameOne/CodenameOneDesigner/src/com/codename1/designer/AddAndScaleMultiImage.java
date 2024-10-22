package com.codename1.designer;

import com.codename1.designer.ResourceEditorView;
import com.codename1.ui.EncodedImage;
import com.codename1.ui.util.EditableResources;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class AddAndScaleMultiImage extends javax.swing.JPanel {
    private float aspect;
    private File selectedFile;
    
    public AddAndScaleMultiImage() {
        initComponents();
        initSpinner(veryLowWidth, "veryLowWidthSpinner", 28);
        initSpinner(veryLowHeight, "veryLowHeightSpinner", 28);
        initSpinner(lowWidth, "lowWidthSpinner", 36);
        initSpinner(lowHeight, "lowHeightSpinner", 36);
        initSpinner(mediumWidth, "mediumWidthSpinner", 48);
        initSpinner(mediumHeight, "mediumHeightSpinner", 48);
        initSpinner(highWidth, "highWidthSpinner", 72);
        initSpinner(highHeight, "highHeightSpinner", 72);
        initSpinner(veryHighWidth, "veryHighWidthSpinner", 96);
        initSpinner(veryHighHeight, "veryHighHeightSpinner", 96);
        initSpinner(hdWidth, "hdWidthSpinner", 196);
        initSpinner(hdHeight, "hdHeightSpinner", 196);
        initSpinner(hd560Width, "hd560WidthSpinner", 250);
        initSpinner(hd560Height, "hd560HeightSpinner", 250);
        initSpinner(hd2Width, "hd2WidthSpinner", 330);
        initSpinner(hd2Height, "hd2HeightSpinner", 330);
        initSpinner(hd4kWidth, "hd4kWidthSpinner", 400);
        initSpinner(hd4kHeight, "hd4kHeightSpinner", 400);
        percentWidth.setModel(new SpinnerNumberModel(20.0, 1.0, 100.0, 1.0));
        percentHeight.setModel(new SpinnerNumberModel(15.0, 1.0, 100.0, 1.0));
    }

    private float get(JSpinner s) {
        return ((Number)s.getValue()).floatValue();
    }

    private EncodedImage createScale(BufferedImage bi, JSpinner ws, JSpinner hs) throws IOException {
        int w = (int)get(ws);
        int h = (int)get(hs);
        if(w != 0 && h != 0) {
            return EncodedImage.create(scale(bi, w, h));
        }
        return null;
    }

    public static void generateImpl(File[] files, EditableResources res, int sourceResolution) throws IOException {
        for(File f : files) {
            BufferedImage bi = ImageIO.read(f);
            generateMulti(sourceResolution, bi, f.getName(), res);
        }
    }
    
    public void generate(File[] files, EditableResources res, int sourceResolution) {
        for(File f : files) {
            try {
                BufferedImage bi = ImageIO.read(f);
                generateMulti(sourceResolution, bi, f.getName(), res);
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error reading file: " + f, "IO Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void generateMulti(int sourceResolution, BufferedImage bi, String name, EditableResources res) throws IOException {
        EditableResources.MultiImage newImage = new EditableResources.MultiImage();
        
        int[] DPIS = new int[] {com.codename1.ui.Display.DENSITY_VERY_LOW,
            com.codename1.ui.Display.DENSITY_LOW,
            com.codename1.ui.Display.DENSITY_MEDIUM,
            com.codename1.ui.Display.DENSITY_HIGH,
            com.codename1.ui.Display.DENSITY_VERY_HIGH,
            com.codename1.ui.Display.DENSITY_HD,
            ;
        float[] WIDTHS = {
            176, 240, 360, 480, 640, 1024 ;
        
        switch(sourceResolution) {
            case 6: DPIS = new int[] {com.codename1.ui.Display.DENSITY_VERY_LOW,
                            com.codename1.ui.Display.DENSITY_LOW,
                            com.codename1.ui.Display.DENSITY_MEDIUM,
                            com.codename1.ui.Display.DENSITY_HIGH,
                            com.codename1.ui.Display.DENSITY_VERY_HIGH,
                            com.codename1.ui.Display.DENSITY_HD,
                            com.codename1.ui.Display.DENSITY_560,
                            ;
                WIDTHS = new float[] {
                    176, 240, 360, 480, 640, 1024, 1500, ;
                break;
            case 7: DPIS = new int[] {com.codename1.ui.Display.DENSITY_VERY_LOW,
                            com.codename1.ui.Display.DENSITY_LOW,
                            com.codename1.ui.Display.DENSITY_MEDIUM,
                            com.codename1.ui.Display.DENSITY_HIGH,
                            com.codename1.ui.Display.DENSITY_VERY_HIGH,
                            com.codename1.ui.Display.DENSITY_HD,
                            com.codename1.ui.Display.DENSITY_560,
                            com.codename1.ui.Display.DENSITY_2HD,
                            ;
                WIDTHS = new float[] {
                    176, 240, 360, 480, 640, 1024, 1500, 2000;
                break;
            case 8:  DPIS = new int[] {com.codename1.ui.Display.DENSITY_VERY_LOW,
                            com.codename1.ui.Display.DENSITY_LOW,
                            com.codename1.ui.Display.DENSITY_MEDIUM,
                            com.codename1.ui.Display.DENSITY_HIGH,
                            com.codename1.ui.Display.DENSITY_VERY_HIGH,
                            com.codename1.ui.Display.DENSITY_HD,
                            com.codename1.ui.Display.DENSITY_560,
                            com.codename1.ui.Display.DENSITY_2HD,
                            com.codename1.ui.Display.DENSITY_4K
                        };
                WIDTHS = new float[] {
                    176, 240, 360, 480, 640, 1024, 1500, 2000, 2500
                };
                break;
            default:
                break;
        }
        
        EncodedImage[] images = new EncodedImage[WIDTHS.length];
        int imageCount = WIDTHS.length;
        
        for(int iter = 0 ; iter < DPIS.length ; iter++) {
            if(iter == sourceResolution) {
                images[iter] = EncodedImage.create(scale(bi, bi.getWidth(), bi.getHeight()));
            } else {
                float sourceWidth = WIDTHS[sourceResolution];
                float destWidth = WIDTHS[iter];
                int h = (int)(((float)bi.getHeight()) * destWidth / sourceWidth);
                int w = (int)(((float)bi.getWidth()) * destWidth / sourceWidth);
                images[iter] = EncodedImage.create(scale(bi, w, h));
            }
        }
        
        if(imageCount > 0) {
            int offset = 0;
            EncodedImage[] result = new EncodedImage[imageCount];
            int[] resultDPI = new int[imageCount];
            for(int iter = 0 ; iter < images.length ; iter++) {
                if(images[iter] != null) {
                    result[offset] = images[iter];
                    resultDPI[offset] = DPIS[iter];
                    offset++;
                }
            }
            newImage.setDpi(resultDPI);
            newImage.setInternalImages(result);
            String destName = name;
            int count = 1;
            while(res.containsResource(destName)) {
                destName = name + " " + count;
                count++;
            }
            res.setMultiImage(destName, newImage);
        }
    }

    public void generate(File[] files, EditableResources res) {
        for(File f : files) {
            try {
                BufferedImage bi = ImageIO.read(f);
                EditableResources.MultiImage newImage = new EditableResources.MultiImage();
                
                int[] DPIS = new int[] {com.codename1.ui.Display.DENSITY_VERY_LOW,
                    com.codename1.ui.Display.DENSITY_LOW,
                    com.codename1.ui.Display.DENSITY_MEDIUM,
                    com.codename1.ui.Display.DENSITY_HIGH,
                    com.codename1.ui.Display.DENSITY_VERY_HIGH,
                    com.codename1.ui.Display.DENSITY_HD,
                    com.codename1.ui.Display.DENSITY_560,
                    com.codename1.ui.Display.DENSITY_2HD,
                    com.codename1.ui.Display.DENSITY_4K
                };
                EncodedImage[] images = new EncodedImage[DPIS.length];
                int imageCount = 0;
                JSpinner[] ws = {veryLowWidth, lowWidth, mediumWidth, highWidth, veryHighWidth, hdWidth, hd560Width, hd2Width, hd4kWidth};
                JSpinner[] hs = {veryLowHeight, lowHeight, mediumHeight, highHeight, veryHighHeight, hdHeight, hd560Height, hd2Height, hd4kHeight};
                if(squareImages.isSelected()) {
                    hs = ws;
                }

                for(int iter = 0 ; iter < ws.length ; iter++) {
                    images[iter] = createScale(bi, ws[iter], hs[iter]);
                    if(images[iter] != null) {
                        imageCount++;
                    }
                }

                if(imageCount > 0) {
                    int offset = 0;
                    EncodedImage[] result = new EncodedImage[imageCount];
                    int[] resultDPI = new int[imageCount];
                    for(int iter = 0 ; iter < images.length ; iter++) {
                        if(images[iter] != null) {
                            result[offset] = images[iter];
                            resultDPI[offset] = DPIS[iter];
                            offset++;
                        }
                    }
                    newImage.setDpi(resultDPI);
                    newImage.setInternalImages(result);
                    String destName = f.getName();
                    int count = 1;
                    while(res.containsResource(destName)) {
                        destName = f.getName() + " " + count;
                    }
                    res.setMultiImage(destName, newImage);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error reading file: " + f, "IO Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private static BufferedImage getScaledInstance(BufferedImage img,
                                            int targetWidth,
                                            int targetHeight) {
        if (targetWidth < 0) {
            throw new IllegalArgumentException(String.format("Negative target sizes not allowed: %d", targetWidth));
        }
        if (targetHeight < 0) {
            throw new IllegalArgumentException(String.format("Negative target sizes not allowed: %d", targetHeight));
        }
        int type = (img.getTransparency() == Transparency.OPAQUE) ?
                BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = img;
        int w;
        int h;
        w = img.getWidth();
        h = img.getHeight();

        int breakLoop = 0;
        
        do {
            breakLoop++;
            
            w = reduce(w, targetWidth);
            h = reduce(h, targetHeight);

            BufferedImage tmp = new BufferedImage(w, h, type);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.drawImage(ret, 0, 0, w, h, null);
            g2.dispose();

            ret = tmp;
            if(breakLoop > 20) {
                return null;
            }
        } while ((w != targetWidth) || (h != targetHeight));

        return ret;
    }

    private static int reduce(int dimension, int targetDimension) {
        if (dimension > targetDimension) {
            dimension /= 2;
            if (dimension < targetDimension) {
                dimension = targetDimension;
            }
        } else if (dimension < targetDimension) {
            dimension = targetDimension;
        }
        return dimension;
    }    

    private static byte[] scale(BufferedImage bi, int w, int h) throws IOException {
        BufferedImage newbi = getScaledInstance(bi, w, h);
        if(newbi != null) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(newbi, "png", output);
            output.close();
            return output.toByteArray();
        }
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(bi, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
        g2d.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(scaled, "png", output);
        output.close();
        return output.toByteArray();
    }

    private void initSpinner(final JSpinner s, final String name, int defaultVal) {
        int val = Preferences.userNodeForPackage(ResourceEditorView.class).getInt(name, defaultVal);
        s.setModel(new SpinnerNumberModel(val, 0, 3000, 1));
        s.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int val = ((Number)s.getValue()).intValue();
                if(val >= 0 && val <= 3000) {
                    Preferences.userNodeForPackage(ResourceEditorView.class).putInt(name, val);
                }
            }
        });
    }

    public void selectFiles(JComponent parent, EditableResources res) {
        File[] selection = ResourceEditorView.showOpenFileChooser(true, "Images", ".gif", ".png", ".jpg");
        if (selection == null || selection.length == 0) {
            return;
        }
        selectedFile = selection[0];
        int result = JOptionPane.showConfirmDialog(parent, this, "Select Resolutions", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if(result != JOptionPane.OK_OPTION) {
            return;
        }
        generate(selection, res);
    }
    
    public void selectFilesSimpleMode(JComponent parent, EditableResources res) {
        File[] selection = ResourceEditorView.showOpenFileChooser(true, "Images", ".gif", ".png", ".jpg");
        if (selection == null) {
            return;
        }
        JComboBox sourceResolution = new JComboBox(new String[] {"Very Low", "Low", "Medium", "High", "Very High", "HD", "560", "2HD", "4K"});
        sourceResolution.setSelectedIndex(4);
        int result = JOptionPane.showConfirmDialog(parent, sourceResolution, "Select Source Resolutions", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if(result != JOptionPane.OK_OPTION) {
            return;
        }
        generate(selection, res, sourceResolution.getSelectedIndex());
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        veryLowWidth = new javax.swing.JSpinner();
        veryLowHeight = new javax.swing.JSpinner();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        lowWidth = new javax.swing.JSpinner();
        lowHeight = new javax.swing.JSpinner();
        jLabel7 = new javax.swing.JLabel();
        mediumWidth = new javax.swing.JSpinner();
        mediumHeight = new javax.swing.JSpinner();
        jLabel8 = new javax.swing.JLabel();
        highWidth = new javax.swing.JSpinner();
        highHeight = new javax.swing.JSpinner();
        jLabel9 = new javax.swing.JLabel();
        veryHighWidth = new javax.swing.JSpinner();
        veryHighHeight = new javax.swing.JSpinner();
        jLabel10 = new javax.swing.JLabel();
        hdWidth = new javax.swing.JSpinner();
        hdHeight = new javax.swing.JSpinner();
        jLabel11 = new javax.swing.JLabel();
        percentWidth = new javax.swing.JSpinner();
        percentHeight = new javax.swing.JSpinner();
        squareImages = new javax.swing.JCheckBox();
        preserveAspectRatio = new javax.swing.JCheckBox();
        jLabel12 = new javax.swing.JLabel();
        hd560Width = new javax.swing.JSpinner();
        hd560Height = new javax.swing.JSpinner();
        jLabel13 = new javax.swing.JLabel();
        hd2Width = new javax.swing.JSpinner();
        hd2Height = new javax.swing.JSpinner();
        jLabel14 = new javax.swing.JLabel();
        hd4kWidth = new javax.swing.JSpinner();
        hd4kHeight = new javax.swing.JSpinner();

        FormListener formListener = new FormListener();

        setName("Form"); jLabel1.setText("Select The Sizes For Every DPI (select 0 to ignore)");
        jLabel1.setName("jLabel1"); jLabel2.setText("Very Low");
        jLabel2.setName("jLabel2"); veryLowWidth.setName("veryLowWidth"); veryLowWidth.addChangeListener(formListener);

        veryLowHeight.setName("veryLowHeight"); veryLowHeight.addChangeListener(formListener);

        jLabel3.setText("Size");
        jLabel3.setName("jLabel3"); jLabel4.setText("Width");
        jLabel4.setName("jLabel4"); jLabel5.setText("Height");
        jLabel5.setName("jLabel5"); jLabel6.setText("Low");
        jLabel6.setName("jLabel6"); lowWidth.setName("lowWidth"); lowWidth.addChangeListener(formListener);

        lowHeight.setName("lowHeight"); lowHeight.addChangeListener(formListener);

        jLabel7.setText("Medium");
        jLabel7.setName("jLabel7"); mediumWidth.setName("mediumWidth"); mediumWidth.addChangeListener(formListener);

        mediumHeight.setName("mediumHeight"); mediumHeight.addChangeListener(formListener);

        jLabel8.setText("High");
        jLabel8.setName("jLabel8"); highWidth.setName("highWidth"); highWidth.addChangeListener(formListener);

        highHeight.setName("highHeight"); highHeight.addChangeListener(formListener);

        jLabel9.setText("Very High");
        jLabel9.setName("jLabel9"); veryHighWidth.setName("veryHighWidth"); veryHighWidth.addChangeListener(formListener);

        veryHighHeight.setName("veryHighHeight"); veryHighHeight.addChangeListener(formListener);

        jLabel10.setText("HD");
        jLabel10.setName("jLabel10"); hdWidth.setName("hdWidth"); hdWidth.addChangeListener(formListener);

        hdHeight.setName("hdHeight"); hdHeight.addChangeListener(formListener);

        jLabel11.setText("% (will affect all entries)");
        jLabel11.setName("jLabel11"); percentWidth.setName("percentWidth"); percentWidth.addChangeListener(formListener);

        percentHeight.setName("percentHeight"); percentHeight.addChangeListener(formListener);

        squareImages.setText("Square Images");
        squareImages.setName("squareImages"); squareImages.addActionListener(formListener);

        preserveAspectRatio.setText("Preserve Aspect Ratio");
        preserveAspectRatio.setName("preserveAspectRatio"); preserveAspectRatio.addActionListener(formListener);

        jLabel12.setText("560");
        jLabel12.setName("jLabel12"); hd560Width.setName("hd560Width"); hd560Width.addChangeListener(formListener);

        hd560Height.setName("hd560Height"); hd560Height.addChangeListener(formListener);

        jLabel13.setText("2HD");
        jLabel13.setName("jLabel13"); hd2Width.setName("hd2Width"); hd2Width.addChangeListener(formListener);

        hd2Height.setName("hd2Height"); hd2Height.addChangeListener(formListener);

        jLabel14.setText("4K");
        jLabel14.setName("jLabel14"); hd4kWidth.setName("hd4kWidth"); hd4kWidth.addChangeListener(formListener);

        hd4kHeight.setName("hd4kHeight"); hd4kHeight.addChangeListener(formListener);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(squareImages)
                            .add(preserveAspectRatio)
                            .add(layout.createSequentialGroup()
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(jLabel11)
                                    .add(jLabel13)
                                    .add(jLabel14))
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(layout.createSequentialGroup()
                                        .add(percentWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 43, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(percentHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                    .add(layout.createSequentialGroup()
                                        .add(hd4kWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 44, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(hd4kHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 46, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                    .add(layout.createSequentialGroup()
                                        .add(hd2Width, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 44, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(hd2Height, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 46, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                    .add(layout.createSequentialGroup()
                                        .add(hd560Width, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 44, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(hd560Height, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 46, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                    .add(layout.createSequentialGroup()
                                        .add(hdWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 44, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(hdHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 46, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                    .add(layout.createSequentialGroup()
                                        .add(veryHighWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 44, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(veryHighHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 46, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                    .add(layout.createSequentialGroup()
                                        .add(highWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 44, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(highHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 46, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                    .add(layout.createSequentialGroup()
                                        .add(mediumWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 44, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(mediumHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 46, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                    .add(layout.createSequentialGroup()
                                        .add(lowWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 44, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(lowHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 46, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                    .add(layout.createSequentialGroup()
                                        .add(veryLowWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 44, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(veryLowHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 46, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                    .add(layout.createSequentialGroup()
                                        .add(jLabel4)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(jLabel5))))))
                    .add(layout.createSequentialGroup()
                        .add(10, 10, 10)
                        .add(jLabel1))
                    .add(layout.createSequentialGroup()
                        .add(6, 6, 6)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jLabel3)
                            .add(jLabel2)
                            .add(jLabel6)
                            .add(jLabel7)
                            .add(jLabel8)
                            .add(jLabel9)
                            .add(jLabel10)))
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(jLabel12)))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        layout.linkSize(new java.awt.Component[] {hd2Height, hd2Width, hd4kHeight, hd4kWidth, hd560Height, hd560Width, hdHeight, hdWidth, highHeight, highWidth, jLabel4, jLabel5, lowHeight, lowWidth, mediumHeight, mediumWidth, percentHeight, percentWidth, veryHighHeight, veryHighWidth, veryLowHeight, veryLowWidth}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(jLabel4)
                    .add(jLabel5))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel2)
                    .add(veryLowWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(veryLowHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel6)
                    .add(lowWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(lowHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel7)
                    .add(mediumWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(mediumHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel8)
                    .add(highWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(highHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel9)
                    .add(veryHighWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(veryHighHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel10)
                    .add(hdWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(hdHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel12)
                    .add(hd560Width, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(hd560Height, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel13)
                    .add(hd2Width, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(hd2Height, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel14)
                    .add(hd4kWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(hd4kHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel11)
                    .add(percentWidth, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(percentHeight, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(18, 18, 18)
                .add(squareImages)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(preserveAspectRatio)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }

    private class FormListener implements java.awt.event.ActionListener, javax.swing.event.ChangeListener {
        FormListener() {}
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            if (evt.getSource() == squareImages) {
                AddAndScaleMultiImage.this.squareImagesActionPerformed(evt);
            }
            else if (evt.getSource() == preserveAspectRatio) {
                AddAndScaleMultiImage.this.preserveAspectRatioActionPerformed(evt);
            }
        }

        public void stateChanged(javax.swing.event.ChangeEvent evt) {
            if (evt.getSource() == veryLowWidth) {
                AddAndScaleMultiImage.this.veryLowWidthStateChanged(evt);
            }
            else if (evt.getSource() == veryLowHeight) {
                AddAndScaleMultiImage.this.veryLowHeightStateChanged(evt);
            }
            else if (evt.getSource() == lowWidth) {
                AddAndScaleMultiImage.this.lowWidthStateChanged(evt);
            }
            else if (evt.getSource() == lowHeight) {
                AddAndScaleMultiImage.this.lowHeightStateChanged(evt);
            }
            else if (evt.getSource() == mediumWidth) {
                AddAndScaleMultiImage.this.mediumWidthStateChanged(evt);
            }
            else if (evt.getSource() == mediumHeight) {
                AddAndScaleMultiImage.this.mediumHeightStateChanged(evt);
            }
            else if (evt.getSource() == highWidth) {
                AddAndScaleMultiImage.this.highWidthStateChanged(evt);
            }
            else if (evt.getSource() == highHeight) {
                AddAndScaleMultiImage.this.highHeightStateChanged(evt);
            }
            else if (evt.getSource() == veryHighWidth) {
                AddAndScaleMultiImage.this.veryHighWidthStateChanged(evt);
            }
            else if (evt.getSource() == veryHighHeight) {
                AddAndScaleMultiImage.this.veryHighHeightStateChanged(evt);
            }
            else if (evt.getSource() == hdWidth) {
                AddAndScaleMultiImage.this.hdWidthStateChanged(evt);
            }
            else if (evt.getSource() == hdHeight) {
                AddAndScaleMultiImage.this.hdHeightStateChanged(evt);
            }
            else if (evt.getSource() == percentWidth) {
                AddAndScaleMultiImage.this.percentWidthStateChanged(evt);
            }
            else if (evt.getSource() == percentHeight) {
                AddAndScaleMultiImage.this.percentHeightStateChanged(evt);
            }
            else if (evt.getSource() == hd560Width) {
                AddAndScaleMultiImage.this.hd560WidthStateChanged(evt);
            }
            else if (evt.getSource() == hd560Height) {
                AddAndScaleMultiImage.this.hd560HeightStateChanged(evt);
            }
            else if (evt.getSource() == hd2Width) {
                AddAndScaleMultiImage.this.hd2WidthStateChanged(evt);
            }
            else if (evt.getSource() == hd2Height) {
                AddAndScaleMultiImage.this.hd2HeightStateChanged(evt);
            }
            else if (evt.getSource() == hd4kWidth) {
                AddAndScaleMultiImage.this.hd4kWidthStateChanged(evt);
            }
            else if (evt.getSource() == hd4kHeight) {
                AddAndScaleMultiImage.this.hd4kHeightStateChanged(evt);
            }
        }
    }private void squareImagesActionPerformed(java.awt.event.ActionEvent evt) {boolean b = !squareImages.isSelected();
        veryLowHeight.setEnabled(b);
        lowHeight.setEnabled(b);
        mediumHeight.setEnabled(b);
        highHeight.setEnabled(b);
        veryHighHeight.setEnabled(b);
        hdHeight.setEnabled(b);
        percentHeight.setEnabled(b);
        hd2Height.setEnabled(b);
        hd4kHeight.setEnabled(b);
        hd560Height.setEnabled(b);
    }private void percentWidthStateChanged(javax.swing.event.ChangeEvent evt) {float percentRatio = get(percentWidth) / 100.0f;
        veryLowWidth.setValue((int)(174.0f * percentRatio));
        lowWidth.setValue((int)(240.0f * percentRatio));
        mediumWidth.setValue((int)(320.0f * percentRatio));
        highWidth.setValue((int)(480.0f * percentRatio));
        veryHighWidth.setValue((int)(640.0f * percentRatio));
        hdWidth.setValue((int)(1024.0f * percentRatio));
        hd560Width.setValue((int)(1500.0f * percentRatio));
        hd2Width.setValue((int)(2000.0f * percentRatio));
        hd4kWidth.setValue((int)(2500.0f * percentRatio));
    }private void percentHeightStateChanged(javax.swing.event.ChangeEvent evt) {float percentRatio = get(percentHeight) / 100.0f;
        veryLowHeight.setValue((int)(220.0f * percentRatio));
        lowHeight.setValue((int)(320.0f * percentRatio));
        mediumHeight.setValue((int)(480.0f * percentRatio));
        highHeight.setValue((int)(854.0f * percentRatio));
        veryHighHeight.setValue((int)(1024.0f * percentRatio));
        hdHeight.setValue((int)(1920.0f * percentRatio));
        hd560Height.setValue((int)(1500.0f * percentRatio));
        hd2Height.setValue((int)(2000.0f * percentRatio));
        hd4kHeight.setValue((int)(2500.0f * percentRatio));
    }private void preserveAspectRatioActionPerformed(java.awt.event.ActionEvent evt) {if(preserveAspectRatio.isSelected()) {
            try {
                BufferedImage bi = ImageIO.read(selectedFile);
                aspect = ((float)bi.getWidth()) / ((float)bi.getHeight());
                
                veryLowHeight.setValue( (int)(((Integer)veryLowWidth.getValue()).intValue() * aspect) );
                lowHeight.setValue((int)(((Integer)lowWidth.getValue()).intValue() * aspect));
                mediumHeight.setValue((int)(((Integer)mediumWidth.getValue()).intValue() * aspect));
                highHeight.setValue((int)(((Integer)highWidth.getValue()).intValue() * aspect));
                hdHeight.setValue((int)(((Integer)hdWidth.getValue()).intValue() * aspect));
                veryHighHeight.setValue((int)(((Integer)veryHighWidth.getValue()).intValue() * aspect));
                
                hd560Height.setValue((int)(((Integer)hd560Width.getValue()).intValue() * aspect));
                hd2Height.setValue((int)(((Integer)hd2Width.getValue()).intValue() * aspect));
                hd4kHeight.setValue((int)(((Integer)hd4kWidth.getValue()).intValue() * aspect));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }private void veryLowWidthStateChanged(javax.swing.event.ChangeEvent evt) {updateHeight(veryLowWidth, veryLowHeight);
    }private void lowWidthStateChanged(javax.swing.event.ChangeEvent evt) {updateHeight(lowWidth, lowHeight);
    }private void mediumWidthStateChanged(javax.swing.event.ChangeEvent evt) {updateHeight(mediumWidth, mediumHeight);
    }private void highWidthStateChanged(javax.swing.event.ChangeEvent evt) {updateHeight(highWidth, highHeight);
    }private void veryHighWidthStateChanged(javax.swing.event.ChangeEvent evt) {updateHeight(veryHighWidth, veryHighHeight);
    }private void hdWidthStateChanged(javax.swing.event.ChangeEvent evt) {updateHeight(hdWidth, hdHeight);
    }private void veryLowHeightStateChanged(javax.swing.event.ChangeEvent evt) {updateWidth(veryLowHeight, veryLowWidth);
    }private void lowHeightStateChanged(javax.swing.event.ChangeEvent evt) {updateWidth(lowHeight, lowWidth);
    }private void mediumHeightStateChanged(javax.swing.event.ChangeEvent evt) {updateWidth(mediumHeight, mediumWidth);
    }private void highHeightStateChanged(javax.swing.event.ChangeEvent evt) {updateWidth(highHeight, highWidth);
    }private void veryHighHeightStateChanged(javax.swing.event.ChangeEvent evt) {updateWidth(veryHighHeight, veryHighWidth);
    }private void hdHeightStateChanged(javax.swing.event.ChangeEvent evt) {updateWidth(hdHeight, hdWidth);
    }private void hd560WidthStateChanged(javax.swing.event.ChangeEvent evt) {updateHeight(hd560Width, hd560Height);
    }private void hd560HeightStateChanged(javax.swing.event.ChangeEvent evt) {updateWidth(hd560Height, hd560Width);
    }private void hd2WidthStateChanged(javax.swing.event.ChangeEvent evt) {updateHeight(hd2Width, hd2Height);
    }private void hd2HeightStateChanged(javax.swing.event.ChangeEvent evt) {updateWidth(hd2Height, hd2Width);
    }private void hd4kWidthStateChanged(javax.swing.event.ChangeEvent evt) {updateHeight(hd4kWidth, hd4kHeight);
    }private void hd4kHeightStateChanged(javax.swing.event.ChangeEvent evt) {updateWidth(hd4kHeight, hd4kWidth);
    }private boolean lock;
    private void updateHeight(JSpinner source, JSpinner dest) {
        if(preserveAspectRatio.isSelected() && !lock) {
            lock = true;
            float width = get(source);
            float height = width / aspect;
            dest.setValue(new Integer((int)height));
            lock = false;
        }
    }

    private void updateWidth(JSpinner source, JSpinner dest) {
        if(preserveAspectRatio.isSelected() && !lock) {
            lock = true;
            float height = get(source);
            float width = height *  aspect;
            dest.setValue(new Integer((int)width));
            lock = false;
        }
    }
    
    private javax.swing.JSpinner hd2Height;
    private javax.swing.JSpinner hd2Width;
    private javax.swing.JSpinner hd4kHeight;
    private javax.swing.JSpinner hd4kWidth;
    private javax.swing.JSpinner hd560Height;
    private javax.swing.JSpinner hd560Width;
    private javax.swing.JSpinner hdHeight;
    private javax.swing.JSpinner hdWidth;
    private javax.swing.JSpinner highHeight;
    private javax.swing.JSpinner highWidth;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JSpinner lowHeight;
    private javax.swing.JSpinner lowWidth;
    private javax.swing.JSpinner mediumHeight;
    private javax.swing.JSpinner mediumWidth;
    private javax.swing.JSpinner percentHeight;
    private javax.swing.JSpinner percentWidth;
    private javax.swing.JCheckBox preserveAspectRatio;
    private javax.swing.JCheckBox squareImages;
    private javax.swing.JSpinner veryHighHeight;
    private javax.swing.JSpinner veryHighWidth;
    private javax.swing.JSpinner veryLowHeight;
    private javax.swing.JSpinner veryLowWidth;
    
