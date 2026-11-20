package smartnetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Affine;
import smartnetwork.Topology.Device;
import smartnetwork.Topology.Item;
import smartnetwork.Topology.Link;
import smartnetwork.Topology.Type;

final class NetworkCanvas extends Pane {
    private static final double HANDLE_RADIUS = 6;

    private enum End { SOURCE, TARGET }

    private record Edge(Link link, Point2D start, Point2D control, Point2D end, HBox badge) {
        Point2D at(double t) {
            double u = 1 - t;
            return start.multiply(u * u).add(control.multiply(2 * u * t)).add(end.multiply(t * t));
        }
    }

    private record NodeBadge(Device device, HBox badge) {}

    private final Topology topology;
    private final Icons icons;
    private final Consumer<String> status;
    private final Consumer<Item> properties;
    private final Canvas grid = new Canvas();
    private final Group world = new Group();
    private final Affine transform = new Affine();
    private final List<Edge> edges = new ArrayList<>();
    private final List<NodeBadge> nodeBadges = new ArrayList<>();
    private final ContextMenu menu = new ContextMenu();
    private double zoom = 1;
    private Point2D pan = Point2D.ZERO;
    private Point2D pressed;
    private MouseButton pressedButton;
    private Point2D panAtPress;
    private Point2D dragDelta = Point2D.ZERO;
    private boolean moved;
    private Device dragging;
    private Device dropParent;
    private Device linkStart;
    private Type linkType;
    private Item selection;
    private End draggedEnd;
    private Point2D endpointPreview;
    private TextField nameEditor;
    private Point2D nameAnchor;

    NetworkCanvas(Topology topology, Icons icons, Consumer<String> status, Consumer<Item> properties) {
        this.topology = topology;
        this.icons = icons;
        this.status = status;
        this.properties = properties;
        getStyleClass().add("network-canvas");
        setMinSize(200, 100);
        setFocusTraversable(true);
        setPickOnBounds(true);
        grid.setMouseTransparent(true);
        grid.setManaged(false);
        world.setMouseTransparent(true);
        world.setManaged(false);
        world.getTransforms().add(transform);
        getChildren().addAll(grid, world);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        widthProperty().addListener((obs, old, width) -> updateView());
        heightProperty().addListener((obs, old, height) -> updateView());
        installInput();
    }

