package org.openjfx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RenameOperation implements ReversibleOperation {

    private final Path folderPath;
    private final String originalName;
    private final String renamedName;

    public RenameOperation(Path folderPath, String originalName, String renamedName) {
        this.folderPath = folderPath;
        this.originalName = originalName;
        this.renamedName = renamedName;
    }

    public RenameOperation _do() throws IOException {
        Files.move(folderPath.resolve(originalName), folderPath.resolve(renamedName));
        return this;
    }

    public RenameOperation _undo() throws IOException {
        Files.move(folderPath.resolve(renamedName), folderPath.resolve(originalName));
        return this;
    }

}
