package smartnetwork;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Point2D;

final class Topology {
    enum Type {
        VPS("VPS Server", "Virtual private server", "vps", false, "#65bbff"),
        ROUTER("Home Router", "Home network router", "router", false, "#66dfbf"),
        PC("Home PC", "Home computer", "pc", false, "#8daaff"),
        WG_SERVER("WireGuard Server", "WireGuard server", "wg-server", false, "#ff8e92"),
        WG_CLIENT("WireGuard Client", "WireGuard client", "wg-client", false, "#ff8e92"),
        AMNEZIA_SERVER("Amnezia VPN Server", "Amnezia VPN server", "amnezia-server", false, "#c3a2ff"),
        AMNEZIA_CLIENT("Amnezia VPN Client", "Amnezia VPN client", "amnezia-client", false, "#c3a2ff"),
        MIHOMO("Mihomo App", "Mihomo application", "mihomo", false, "#f7c670"),
        GITHUB("GitHub", "GitHub repository or service", "github", false, "#e0e7f8"),
        PORT("Port", "Network port for a service", "port", false, "#7edbef"),
        UNKNOWN("UNKNOWN", "No link protocol selected", "unknown", true, "#aabfd6"),
        HTTP("http", "HTTP connection", "http", true, "#65bbff"),
        HTTPS("https", "HTTP connection encrypted with TLS", "https", true, "#66dfbf"),
        SSH("ssh", "SSH connection", "ssh", true, "#f7c670"),
        WIREGUARD("wireguard", "WireGuard tunnel", "wireguard", true, "#ff8e92"),
        AMNEZIA("amnezia-vpn", "Amnezia VPN tunnel", "amnezia-vpn", true, "#c3a2ff"),
        HISTERIA("histeria-vpn", "Hysteria VPN tunnel", "histeria-vpn", true, "#66dfbf");

        final String name, tooltip, icon, color;
        final boolean link;

        Type(String name, String tooltip, String icon, boolean link, String color) {
            this.name = name;
            this.tooltip = tooltip;
            this.icon = "/icons/" + icon + ".svg";
            this.link = link;
            this.color = color;
        }
    }

    abstract static class Item {
        final String id;
        Type type;
        final StringProperty name = new SimpleStringProperty("");
        String description = "";

        Item(String id, Type type) {
            this.id = id;
            this.type = type;
        }

        String title() {
            return name.get().isBlank() ? type.name : name.get();
        }
    }

    enum SensitiveProperty {
        IP_ADDRESS("IP address"), LOGIN("Username"), PASSWORD("Password");

        final String label;

        SensitiveProperty(String label) {
            this.label = label;
        }
    }

    static final class Device extends Item {
        final List<Device> children = new ArrayList<>();
        final EnumMap<SensitiveProperty, String> sensitiveValues = new EnumMap<>(SensitiveProperty.class);
        Device parent;
        Point2D position;
        double radius = 49;

        Device(Type type, Point2D position) {
            this(UUID.randomUUID().toString(), type, position);
        }

        Device(String id, Type type, Point2D position) {
            super(id, type);
            this.position = position;
        }

        Point2D center() {
            return parent == null ? position : parent.center().add(position);
        }

        boolean contains(Device device) {
            for (; device != null; device = device.parent) {
                if (device == this) return true;
            }
            return false;
        }
    }

    static final class Link extends Item {
        Device source, target;

        Link(Type type, Device source, Device target) {
            this(UUID.randomUUID().toString(), type, source, target);
        }

        Link(String id, Type type, Device source, Device target) {
            super(id, type);
            this.source = source;
            this.target = target;
        }
    }

    final List<Device> roots = new ArrayList<>();
    final List<Link> links = new ArrayList<>();

    List<Device> devices() {
        List<Device> devices = new ArrayList<>();
        collect(roots, devices);
        return devices;
    }

    private void collect(List<Device> branch, List<Device> result) {
        for (Device device : branch) {
            result.add(device);
            collect(device.children, result);
        }
    }