    private void installInput() {
        setOnDragOver(event -> {
            if (!event.getDragboard().hasContent(NetworkEditor.PALETTE_TYPE)) return;
            event.acceptTransferModes(TransferMode.COPY);
            Type type = Type.valueOf((String) event.getDragboard().getContent(NetworkEditor.PALETTE_TYPE));
            setDropParent(type.link ? null : deviceAt(toWorld(event.getX(), event.getY()), null));
            event.consume();
        });
        setOnDragExited(event -> setDropParent(null));
        setOnDragDropped(event -> {
            if (!event.getDragboard().hasContent(NetworkEditor.PALETTE_TYPE)) return;
            requestFocus();
            Type type = Type.valueOf((String) event.getDragboard().getContent(NetworkEditor.PALETTE_TYPE));
            Point2D position = toWorld(event.getX(), event.getY());
            Device target = deviceAt(position, null);
            dropParent = null;
            linkStart = null;
            linkType = null;
            if (type.link) {
                linkType = type;
                linkStart = target;
                selection = target;
            } else {
                var device = new Device(type, position);
                topology.place(device, target, position);
                selection = device;
            }
            redraw();
            showHint();
            event.setDropCompleted(true);
            event.consume();
        });
        setOnMousePressed(event -> {
            if (nameEditor != null && event.getTarget() != this) return;
            if (pressed != null) return;
            menu.hide();
            requestFocus();
            pressed = new Point2D(event.getX(), event.getY());
            pressedButton = event.getButton();
            panAtPress = pan;
            moved = false;
            if (event.getButton() != MouseButton.PRIMARY || linkType != null) return;
            Point2D position = toWorld(event.getX(), event.getY());
            draggedEnd = handleAt(position);
            if (draggedEnd != null) return;
            selection = itemAt(position);
            if (selection instanceof Device device) dragging = device;
            redraw();
        });
        setOnMouseDragged(event -> {
            if (pressed == null) return;
            Point2D delta = new Point2D(event.getX(), event.getY()).subtract(pressed);
            moved |= delta.magnitude() >= 4;
            if (!moved) return;
            if (pressedButton == MouseButton.SECONDARY) {
                pan = panAtPress.add(delta);
                updateView();
            } else if (draggedEnd != null) {
                endpointPreview = toWorld(event.getX(), event.getY());
                dropParent = endpointTarget((Link) selection, endpointPreview);
                redraw();
            } else if (dragging != null) {
                dragDelta = delta.multiply(1 / zoom);
                dropParent = deviceAt(toWorld(event.getX(), event.getY()), dragging);
                redraw();
            }
        });
        setOnMouseReleased(event -> {
            if (pressed == null || event.getButton() != pressedButton) return;
            Point2D position = toWorld(event.getX(), event.getY());
            if (event.getButton() == MouseButton.SECONDARY && !moved) {
                showMenu(itemAt(position), event.getScreenX(), event.getScreenY());
            }
            if (event.getButton() == MouseButton.PRIMARY) {
                if (draggedEnd != null && moved) {
                    Link link = (Link) selection;
                    Device target = endpointTarget(link, position);
                    if (target != null) {
                        if (draggedEnd == End.SOURCE) link.source = target;
                        else link.target = target;
                    }
                } else if (linkType != null && !moved) {
                    chooseEndpoint(deviceAt(position, null));
                } else if (dragging != null && moved) {
                    Point2D center = dragging.center().add(dragDelta);
                    topology.place(dragging, deviceAt(position, dragging), center);
                }
                draggedEnd = null;
                endpointPreview = null;
                dragging = null;
                dropParent = null;
                dragDelta = Point2D.ZERO;
                redraw();
            }
            pressed = null;
            pressedButton = null;
        });
        setOnMouseMoved(event -> setCursor(event.getTarget() == this && linkType == null
            && handleAt(toWorld(event.getX(), event.getY())) != null ? Cursor.HAND : Cursor.DEFAULT));
        setOnMouseClicked(event -> {
            if (event.getTarget() == this && event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 2 && !moved && linkType == null
                && selection == itemAt(toWorld(event.getX(), event.getY())) && editSelectionName()) {
                event.consume();
            }
        });
        setOnScroll(event -> {
            if (event.getDeltaY() == 0 || dragging != null || draggedEnd != null) return;
            Point2D cursor = new Point2D(event.getX(), event.getY());
            Point2D before = toWorld(cursor.getX(), cursor.getY());
            zoom = Math.clamp(zoom * Math.pow(1.0018, event.getDeltaY()), 0.2, 3.5);
            pan = cursor.subtract(before.multiply(zoom));
            updateView();
            if (selection instanceof Link) redraw();
            event.consume();
        });
    }

    boolean editSelectionName() {
        if (selection == null || nameEditor != null || pressed != null) return false;
        Item item = selection;
        if (item instanceof Device device) {
            nameAnchor = badgePoint(device);
        } else {
            nameAnchor = selectedEdge().at(0.5);
        }
        TextField field = new TextField(item.name);
        field.setPromptText(item.type.name);
        field.setAccessibleText("Имя на схеме");
        field.setManaged(false);
        field.setOnAction(event -> {
            finishNameEdit(item, field);
            requestFocus();
        });
        field.focusedProperty().addListener((obs, before, focused) -> {
            if (!focused) finishNameEdit(item, field);
        });
        nameEditor = field;
        getChildren().add(field);
        field.applyCss();
        positionNameEditor();
        field.requestFocus();
        field.selectAll();
        return true;
    }

    private void finishNameEdit(Item item, TextField field) {
        if (nameEditor != field) return;
        item.name = field.getText().strip();
        nameEditor = null;
        getChildren().remove(field);
        redraw();
    }

    private void positionNameEditor() {
        if (nameEditor == null) return;
        Point2D point = nameAnchor.multiply(zoom).add(pan);
        double width = Math.min(230, getWidth());
        double height = Math.min(nameEditor.prefHeight(width), getHeight());
        nameEditor.resizeRelocate(
            Math.clamp(point.getX() - width / 2, 0, getWidth() - width),
            Math.clamp(point.getY() - height / 2, 0, getHeight() - height),
            width, height);
    }

    private void chooseEndpoint(Device device) {
        if (device == null || device == linkStart) return;
        if (linkStart == null) {
            linkStart = device;
            selection = device;
        } else {
            Link link = new Link(linkType, linkStart, device);
            topology.links.add(link);
            selection = link;
            linkType = null;
            linkStart = null;
        }
        showHint();
    }

