package smartnetwork;

import com.google.gson.JsonParseException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

final class ProjectActions {
    private final Stage stage;
    private final Topology topology;
    private final NetworkCanvas canvas;
    private final Consumer<String> status;
    private String savedSnapshot;
    private final ObjectProperty<Path> currentFile = new SimpleObjectProperty<>();

    ProjectActions(Stage stage, Topology topology, NetworkCanvas canvas, Consumer<String> status) {
        this.stage = stage;
        this.topology = topology;
        this.canvas = canvas;
        this.status = status;
        savedSnapshot = ProjectFiles.snapshot(topology);
    }

    void newProject() {
        if (!confirmReplacement()) return;
        replaceProject(new Topology(), null);
        status.accept("Создан новый проект.");
    }

    BooleanBinding noCurrentFile() {
        return currentFile.isNull();
    }

    void saveCurrent() {
        Path path = currentFile.get();
        if (path == null) return;
        canvas.commitNameEdit();
        save(path, ProjectFiles.snapshot(topology));
    }

    boolean saveAs() {
        canvas.commitNameEdit();
        Path current = currentFile.get();
        FileChooser chooser = fileChooser("Сохранить проект как");
        chooser.setInitialFileName(current == null ? "project" + ProjectFiles.EXTENSION
            : current.getFileName().toString());
        File file = chooser.showSaveDialog(stage);
        if (file == null) return false;
        Path path = file.toPath();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(ProjectFiles.EXTENSION)) {
            path = path.resolveSibling(file.getName() + ProjectFiles.EXTENSION);
            // The native chooser only checks the name that was entered.
            if (Files.exists(path) && !confirmOverwrite(path)) return false;
        }
        return save(path, ProjectFiles.snapshot(topology));
    }

    void loadFrom() {
        File file = fileChooser("Загрузить проект").showOpenDialog(stage);
        if (file == null || !confirmReplacement()) return;
        try {
            Path path = file.toPath();
            replaceProject(ProjectFiles.load(path), path);
            status.accept("Проект загружен · " + file.getName());
        } catch (JsonParseException failure) {
            error("Не удалось загрузить проект", "Файл содержит некорректный JSON.");
        } catch (IOException failure) {
            error("Не удалось загрузить проект", failure.getMessage());
        }
    }

    private boolean confirmReplacement() {
        canvas.commitNameEdit();
        String current = ProjectFiles.snapshot(topology);
        if (current.equals(savedSnapshot)) return true;
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Не сохранять", ButtonBar.ButtonData.NO);
        Alert dialog = dialog(Alert.AlertType.CONFIRMATION, "Несохранённые изменения",
            "Сохранить изменения в текущем проекте?", save, discard, ButtonType.CANCEL);
        ButtonType answer = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (answer == discard) return true;
        Path path = currentFile.get();
        return answer == save && (path == null ? saveAs() : save(path, current));
    }

    private boolean confirmOverwrite(Path file) {
        return dialog(Alert.AlertType.CONFIRMATION, "Заменить файл?",
            "Файл «" + file.getFileName() + "» уже существует.", ButtonType.OK, ButtonType.CANCEL)
            .showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private boolean save(Path path, String snapshot) {
        try {
            ProjectFiles.save(path, snapshot);
            currentFile.set(path.toAbsolutePath());
            savedSnapshot = snapshot;
            status.accept("Проект сохранён · " + path.getFileName());
            return true;
        } catch (IOException failure) {
            error("Не удалось сохранить проект", failure.getMessage());
            return false;
        }
    }

    private void replaceProject(Topology restored, Path path) {
        topology.roots.clear();
        topology.roots.addAll(restored.roots);
        topology.links.clear();
        topology.links.addAll(restored.links);
        currentFile.set(path == null ? null : path.toAbsolutePath());
        savedSnapshot = ProjectFiles.snapshot(topology);
        canvas.resetProjectView();
    }

    private FileChooser fileChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "Smart Network (*" + ProjectFiles.EXTENSION + ")", "*" + ProjectFiles.EXTENSION));
        Path current = currentFile.get();
        if (current != null && Files.isDirectory(current.getParent())) {
            chooser.setInitialDirectory(current.getParent().toFile());
        }
        return chooser;
    }

    private Alert dialog(Alert.AlertType type, String title, String message, ButtonType... buttons) {
        Alert dialog = new Alert(type, message, buttons);
        dialog.initOwner(stage);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/editor.css").toExternalForm());
        return dialog;
    }

    private void error(String title, String message) {
        dialog(Alert.AlertType.ERROR, title, message, ButtonType.OK).showAndWait();
    }
}
