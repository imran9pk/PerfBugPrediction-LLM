package com.codename1.ui.html;

import com.codename1.ui.Component;
import com.codename1.ui.Display;
import com.codename1.ui.Image;
import com.codename1.ui.Label;
import com.codename1.ui.geom.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

class ResourceThreadQueue {

    private static int DEFAULT_MAX_THREADS = 2;

    HTMLComponent htmlC;
    Vector queue = new Vector();
    Vector running = new Vector();
    Vector bgImageCompsUnselected = new Vector();
    Vector bgImageCompsSelected = new Vector();
    Vector bgImageCompsPressed = new Vector();

    Hashtable images = new Hashtable();
    static int maxThreads = DEFAULT_MAX_THREADS;
    int threadCount;
    private int cssCount=-1; boolean started;

    ResourceThreadQueue(HTMLComponent htmlC) {
        this.htmlC=htmlC;
    }

    static void setMaxThreads(int threadsNum) {
        maxThreads=threadsNum;
    }

    synchronized void add(Component imgLabel,String imageUrl) {
        if (started) {
            throw new IllegalStateException("ResourceThreadQueue already started! stop/cancel first");
        }
        images.put(imgLabel, imageUrl); }

    synchronized void addBgImage(Component imgComp,String imageUrl,int styles) {
        if (HTMLComponent.SUPPORT_CSS) {
            add(imgComp,imageUrl);
                if ((styles & CSSEngine.STYLE_SELECTED)!=0) {
                    bgImageCompsSelected.addElement(imgComp);
                }
                if ((styles & CSSEngine.STYLE_UNSELECTED)!=0) {
                    bgImageCompsUnselected.addElement(imgComp);
                }
                if ((styles & CSSEngine.STYLE_PRESSED)!=0) {
                    bgImageCompsPressed.addElement(imgComp);
                }
            }
    }

    synchronized void addCSS(String cssUrl,String encoding) {
        if (started) {
            throw new IllegalStateException("ResourceThreadQueue alreadey started! stop/cancel first");
        }
        DocumentInfo cssDocInfo=new DocumentInfo(cssUrl, DocumentInfo.TYPE_CSS);
        if (encoding!=null) {
            cssDocInfo.setEncoding(encoding);
        }
        ResourceThread t =  new ResourceThread(cssDocInfo, htmlC, this);
        queue.addElement(t);
        incCSSCount();
    }

    private void incCSSCount() {
        if (cssCount==-1) { cssCount++;
        }
        cssCount++;
    }

    synchronized int getCSSCount() {
        return cssCount;
    }

    int getQueueSize() {
        return (images.size()+queue.size()); }

    synchronized void startRunning() {
        if (!startDequeue()) {
            startRunningImages();
        }
    }

    synchronized void startRunningImages() {
        queue.removeAllElements();
        Vector urls=new Vector();
        for(Enumeration e=images.keys();e.hasMoreElements();) {
            Component imgComp = (Component)e.nextElement();
            String imageUrl = (String)images.get(imgComp);
            int urlIndex=urls.indexOf(imageUrl);

            if (urlIndex!=-1) {
                ResourceThread t=(ResourceThread)queue.elementAt(urlIndex);
                t.addLabel(imgComp);
            } else {
                ResourceThread t =  new ResourceThread(imageUrl, imgComp, htmlC, this);
                queue.addElement(t);
                urls.addElement(imageUrl);
            }
        }
        urls=null;
        
        images=new Hashtable();

        if (!startDequeue()) {
            htmlC.setPageStatus(HTMLCallback.STATUS_COMPLETED);
        }

    }

    private synchronized boolean startDequeue() {
        int threads=Math.min(queue.size(), maxThreads);

        for(int i=0;i<threads;i++) {
            ResourceThread t=(ResourceThread)queue.firstElement();
            queue.removeElementAt(0);
            running.addElement(t);
            threadCount++;
            }
        for(Enumeration e=running.elements();e.hasMoreElements();) {
            ResourceThread t=(ResourceThread)e.nextElement();
            t.go();
        }
        
        
        
        return (threads>0);
    }

