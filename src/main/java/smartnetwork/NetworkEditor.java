package smartnetwork;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import smartnetwork.Topology.Type;

public final class NetworkEditor extends Application {
    static final DataFormat PALETTE_TYPE = new DataFormat("application/x-smart-network-type");
    private final Topology topology = new Topology();
    private final Label status = new Label();
    private final TextField endpoint = new TextField();
    private final Button send = new Button("Отправить агенту");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ToggleButton showProperties = new ToggleButton();
    private final ToggleButton hideSensitive = new ToggleButton();
    private NetworkCanvas canvas;
    private PropertyPanel properties;

    @Override
    public void start(Stage stage) {
        Icons icons = new Icons();
        showProperties.setSelected(true);
        hideSensitive.setSelected(true);
        properties = new PropertyPanel(hideSensitive.selectedProperty(), () -> canvas.redraw());
        canvas = new NetworkCanvas(topology, icons, status::setText, item -> {
            showProperties.setSelected(true);
            properties.focusName();
        }, properties::showItem);
        SplitPane split = new SplitPane(sidebar(icons), canvas);
        split.setDividerPositions(0.25);
        BorderPane window = new BorderPane(split);
        window.setTop(toolbar(icons, new ProjectActions(stage, topology, canvas, status::setText)));
        status.getStyleClass().add("status");
        window.setBottom(status);
        canvas.showHint();

        Scene scene = new Scene(window, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/editor.css").toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (event.isConsumed() || event.getTarget() instanceof TextInputControl) return;
            if (event.getCode() == KeyCode.F2 && canvas.editSelectionName()) event.consume();
            if (event.getCode() == KeyCode.ESCAPE) canvas.cancelLink();
            if (event.getCode() == KeyCode.DELETE && event.getTarget() == canvas) canvas.deleteSelection();
        });
        stage.setTitle("Smart Network");
        stage.setMinWidth(900);
        stage.setMinHeight(470);
        stage.setScene(scene);
        stage.show();
    }

    private HBox toolbar(Icons icons, ProjectActions projects) {
        Label title = new Label("Smart Network");
        title.getStyleClass().add("app-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        endpoint.setPromptText("https://agent.example/jobs");
        endpoint.setPrefColumnCount(16);
        endpoint.setMinWidth(72);
        endpoint.setText(System.getenv().getOrDefault("SMART_NETWORK_AGENT_URL", ""));
        send.getStyleClass().add("primary");
        send.setOnAction(event -> sendDesign());
        Button resetView = new Button("Сбросить вид");
        resetView.setOnAction(event -> canvas.resetView());
        configureTool(showProperties, icons, "properties", "Свойства", "Показать или скрыть свойства выбранного элемента");
        configureTool(hideSensitive, icons, "hide-sensitive", "Скрыть секреты", "Показать или скрыть IP-адреса, логины и пароли");
        Button sendPreview = new Button();
        configureTool(sendPreview, icons, "send-ai", "Отправить в AI", "Отправить в AI (недоступно)");
        sendPreview.setDisable(true);
        sendPreview.setMouseTransparent(true);
        StackPane unavailableTool = new StackPane(sendPreview);
        unavailableTool.setPickOnBounds(true);
        Tooltip.install(unavailableTool, sendPreview.getTooltip());
        Button newProject = projectButton(icons, "new-project", "New Project", "Создать новый проект", projects::newProject);
        Button saveAs = projectButton(icons, "save-as", "Save As", "Сохранить проект в файл", projects::saveAs);
        Button loadFrom = projectButton(icons, "load-from", "Load From", "Загрузить проект из файла", projects::loadFrom);
        Separator groupDivider = toolDivider();
        groupDivider.getStyleClass().add("tool-group-divider");
        ToolBar tools = new ToolBar(newProject, toolDivider(), saveAs, toolDivider(), loadFrom,
            groupDivider, showProperties, toolDivider(), hideSensitive, toolDivider(), unavailableTool);
        tools.getStyleClass().add("editor-tools");
        tools.setMinWidth(Region.USE_PREF_SIZE);
        HBox bar = new HBox(10, title, tools, spacer, resetView, new Label("Адрес агента"), endpoint, send);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    private Button projectButton(Icons icons, String icon, String name, String hint, Runnable action) {
        Button button = new Button();
        configureTool(button, icons, icon, name, hint);
        button.setOnAction(event -> action.run());
        return button;
    }

    private Separator toolDivider() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().add("tool-divider");
        return separator;
    }

    private void configureTool(ButtonBase button, Icons icons, String icon, String name, String hint) {
        button.setGraphic(icons.toolbarIcon(icon));
        button.setAccessibleText(name);
        button.setTooltip(new Tooltip(hint));
        button.getStyleClass().add("editor-tool");
    }

    private SplitPane sidebar(Icons icons) {
        ScrollPane details = new ScrollPane(properties);
        details.setFitToWidth(true);
        details.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        details.setMinHeight(92);
        details.getStyleClass().add("properties-scroll");
        SplitPane left = new SplitPane(palette(icons), details);
        left.setOrientation(Orientation.VERTICAL);
        left.setMinWidth(142);
        left.setDividerPositions(0.55);
        showProperties.selectedProperty().addListener((obs, wasVisible, visible) -> {
            if (visible) {
                left.getItems().add(details);
                left.setDividerPositions(0.55);
            } else {
                left.getItems().remove(details);
            }
        });
        return left;
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
