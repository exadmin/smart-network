package smartnetwork;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import smartnetwork.Topology.Item;
import smartnetwork.Topology.Link;
import smartnetwork.Topology.Type;

public final class NetworkEditor extends Application {
    static final DataFormat PALETTE_TYPE = new DataFormat("application/x-smart-network-type");
    private final Topology topology = new Topology();
    private final Label status = new Label();
    private final TextField endpoint = new TextField();
    private final Button send = new Button("Отправить агенту");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private Stage stage;
    private NetworkCanvas canvas;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Icons icons = new Icons();
        canvas = new NetworkCanvas(topology, icons, status::setText, this::properties);
        SplitPane split = new SplitPane(palette(icons), canvas);
        split.setDividerPositions(0.25);
        BorderPane window = new BorderPane(split);
        window.setTop(toolbar());
        status.getStyleClass().add("status");
        window.setBottom(status);
        canvas.showHint();

        Scene scene = new Scene(window, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/editor.css").toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (event.isConsumed() || event.getTarget() instanceof TextInputControl) return;
            if (event.getCode() == KeyCode.F2 && canvas.editSelectionName()) event.consume();
            if (event.getCode() == KeyCode.ESCAPE) canvas.cancelLink();
            if (event.getCode() == KeyCode.DELETE) canvas.deleteSelection();
        });
        stage.setTitle("Smart Network");
        stage.setMinWidth(720);
        stage.setMinHeight(470);
        stage.setScene(scene);
        stage.show();
    }

    private HBox toolbar() {
        Label title = new Label("Smart Network");
        title.getStyleClass().add("app-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        endpoint.setPromptText("https://agent.example/jobs");
        endpoint.setPrefColumnCount(24);
        endpoint.setText(System.getenv().getOrDefault("SMART_NETWORK_AGENT_URL", ""));
        send.getStyleClass().add("primary");
        send.setOnAction(event -> sendDesign());
        Button resetView = new Button("Сбросить вид");
        resetView.setOnAction(event -> canvas.resetView());
        HBox bar = new HBox(10, title, spacer, resetView, new Label("Адрес агента"), endpoint, send);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    private VBox palette(Icons icons) {
        Label title = new Label("Палитра");
        title.getStyleClass().add("panel-title");
        VBox contents = new VBox(15, category(icons));
        contents.setPadding(new Insets(0, 4, 8, 0));
        ScrollPane scroll = new ScrollPane(contents);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox sidebar = new VBox(14, title, scroll);
        sidebar.setMinWidth(142);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private VBox category(Icons icons) {
        TilePane tiles = new TilePane(8, 10);
        tiles.setPrefTileWidth(116);
        tiles.setPrefColumns(2);
        tiles.setTileAlignment(javafx.geometry.Pos.TOP_CENTER);
        for (Type type : Type.values()) {
            if (type.link) continue;
            StackPane square = new StackPane(icons.create(type));
            square.getStyleClass().add("icon-square");
            Label caption = new Label(type.name);
            caption.setWrapText(true);
            caption.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            VBox tile = new VBox(5, square, caption);
            tile.getStyleClass().add("palette-tile");
            tile.setAlignment(javafx.geometry.Pos.TOP_CENTER);
            tile.setPrefWidth(116);
            Tooltip.install(tile, new Tooltip(type.tooltip));
            tile.setOnDragDetected(event -> {
                var board = tile.startDragAndDrop(TransferMode.COPY);
                var content = new ClipboardContent();
                content.put(PALETTE_TYPE, type.name());
                board.setContent(content);
                board.setDragView(square.snapshot(null, null));
                event.consume();
            });
            tiles.getChildren().add(tile);
        }
        Label heading = new Label("Элементы");
        heading.getStyleClass().add("category");
        return new VBox(8, heading, tiles);
    }

    private void properties(Item item) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Свойства");
        dialog.setHeaderText(item.type.name + " · " + item.type.tooltip);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/editor.css").toExternalForm());
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        TextField name = new TextField(item.name);
        name.setPromptText(item.type.name);
        TextArea description = new TextArea(item.description);
        description.setWrapText(true);
        description.setPrefRowCount(5);
        Label iconPath = new Label(item.type.icon);
        ChoiceBox<Type> protocol = new ChoiceBox<>();
        VBox fields = new VBox(8, new Label("Имя на схеме"), name);
        if (item instanceof Link) {
            protocol.getItems().setAll(Arrays.stream(Type.values()).filter(type -> type.link).toList());
            protocol.setConverter(new StringConverter<>() {
                @Override public String toString(Type type) { return type == null ? "" : type.name; }
                @Override public Type fromString(String value) { return null; }
            });
            protocol.setValue(item.type);
            protocol.valueProperty().addListener((obs, previous, type) -> {
                name.setPromptText(type.name);
                iconPath.setText(type.icon);
            });
            Label protocolLabel = new Label("Тип связи");
            protocolLabel.setLabelFor(protocol);
            fields.getChildren().addAll(protocolLabel, protocol);
        }
        fields.getChildren().addAll(new Label("Описание для AI"), description, iconPath);
        dialog.getDialogPane().setContent(fields);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == save) {
            if (item instanceof Link) item.type = protocol.getValue();
            item.name = name.getText().strip();
            item.description = description.getText();
            canvas.redraw();
        }
    }

    private void sendDesign() {
        if (topology.roots.isEmpty()) {
            status.setText("Добавьте хотя бы один элемент перед отправкой.");
            return;
        }
        HttpRequest request;
        try {
            URI uri = URI.create(endpoint.getText().strip());
            request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(topology.toJson()))
                .build();
        } catch (IllegalArgumentException failure) {
            status.setText("Укажите действительный HTTP- или HTTPS-адрес агента.");
            return;
        }
        send.setDisable(true);
        status.setText("Отправка проекта…");
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) ->
            Platform.runLater(() -> {
                send.setDisable(false);
                if (error != null) {
                    status.setText("Ошибка отправки. Проверьте доступность адреса агента.");
                } else if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    status.setText("Агент принял проект (HTTP " + response.statusCode() + ").");
                } else {
                    status.setText("Агент вернул HTTP " + response.statusCode() + ".");
                }
            }));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