    synchronized void threadFinished(ResourceThread finishedThread,boolean success) {
        
        if(finishedThread.cssDocInfo!=null) {
            cssCount--; }

        if ((HTMLComponent.SUPPORT_CSS) && (cssCount==0)) {
            cssCount=-1; htmlC.applyAllCSS();
            htmlC.cssCompleted();
        }
        running.removeElement(finishedThread);


        if (queue.size()>0) {
            ResourceThread t=(ResourceThread)queue.firstElement();
            queue.removeElementAt(0);
            running.addElement(t);
            t.go();          } else {
            threadCount--;
        }

        if (threadCount==0) {
            if (images.size()==0) {
                htmlC.setPageStatus(HTMLCallback.STATUS_COMPLETED);
            } else {
                startRunningImages();
            }
        }
    }

    synchronized void discardQueue() {
        queue.removeAllElements();

        for(Enumeration e=running.elements();e.hasMoreElements();) {
            ResourceThread t = (ResourceThread)e.nextElement();
            t.cancel();
        }
        running.removeAllElements();
        bgImageCompsSelected.removeAllElements();
        bgImageCompsUnselected.removeAllElements();
        bgImageCompsPressed.removeAllElements();

        threadCount=0;
        cssCount=-1;
        started=false;

    }

    public String toString() {
        String str=("---- Running ----\n");
        int i=1;
        for(Enumeration e=running.elements();e.hasMoreElements();) {
            ResourceThread t = (ResourceThread)e.nextElement();
            if (t.imageUrl!=null) {
                str+="#"+i+": "+t.imageUrl+"\n";
            } else {
                str+="#"+i+": CSS - "+t.cssDocInfo.getUrl()+"\n";
            }
            i++;
        }
        i=1;
        str+="Queue:\n";
        for(Enumeration e=queue.elements();e.hasMoreElements();) {
            ResourceThread t = (ResourceThread)e.nextElement();
            if (t.imageUrl!=null) {
                str+="#"+i+": "+t.imageUrl+"\n";
            } else {
                str+="#"+i+": CSS - "+t.cssDocInfo.getUrl()+"\n";
            }
            i++;
        }
        str+="---- count:"+threadCount+" ----\n";
        return str;
    }

    class ResourceThread implements Runnable, IOCallback {

        Component imgLabel;
        Vector labels;
        String imageUrl;
        DocumentRequestHandler handler;
        ResourceThreadQueue threadQueue;
        boolean cancelled;
        HTMLComponent htmlC;
        Image img;
        DocumentInfo cssDocInfo;

        ResourceThread(String imageUrl, Component imgLabel,HTMLComponent htmlC,ResourceThreadQueue threadQueue) {
            this.imageUrl=imageUrl;
            this.imgLabel=imgLabel;
            this.handler=htmlC.getRequestHandler();
            this.threadQueue=threadQueue;
            this.htmlC=htmlC;
        }

        ResourceThread(DocumentInfo cssDocInfo,HTMLComponent htmlC,ResourceThreadQueue threadQueue) {
            this.cssDocInfo=cssDocInfo;
            this.handler=htmlC.getRequestHandler();
            this.threadQueue=threadQueue;
            this.htmlC=htmlC;
        }

        void cancel() {
            cancelled=true;
        }

        void addLabel(Component label) {
            if (labels==null) {
                labels=new Vector();
            }
            labels.addElement(label);
        }

        void go() {
                if (handler instanceof AsyncDocumentRequestHandler) {
                DocumentInfo docInfo=cssDocInfo!=null?cssDocInfo:new DocumentInfo(imageUrl,DocumentInfo.TYPE_IMAGE);
                    ((AsyncDocumentRequestHandler)handler).resourceRequestedAsync(docInfo, this);
                } else {
                    Display.getInstance().startThread(this, "HTML Resources").start();
                }
        }

        public void run() {
                DocumentInfo docInfo=cssDocInfo!=null?cssDocInfo:new DocumentInfo(imageUrl,DocumentInfo.TYPE_IMAGE);
                InputStream is = handler.resourceRequested(docInfo);
                streamReady(is, docInfo);
        }

