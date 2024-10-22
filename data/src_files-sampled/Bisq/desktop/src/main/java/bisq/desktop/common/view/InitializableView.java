package bisq.desktop.common.view;

import javafx.fxml.Initializable;

import javafx.scene.Node;

import java.net.URL;

import java.util.ResourceBundle;

public abstract class InitializableView<R extends Node, M> extends AbstractView<R, M> implements Initializable {

    public InitializableView(M model) {
        super(model);
    }

    public InitializableView() {
        this(null);
    }

    @Override
    public final void initialize(URL location, ResourceBundle resources) {
        prepareInitialize();
        initialize();
    }

    protected void prepareInitialize() {
    }

    protected void initialize() {
    }
}
