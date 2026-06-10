package dev.voldpix.doppio.request;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;
import dev.voldpix.doppio.pipeline.RequestFileResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RequestFileOperator {
    private final DoppioProjectResolver projectResolver;
    private final RequestFileResolver requestFileResolver;

    public RequestFileOperator() {
        this(new DoppioProjectResolver(), new RequestFileResolver());
    }

    public RequestFileOperator(DoppioProjectResolver projectResolver, RequestFileResolver requestFileResolver) {
        this.projectResolver = projectResolver;
        this.requestFileResolver = requestFileResolver;
    }

    public RequestFileOperation move(Path sourcePath, Path destinationPath, Path workingDirectory) throws DoppioException {
        var paths = resolvePaths(sourcePath, destinationPath, workingDirectory);
        try {
            Files.createDirectories(paths.destinationFile().getParent());
            Files.move(paths.sourceFile(), paths.destinationFile());
            return new RequestFileOperation(paths.sourceRelativePath(), paths.destinationRelativePath(), paths.destinationFile());
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to move recipe: " + paths.sourceRelativePath(), e);
        }
    }

    public RequestFileOperation copy(Path sourcePath, Path destinationPath, Path workingDirectory) throws DoppioException {
        var paths = resolvePaths(sourcePath, destinationPath, workingDirectory);
        try {
            Files.createDirectories(paths.destinationFile().getParent());
            Files.copy(paths.sourceFile(), paths.destinationFile());
            return new RequestFileOperation(paths.sourceRelativePath(), paths.destinationRelativePath(), paths.destinationFile());
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to copy recipe: " + paths.sourceRelativePath(), e);
        }
    }

    public RequestFileOperation rename(Path sourcePath, Path newName, Path workingDirectory) throws DoppioException {
        if (newName == null || newName.getFileName() == null) {
            throw new DoppioException(ErrorKind.FILE, "New recipe name is required");
        }
        var normalizedName = newName.normalize();
        if (normalizedName.getNameCount() != 1) {
            throw new DoppioException(ErrorKind.FILE, "Rename target must be a file name. Use mv to change folders.");
        }

        var context = projectContext(workingDirectory);
        var source = sourceFile(sourcePath, workingDirectory, context.recipesDir());
        var sourceParent = source.relativePath().getParent();
        var destinationRelative = sourceParent == null
            ? normalizeDestinationPath(normalizedName)
            : sourceParent.resolve(normalizeDestinationPath(normalizedName));

        return moveResolved(source, destinationRelative, context.recipesDir());
    }

    private RequestFileOperation moveResolved(SourceRecipe source, Path destinationRelative, Path recipesDir) throws DoppioException {
        var destinationFile = destinationFile(destinationRelative, recipesDir);
        try {
            Files.createDirectories(destinationFile.getParent());
            Files.move(source.file(), destinationFile);
            return new RequestFileOperation(source.relativePath(), destinationRelative, destinationFile);
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to rename recipe: " + source.relativePath(), e);
        }
    }

    private OperationPaths resolvePaths(Path sourcePath, Path destinationPath, Path workingDirectory) throws DoppioException {
        var context = projectContext(workingDirectory);
        var source = sourceFile(sourcePath, workingDirectory, context.recipesDir());
        var destinationRelative = normalizeDestinationPath(destinationPath);
        var destinationFile = destinationFile(destinationRelative, context.recipesDir());
        return new OperationPaths(source.file(), source.relativePath(), destinationFile, destinationRelative);
    }

    private ProjectContext projectContext(Path workingDirectory) throws DoppioException {
        var doppioDir = projectResolver.findDoppioDirectory(workingDirectory.toAbsolutePath().normalize());
        if (doppioDir == null) {
            throw new DoppioException(ErrorKind.FILE, "No .doppio project found. Run `doppio init` first.");
        }
        return new ProjectContext(doppioDir.resolve("recipes").toAbsolutePath().normalize());
    }

    private SourceRecipe sourceFile(Path requestedPath, Path workingDirectory, Path recipesDir) throws DoppioException {
        var resolution = requestFileResolver.resolve(requestedPath, workingDirectory);
        var sourceFile = resolution.requestFile().toAbsolutePath().normalize();
        if (!sourceFile.startsWith(recipesDir)) {
            throw new DoppioException(ErrorKind.FILE, "Recipe operation only supports files under .doppio/recipes");
        }
        return new SourceRecipe(sourceFile, recipesDir.relativize(sourceFile));
    }

    private Path destinationFile(Path destinationRelative, Path recipesDir) throws DoppioException {
        var destinationFile = recipesDir.resolve(destinationRelative).normalize();
        if (!destinationFile.startsWith(recipesDir)) {
            throw new DoppioException(ErrorKind.FILE, "Recipe path must stay inside .doppio/recipes: " + destinationRelative);
        }
        if (Files.exists(destinationFile)) {
            throw new DoppioException(ErrorKind.FILE, "Recipe already exists: " + destinationRelative);
        }
        return destinationFile;
    }

    private Path normalizeDestinationPath(Path requestedPath) throws DoppioException {
        if (requestedPath == null || requestedPath.getFileName() == null) {
            throw new DoppioException(ErrorKind.FILE, "Destination recipe path is required");
        }
        if (requestedPath.isAbsolute()) {
            throw new DoppioException(ErrorKind.FILE, "Use a recipe path relative to .doppio/recipes");
        }

        var path = requestedPath.normalize();
        if (path.getNameCount() == 0 || path.startsWith("..")) {
            throw new DoppioException(ErrorKind.FILE, "Destination recipe path is required");
        }

        var first = path.getName(0).toString();
        if (".doppio".equals(first) || "recipes".equals(first)) {
            throw new DoppioException(ErrorKind.FILE, "Use shorthand paths like auth/login.dopo, without .doppio/recipes");
        }

        var fileName = path.getFileName().toString();
        if (!fileName.endsWith(".dopo")) {
            if (fileName.contains(".")) {
                throw new DoppioException(ErrorKind.FILE, "Only .dopo recipe files are supported: " + requestedPath);
            }
            path = path.resolveSibling(fileName + ".dopo");
        }
        return path;
    }

    private record ProjectContext(Path recipesDir) {
    }

    private record SourceRecipe(Path file, Path relativePath) {
    }

    private record OperationPaths(Path sourceFile, Path sourceRelativePath, Path destinationFile, Path destinationRelativePath) {
    }
}
