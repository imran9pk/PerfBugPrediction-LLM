package com.codename1.samples;


import com.codename1.components.SplitPane;
import static com.codename1.ui.CN.*;
import com.codename1.ui.Display;
import com.codename1.ui.Form;
import com.codename1.ui.Dialog;
import com.codename1.ui.Label;
import com.codename1.ui.plaf.UIManager;
import com.codename1.ui.util.Resources;
import com.codename1.io.Log;
import com.codename1.ui.Toolbar;
import java.io.IOException;
import com.codename1.ui.layouts.BoxLayout;
import com.codename1.io.NetworkEvent;
import com.codename1.ui.BrowserComponent;
import com.codename1.ui.Button;
import com.codename1.ui.CN;
import com.codename1.ui.TextArea;
import com.codename1.ui.layouts.BorderLayout;

public class SharedJavascriptContextSample {

    private Form current;
    private Resources theme;

    public void init(Object context) {
        updateNetworkThreadCount(2);

        theme = UIManager.initFirstTheme("/theme");

        Toolbar.setGlobalToolbar(true);

        Log.bindCrashProtection(true);

        addNetworkErrorListener(err -> {
            err.consume();
            if(err.getError() != null) {
                Log.e(err.getError());
            }
            Log.sendLogAsync();
            Dialog.show("Connection Error", "There was a networking error in the connection to " + err.getConnectionRequest().getUrl(), "OK", null);
        });        
    }
    
    public void start() {
        if(current != null){
            current.show();
            return;
        }
        Form hi = new Form("Hi World", new BorderLayout());
        TextArea input = new TextArea();
        TextArea output = new TextArea();
        output.setEditable(false);
        
        Button execute = new Button("Run");
        execute.addActionListener(evt->{
            BrowserComponent bc = CN.getSharedJavascriptContext().ready().get();
            bc.execute("callback.onSuccess(window.eval(${0}))", new Object[]{input.getText()}, res->{
                output.setText(res.toString());
            });
        });
        SplitPane split = new SplitPane(SplitPane.VERTICAL_SPLIT, input, output, "0", "50%", "99%");
        hi.add(CENTER, split);
        hi.add(NORTH, execute);
        
        hi.show();
    }

    public void stop() {
        current = getCurrentForm();
        if(current instanceof Dialog) {
            ((Dialog)current).dispose();
            current = getCurrentForm();
        }
    }
    
    public void destroy() {
    }

}
