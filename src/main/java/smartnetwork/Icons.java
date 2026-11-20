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
            try (InputStream file = Icons.class.getResourceAsStream(type.icon)) {
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                var svg = factory.newDocumentBuilder().parse(file);
                var path = (Element) svg.getElementsByTagName("path").item(0);
                paths.put(type, path.getAttribute("d"));
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot load " + type.icon, failure);
            }
        }
    }

    Group create(Type type) {
        var path = new SVGPath();
        path.setContent(paths.get(type));
        path.setFill(null);
        path.setStroke(Color.web(type.color));
        path.setStrokeWidth(1.65);
        path.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        path.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        var bounds = new javafx.scene.shape.Rectangle(24, 24, Color.TRANSPARENT);
        return new Group(bounds, path);
    }
}