        public void streamReady(InputStream is,DocumentInfo docInfo) {
            try {
                if (is==null) {
                    if (htmlC.getHTMLCallback()!=null) {
                        htmlC.getHTMLCallback().parsingError(cssDocInfo!=null?HTMLCallback.ERROR_CSS_NOT_FOUND:HTMLCallback.ERROR_IMAGE_NOT_FOUND, null, null, null, (cssDocInfo!=null?"CSS":"Image")+" not found at "+(cssDocInfo!=null?cssDocInfo.getUrl():imageUrl));
                    }
                } else {
                    if(cssDocInfo!=null) { if (HTMLComponent.SUPPORT_CSS) { CSSElement result = CSSParser.getInstance().parseCSSSegment(new InputStreamReader(is),is,htmlC,cssDocInfo.getUrl());
                            result.setAttribute(result.getAttributeName(new Integer(CSSElement.CSS_PAGEURL)), cssDocInfo.getUrl());
                            htmlC.addToExternalCSS(result);
                        }
                        threadQueue.threadFinished(this,true);
                        return;
                    } else {
                        img=Image.createImage(is);
                        if (img==null) {
                            if (htmlC.getHTMLCallback()!=null) {
                                htmlC.getHTMLCallback().parsingError(HTMLCallback.ERROR_IMAGE_BAD_FORMAT, null, null, null, "Image could not be created from "+imageUrl);
                            }
                        }
                    }
                }

                if (img==null) {
                    threadQueue.threadFinished(this,false);
                    return;
                }
                if (!cancelled) {
                    Display.getInstance().callSerially(new Runnable() {
                        public void run() {
                            handleImage(img,imgLabel);
                            if (labels!=null) {
                                for(Enumeration e=labels.elements();e.hasMoreElements();) {
                                    Component cmp=(Component)e.nextElement();
                                    handleImage(img,cmp);
                                }
                            }
                        }
                    });
                    threadQueue.threadFinished(this,true);
                }
            } catch (IOException ioe) {
                if (htmlC.getHTMLCallback()!=null) {
                    htmlC.getHTMLCallback().parsingError(HTMLCallback.ERROR_IMAGE_BAD_FORMAT, null, null, null, "Image could not be created from "+imageUrl+": "+ioe.getMessage());
                }
                if(!cancelled) {
                    threadQueue.threadFinished(this,false);
                }
            }

        }

        private void handleImage(Image img,Component cmp) {
            boolean bgUnselected=(threadQueue.bgImageCompsUnselected.contains(cmp));;
            boolean bgSelected=(threadQueue.bgImageCompsSelected.contains(cmp));
            boolean bgPressed=(threadQueue.bgImageCompsPressed.contains(cmp));
            handleImage(img, cmp,bgUnselected,bgSelected,bgPressed);
        }

        void handleImage(Image img,Component cmp,boolean bgUnselected,boolean bgSelected,boolean bgPressed) {
            boolean bg=false;
            if (bgUnselected) {
                cmp.getUnselectedStyle().setBgImage(img);
                bg=true;
            }
            if (bgSelected) {
                cmp.getSelectedStyle().setBgImage(img);
                bg=true;
            }
            if (bgPressed) {
                if (cmp instanceof HTMLLink) {
                    ((HTMLLink)cmp).getPressedStyle().setBgImage(img);
                }
                bg=true;
            }
            if (bg) {
                cmp.repaint();
                return;
            }

            Label label = (Label)cmp;
            label.setText(""); int width=label.getPreferredW()-label.getStyle().getPadding(Component.LEFT)-label.getStyle().getPadding(Component.RIGHT);
            int height=label.getPreferredH()-label.getStyle().getPadding(Component.TOP)-label.getStyle().getPadding(Component.BOTTOM);

            if (width!=0) {
                if (height==0) { height=img.getHeight()*width/img.getWidth();
                }
            } else if (height!=0) {
                if (width==0) { width=img.getWidth()*height/img.getHeight();
                }
            }

            if (width!=0) { img=img.scaled(width, height);
                width+=label.getStyle().getPadding(Component.LEFT)+label.getStyle().getPadding(Component.RIGHT);
                height+=label.getStyle().getPadding(Component.TOP)+label.getStyle().getPadding(Component.BOTTOM);
                label.setPreferredSize(new Dimension(width,height));
            }

            label.setIcon(img);

            htmlC.revalidate();
            if (label.getClientProperty(HTMLComponent.CLIENT_PROPERTY_IMG_BORDER)==null) { label.getUnselectedStyle().setBorder(null); } else {
                int borderSize=((Integer)label.getClientProperty(HTMLComponent.CLIENT_PROPERTY_IMG_BORDER)).intValue();
                label.getUnselectedStyle().setPadding(borderSize,borderSize,borderSize,borderSize);
                label.getSelectedStyle().setPadding(borderSize,borderSize,borderSize,borderSize);
            }

        }

    }
    
}