    void place(Device device, Device parent, Point2D position) {
        if (device.contains(parent)) throw new IllegalArgumentException("Cyclic containment");
        Device previousParent = device.parent;
        siblings(previousParent).remove(device);
        device.position = parent == null ? position : position.subtract(parent.center());
        device.parent = parent;
        siblings(parent).add(device);
        if (previousParent != null && previousParent != parent && !previousParent.contains(parent)) {
            arrange(null, previousParent);
        }
        arrange(device, parent);
    }

    void remove(Item item) {
        if (item instanceof Link link) {
            links.remove(link);
        } else if (item instanceof Device device) {
            links.removeIf(link -> device.contains(link.source) || device.contains(link.target));
            siblings(device.parent).remove(device);
            arrange(null, device.parent);
        }
    }

    private List<Device> siblings(Device parent) {
        return parent == null ? roots : parent.children;
    }

    private void arrange(Device anchor, Device parent) {
        while (true) {
            separate(siblings(parent), anchor);
            if (parent == null) return;
            double radius = 49;
            for (Device child : parent.children) {
                radius = Math.max(radius, child.position.magnitude() + child.radius + 39);
            }
            double growth = radius - parent.radius;
            parent.radius = radius;
            if (growth > 0) moveSiblingsOutward(parent, growth);
            anchor = parent;
            parent = parent.parent;
        }
    }

    private void moveSiblingsOutward(Device expanded, double growth) {
        for (Device sibling : siblings(expanded.parent)) {
            if (sibling == expanded) continue;
            Point2D direction = sibling.position.subtract(expanded.position).normalize();
            sibling.position = sibling.position.add(direction.multiply(growth));
        }
    }

    private void separate(List<Device> siblings, Device anchor) {
        List<Device> placed = new ArrayList<>();
        if (anchor != null) placed.add(anchor);
        for (Device device : siblings) {
            if (device == anchor) continue;
            boolean overlaps = placed.stream().anyMatch(other ->
                other.position.distance(device.position) < spacing(other, device));
            if (overlaps) {
                Point2D direction = device.position.subtract(placed.getFirst().position);
                if (direction.magnitude() < 0.01) {
                    double angle = placed.size() * 2.399963;
                    direction = new Point2D(Math.cos(angle), Math.sin(angle));
                }
                direction = direction.normalize();
                double shift = 0;
                // Find the end of each occupied interval along the outward ray.
                for (Device other : placed) {
                    Point2D offset = device.position.subtract(other.position);
                    double projection = offset.dotProduct(direction);
                    double gap = spacing(other, device);
                    double discriminant = gap * gap - offset.dotProduct(offset) + projection * projection;
                    if (discriminant > 0) {
                        shift = Math.max(shift, -projection + Math.sqrt(discriminant) + 0.1);
                    }
                }
                device.position = device.position.add(direction.multiply(shift));
            }
            placed.add(device);
        }
    }

    private double spacing(Device first, Device second) {
        return first.radius + second.radius + 34;
    }

    String toJson() {
        List<String> nodes = new ArrayList<>();
        for (Device device : devices()) {
            Point2D center = device.center();
            nodes.add("{" + fields(device) + ",\"parentId\":"
                + (device.parent == null ? "null" : quote(device.parent.id))
                + ",\"x\":" + center.getX() + ",\"y\":" + center.getY() + "}");
        }
        List<String> connections = new ArrayList<>();
        for (Link link : links) {
            connections.add("{" + fields(link) + ",\"sourceId\":" + quote(link.source.id)
                + ",\"targetId\":" + quote(link.target.id) + "}");
        }
        return "{\"schemaVersion\":1,\"nodes\":[" + String.join(",", nodes)
            + "],\"links\":[" + String.join(",", connections) + "]}";
    }

    private String fields(Item item) {
        return "\"id\":" + quote(item.id) + ",\"type\":" + quote(item.type.name())
            + ",\"typeName\":" + quote(item.type.name) + ",\"customName\":" + quote(item.name.get())
            + ",\"tooltip\":" + quote(item.type.tooltip) + ",\"descriptionForAi\":" + quote(item.description)
            + ",\"iconPath\":" + quote(item.type.icon);
    }

    private String quote(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                default -> {
                    if (character < 32 || Character.isSurrogate(character)) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        return json.append('"').toString();
    }
}
