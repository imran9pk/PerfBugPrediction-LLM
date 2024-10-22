package com.codename1.ui.resource.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ImageTools {

    static List<Integer> buildPalette(int[] array) {
        List<Integer> colors = new ArrayList<Integer>();
        for(int val : array) {
            Integer i = new Integer(val);
            if(!colors.contains(i)) {
                colors.add(i);
            }
        }
        return colors;
    }
    
    private static void replace(int[] array, int oldColor, int newColor) {
        for(int iter = 0 ; iter < array.length ; iter++) {
            if(array[iter] == oldColor) {
                array[iter] = newColor;
            }
        }
    }

    public static BufferedImage getScaledInstance(BufferedImage img,
                                           int targetWidth,
                                           int targetHeight)
    {
        BufferedImage ret = (BufferedImage)img;
        int w, h;
        w = img.getWidth();
        h = img.getHeight();
        
        do {
            if (w > targetWidth) {
                w /= 2;
                if (w < targetWidth) {
                    w = targetWidth;
                }
            } else {
                w = targetWidth;
            }

            if (h > targetHeight) {
                h /= 2;
                if (h < targetHeight) {
                    h = targetHeight;
                }
            } else {
                h = targetHeight;
            }


            BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(ret, 0, 0, w, h, null);
            g2.dispose();

            ret = tmp;
        } while (w != targetWidth || h != targetHeight);

        return ret;
    }
}
