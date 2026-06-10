package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.console.JsonFormatter;
import dev.voldpix.doppio.list.RequestListEntry;
import dev.voldpix.doppio.list.RequestListService;
import dev.voldpix.doppio.model.DoppioException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

@Command(name = "list", aliases = "ls", mixinStandardHelpOptions = true, description = "List Doppio request files.")
public class ListCommand implements Callable<Integer> {
    @Option(names = "--json", description = "Print machine-readable JSON output.")
    private boolean json;

    private final Path workingDirectory;
    private final RequestListService listService;
    private final JsonFormatter jsonFormatter;
    private final PrintWriter out;
    private final PrintWriter err;

    public ListCommand(
        Path workingDirectory,
        RequestListService listService,
        JsonFormatter jsonFormatter,
        PrintWriter out,
        PrintWriter err
    ) {
        this.workingDirectory = workingDirectory;
        this.listService = listService;
        this.jsonFormatter = jsonFormatter;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var entries = listService.list(workingDirectory);
            var projectDirectory = listService.projectDirectory(workingDirectory);
            if (json) {
                out.println(jsonFormatter.formatList(projectDirectory, entries));
                out.flush();
                return 0;
            }
            out.println("Requests");
            out.println();
            out.println(projectDirectory);
            out.println("`-- requests/");
            var root = new TreeNode();
            entries.forEach(entry -> root.add(entry));
            root.print("    ", out);
            out.flush();
            return 0;
        } catch (DoppioException e) {
            err.println("List Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }

    private static final class TreeNode {
        private final Map<String, TreeNode> children = new TreeMap<>();
        private final java.util.List<RequestListEntry> entries = new java.util.ArrayList<>();

        private void add(RequestListEntry entry) {
            var current = this;
            var relativePath = entry.relativePath();
            for (var i = 0; i < relativePath.getNameCount() - 1; i++) {
                current = current.children.computeIfAbsent(relativePath.getName(i).toString(), ignored -> new TreeNode());
            }
            current.entries.add(entry);
            current.entries.sort(Comparator.comparing(item -> item.relativePath().getFileName().toString()));
        }

        private void print(String prefix, PrintWriter out) {
            var total = children.size() + entries.size();
            var index = 0;

            for (var child : children.entrySet()) {
                index++;
                var last = index == total;
                out.printf("%s%s %s/%n", prefix, last ? "`--" : "|--", child.getKey());
                child.getValue().print(prefix + (last ? "    " : "|   "), out);
            }

            for (var entry : entries) {
                index++;
                var last = index == total;
                out.printf("%s%s %s (%s)%s%n",
                    prefix,
                    last ? "`--" : "|--",
                    entry.displayName(),
                    entry.relativePath(),
                    entry.hasError() ? " [" + entry.error() + "]" : "");
            }
        }
    }
}