    private void showMenu(Item item, double x, double y) {
        if (item == null) return;
        selection = item;
        redraw();
        MenuItem edit = new MenuItem("Свойства…");
        edit.setOnAction(event -> properties.accept(item));
        MenuItem delete = new MenuItem("Удалить");
        delete.setOnAction(event -> deleteSelection());
        menu.getItems().setAll(edit, delete);
        menu.show(this, x, y);
    }

    void deleteSelection() {
        if (selection == null || pressed != null) return;
        topology.remove(selection);
        selection = null;
        cancelLink();
        redraw();
    }

    void cancelLink() {
        linkType = null;
        linkStart = null;
        redraw();
        showHint();
    }

    void showHint() {
        if (linkType == null) {
            status.accept("Перетащите элемент из палитры · ПКМ — свойства или панорамирование · Колесо — масштаб");
        } else {
            status.accept(linkType.name + " · Выберите " + (linkStart == null ? "начальный" : "конечный")
                + " элемент · Esc — отмена");
        }
    }

    void resetView() {
        zoom = 1;
        pan = Point2D.ZERO;
        updateView();
        if (selection instanceof Link) redraw();
    }

    private void setDropParent(Device parent) {
        if (dropParent == parent) return;
        dropParent = parent;
        redraw();
    }

    private Point2D toWorld(double x, double y) {
        return new Point2D(x, y).subtract(pan).multiply(1 / zoom);
    }

    private Point2D displayedCenter(Device device) {
        Point2D center = device.center();
        return dragging != null && dragging.contains(device) ? center.add(dragDelta) : center;
    }

    private Point2D badgePoint(Device device) {
        return displayedCenter(device).subtract(0, device.radius);
    }

    private Edge selectedEdge() {
        return edges.stream().filter(edge -> edge.link() == selection).findFirst().orElse(null);
    }

    private End handleAt(Point2D position) {
        Edge edge = selectedEdge();
        if (edge == null) return null;
        double startDistance = edge.start().distance(position);
        double endDistance = edge.end().distance(position);
        if (Math.min(startDistance, endDistance) > (HANDLE_RADIUS + 3) / zoom) return null;
        return startDistance <= endDistance ? End.SOURCE : End.TARGET;
    }

    private Device endpointTarget(Link link, Point2D position) {
        Device target = deviceAt(position, null);
        Device fixed = draggedEnd == End.SOURCE ? link.target : link.source;
        return target == fixed ? null : target;
    }

    private Device deviceAt(Point2D position, Device excluded) {
        for (NodeBadge entry : nodeBadges.reversed()) {
            Device device = entry.device();
            if ((excluded == null || !excluded.contains(device)) && hitBadge(entry.badge(), position)) {
                return device;
            }
        }
        for (Device device : topology.devices().reversed()) {
            if ((excluded == null || !excluded.contains(device))
                && displayedCenter(device).distance(position) <= device.radius) return device;
        }
        return null;
    }

    private Item itemAt(Point2D position) {
        for (NodeBadge entry : nodeBadges.reversed()) {
            if (hitBadge(entry.badge(), position)) return entry.device();
        }
        for (Edge edge : edges.reversed()) {
            if (hitBadge(edge.badge(), position)) return edge.link();
        }
        for (Edge edge : edges.reversed()) {
            if (position.distance(edge.start()) <= 9 / zoom || position.distance(edge.end()) <= 9 / zoom) {
                continue;
            }
            Point2D previous = edge.start();
            for (int i = 1; i <= 24; i++) {
                Point2D next = edge.at(i / 24.0);
                Point2D segment = next.subtract(previous);
                if (segment.dotProduct(segment) > 0) {
                    double t = Math.clamp(position.subtract(previous).dotProduct(segment)
                        / segment.dotProduct(segment), 0.0, 1.0);
                    if (position.distance(previous.add(segment.multiply(t))) <= 6 / zoom) return edge.link();
                }
                previous = next;
            }
        }
        return deviceAt(position, null);
    }

    private boolean hitBadge(HBox badge, Point2D point) {
        return badge.getBoundsInParent().contains(point.getX(), point.getY());
    }

