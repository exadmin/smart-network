package smartnetwork;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import org.w3c.dom.Element;
import smartnetwork.Topology.Type;

final class Icons {
    private final Map<Type, String> paths = new EnumMap<>(Type.class);

    Icons() {
        for (Type type : Type.values()) {
            paths.put(type, loadPath(type.icon));
        }
    }

    private String loadPath(String resource) {
        try (InputStream file = Icons.class.getResourceAsStream(resource)) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var svg = factory.newDocumentBuilder().parse(file);
            var path = (Element) svg.getElementsByTagName("path").item(0);
            return path.getAttribute("d");
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load " + resource, failure);
        }
    }

    Group create(Type type) {
        return graphic(paths.get(type), Color.web(type.color));
    }

    Group toolbarIcon(String resource) {
        Group graphic = graphic(loadPath("/icons/toolbar/" + resource + ".svg"), Color.web("#b9ccdf"));
        graphic.getStyleClass().add("editor-tool-icon");
        return graphic;
    }

    private Group graphic(String data, Color color) {
        var path = new SVGPath();
        path.setContent(data);
        path.setFill(null);
        path.setStroke(color);
        path.setStrokeWidth(1.65);
        path.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        path.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        var bounds = new javafx.scene.shape.Rectangle(24, 24, Color.TRANSPARENT);
        return new Group(bounds, path);
    }
}
