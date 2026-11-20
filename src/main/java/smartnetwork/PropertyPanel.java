package smartnetwork;

import java.util.Arrays;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import smartnetwork.Topology.Device;
import smartnetwork.Topology.Item;
import smartnetwork.Topology.Link;
import smartnetwork.Topology.SensitiveProperty;
import smartnetwork.Topology.Type;

final class PropertyPanel extends VBox {
    private final ObservableBooleanValue hideSensitive;
    private final Runnable redraw;
    private Item item;
    private TextField name;

    PropertyPanel(ObservableBooleanValue hideSensitive, Runnable redraw) {
        this.hideSensitive = hideSensitive;
        this.redraw = redraw;
        getStyleClass().addAll("sidebar", "property-panel");
        setSpacing(10);
        hideSensitive.addListener((obs, previous, hidden) -> showItem(item));
        showItem(null);
    }

    void showItem(Item selected) {
        if (name != null) name.textProperty().unbindBidirectional(item.name);
        name = null;
        item = selected;
        Label title = new Label("Свойства");
        title.getStyleClass().add("panel-title");
        getChildren().setAll(title);
        if (selected == null) {
            Label empty = new Label("Выберите элемент на канве.");
            empty.setWrapText(true);
            getChildren().add(empty);
            return;
        }

        name = new TextField();
        name.setPromptText(selected.type.name);
        name.textProperty().bindBidirectional(selected.name);
        name.textProperty().addListener((obs, old, text) -> redraw.run());
        addField("Имя на схеме", name);

        TextField tooltip = readOnly(selected.type.tooltip);
        TextField icon = readOnly(selected.type.icon);
        if (selected instanceof Link link) {
            ComboBox<Type> protocol = protocolPicker(link.type);
            protocol.valueProperty().addListener((obs, previous, type) -> {
                if (type == null) return;
                link.type = type;
                name.setPromptText(type.name);
                tooltip.setText(type.tooltip);
                icon.setText(type.icon);
                redraw.run();
            });
            addField("Тип связи", protocol);
        } else {
            addField("Тип элемента", readOnly(selected.type.name));
        }

        TextArea description = new TextArea(selected.description);
        description.setWrapText(true);
        description.setPrefRowCount(3);
        description.textProperty().addListener((obs, old, text) -> selected.description = text);
        addField("Описание для AI", description);
        addField("Подсказка", tooltip);
        addField("Путь к иконке", icon);
        if (selected instanceof Device device) {
            for (SensitiveProperty property : SensitiveProperty.values()) {
                TextField field;
                if (hideSensitive.get()) {
                    field = readOnly("********");
                } else {
                    field = new TextField(device.sensitiveValues.getOrDefault(property, ""));
                    field.textProperty().addListener((obs, old, text) -> device.sensitiveValues.put(property, text));
                }
                addField(property.label, field);
            }
        }
    }

    void focusName() {
        if (name != null) name.requestFocus();
    }

    private void addField(String title, Node control) {
        Label label = new Label(title);
        label.setLabelFor(control);
        getChildren().add(new VBox(4, label, control));
    }

    private TextField readOnly(String value) {
        TextField field = new TextField(value);
        field.setEditable(false);
        return field;
    }

    private ComboBox<Type> protocolPicker(Type type) {
        ComboBox<Type> protocol = new ComboBox<>();
        protocol.setMaxWidth(Double.MAX_VALUE);
        protocol.getItems().setAll(Arrays.stream(Type.values()).filter(value -> value.link).toList());
        protocol.setConverter(new StringConverter<>() {
            @Override public String toString(Type value) { return value == null ? "" : value.name; }
            @Override public Type fromString(String text) { return null; }
        });
        protocol.setValue(type);
        return protocol;
    }
}