    private void updateView() {
        transform.setToTransform(zoom, 0, pan.getX(), 0, zoom, pan.getY());
        positionNameEditor();
        grid.setWidth(getWidth());
        grid.setHeight(getHeight());
        var graphics = grid.getGraphicsContext2D();
        graphics.clearRect(0, 0, getWidth(), getHeight());
        graphics.setFill(Color.web("#36516a"));
        double step = 24 * zoom;
        while (step < 13) step *= 2;
        for (double x = pan.getX() % step; x < getWidth(); x += step) {
            for (double y = pan.getY() % step; y < getHeight(); y += step) {
                graphics.fillOval(x, y, 1.4, 1.4);
            }
        }
    }

    void redraw() {
        world.getChildren().clear();
        edges.clear();
        nodeBadges.clear();
        List<Device> devices = topology.devices();
        for (Device device : devices) {
            Point2D center = displayedCenter(device);
            Circle ring = new Circle(center.getX(), center.getY(), device.radius);
            Color color = Color.web(device.type.color);
            boolean highlighted = device == selection || device == linkStart || device == dropParent;
            ring.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), highlighted ? 0.15 : 0.065));
            ring.setStroke(color.deriveColor(0, 1, 1, highlighted ? 1 : 0.65));
            ring.setStrokeWidth(highlighted ? 2.5 : 1.5);
            ring.getStrokeDashArray().setAll(6.0, 5.0);
            world.getChildren().add(ring);
        }
        for (Link link : topology.links) addEdge(link);
        for (Edge edge : edges) addBadge(edge.badge(), edge.at(0.5));
        for (Device device : devices) {
            HBox badge = badge(device);
            addBadge(badge, badgePoint(device));
            nodeBadges.add(new NodeBadge(device, badge));
        }
        Edge edge = selectedEdge();
        if (edge != null) {
            addHandle(edge.start(), edge.link());
            addHandle(edge.end(), edge.link());
        }
    }

    private void addHandle(Point2D point, Link link) {
        Circle handle = new Circle(point.getX(), point.getY(), HANDLE_RADIUS / zoom);
        handle.getStyleClass().add("link-handle");
        handle.setFill(Color.web("#182c42"));
        handle.setStroke(Color.web(link.type.color));
        handle.setStrokeWidth(2 / zoom);
        world.getChildren().add(handle);
    }

    private void addEdge(Link link) {
        Point2D start = displayedCenter(link.source);
        Point2D end = displayedCenter(link.target);
        boolean concentric = start.distance(end) < 1;
        if (concentric) {
            // Separate the contact points so both handles remain usable on concentric nodes.
            start = start.add(-link.source.radius, 0);
            end = end.add(link.target.radius, 0);
        }
        if (link == selection && endpointPreview != null) {
            if (draggedEnd == End.SOURCE) start = endpointPreview;
            else end = endpointPreview;
        }
        int parallel = 0;
        for (Edge prior : edges) {
            Link other = prior.link();
            if ((other.source == link.source && other.target == link.target)
                || (other.target == link.source && other.source == link.target)) parallel++;
        }
        Point2D axis = link.source.id.compareTo(link.target.id) < 0 ? end.subtract(start) : start.subtract(end);
        Point2D normal = new Point2D(-axis.getY(), axis.getX()).normalize();
        double offset = ((parallel + 1) / 2) * 52.0 * (parallel % 2 == 0 ? -1 : 1);
        Point2D control = start.midpoint(end).add(normal.multiply(offset));
        if (concentric) control = control.add(0, 56);
        QuadCurve curve = new QuadCurve(start.getX(), start.getY(), control.getX(), control.getY(),
            end.getX(), end.getY());
        curve.setFill(null);
        curve.setStroke(Color.web(link.type.color));
        curve.setStrokeWidth(link == selection ? 3.5 : 2);
        curve.setStrokeLineCap(StrokeLineCap.ROUND);
        world.getChildren().add(curve);
        edges.add(new Edge(link, start, control, end, badge(link)));
    }

    private HBox badge(Item item) {
        Label name = new Label(item.title());
        name.setMaxWidth(item instanceof Link ? 130 : 140);
        HBox badge = new HBox(5, icons.create(item.type), name);
        badge.getStyleClass().addAll("diagram-badge", item == selection ? "selected-badge" : "regular-badge");
        badge.setManaged(false);
        return badge;
    }

    private void addBadge(HBox badge, Point2D point) {
        world.getChildren().add(badge);
        badge.applyCss();
        badge.autosize();
        badge.relocate(point.getX() - badge.getWidth() / 2, point.getY() - badge.getHeight() / 2);
        badge.layout();
    }
}
