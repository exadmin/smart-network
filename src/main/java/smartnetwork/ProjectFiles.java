package smartnetwork;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Point2D;
import smartnetwork.Topology.Device;
import smartnetwork.Topology.Link;
import smartnetwork.Topology.SensitiveProperty;
import smartnetwork.Topology.Type;

final class ProjectFiles {
    static final String EXTENSION = ".json-sn";
    private static final int VERSION = 1;
    private static final Gson JSON = new GsonBuilder()
        .setStrictness(Strictness.STRICT)
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private record Project(int schemaVersion, List<NodeData> nodes, List<LinkData> links) {}
    private record NodeData(String id, Type type, String customName, String descriptionForAi,
                            double x, double y, double radius, Map<SensitiveProperty, String> sensitiveValues,
                            List<NodeData> children) {}
    private record LinkData(String id, Type type, String customName, String descriptionForAi,
                            String sourceId, String targetId) {}

    static String snapshot(Topology topology) {
        return JSON.toJson(new Project(VERSION, snapshotNodes(topology.roots),
            topology.links.stream().map(link -> new LinkData(link.id, link.type, link.name.get(),
                link.description, link.source.id, link.target.id)).toList()));
    }

    private static List<NodeData> snapshotNodes(List<Device> devices) {
        List<NodeData> result = new ArrayList<>();
        for (Device device : devices) {
            result.add(new NodeData(device.id, device.type, device.name.get(), device.description,
                device.position.getX(), device.position.getY(), device.radius,
                new EnumMap<>(device.sensitiveValues), snapshotNodes(device.children)));
        }
        return result;
    }

    static void save(Path path, String snapshot) throws IOException {
        Path target = path.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".smart-network-", ".tmp");
        try {
            Files.writeString(temporary, snapshot + System.lineSeparator());
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException failure) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Topology load(Path path) throws IOException {
        Project project;
        try (var reader = Files.newBufferedReader(path)) {
            project = JSON.fromJson(reader, Project.class);
        }
        if (project == null || project.schemaVersion != VERSION || project.nodes == null || project.links == null) {
            throw new IOException("Unsupported project format or version.");
        }
        Topology restored = new Topology();
        Map<String, Device> devices = new HashMap<>();
        Set<String> ids = new HashSet<>();
        restoreNodes(project.nodes, restored.roots, null, devices, ids);
        for (LinkData data : project.links) {
            if (data == null || data.type == null || !data.type.link || !validId(data.id, ids)) {
                throw new IOException("Invalid link record.");
            }
            Device source = devices.get(data.sourceId);
            Device target = devices.get(data.targetId);
            if (source == null || target == null || source == target) {
                throw new IOException("A link refers to invalid nodes.");
            }
            var link = new Link(data.id, data.type, source, target);
            link.name.set(text(data.customName));
            link.description = text(data.descriptionForAi);
            restored.links.add(link);
        }
        return restored;
    }

    private static void restoreNodes(List<NodeData> records, List<Device> destination, Device parent,
                                     Map<String, Device> devices, Set<String> ids) throws IOException {
        for (NodeData data : records) {
            if (data == null || data.type == null || data.type.link || data.children == null
                || !Double.isFinite(data.x) || !Double.isFinite(data.y)
                || !Double.isFinite(data.radius) || data.radius <= 0 || !validId(data.id, ids)) {
                throw new IOException("Invalid node record.");
            }
            Device device = new Device(data.id, data.type, new Point2D(data.x, data.y));
            device.parent = parent;
            device.radius = data.radius;
            device.name.set(text(data.customName));
            device.description = text(data.descriptionForAi);
            if (data.sensitiveValues != null) {
                for (var entry : data.sensitiveValues.entrySet()) {
                    if (entry.getKey() == null) throw new IOException("Unknown node property.");
                    device.sensitiveValues.put(entry.getKey(), text(entry.getValue()));
                }
            }
            destination.add(device);
            devices.put(device.id, device);
            restoreNodes(data.children, device.children, device, devices, ids);
        }
    }

    private static boolean validId(String id, Set<String> ids) {
        return id != null && !id.isBlank() && ids.add(id);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private ProjectFiles() {}
}
