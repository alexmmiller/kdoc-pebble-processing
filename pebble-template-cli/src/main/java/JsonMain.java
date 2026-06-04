import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class JsonMain {

    // 1. The Custom Filter Logic in Java
    public static class CleanDocFilter implements Filter {
        @Override
        public List<String> getArgumentNames() {
            return Collections.emptyList();
        }

        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
            if (input == null) return "";
            String rawString = input.toString();

            Pattern pattern = Pattern.compile("Text\\(body=(.*?), children=\\[");
            Matcher matcher = pattern.matcher(rawString);

            StringBuilder cleanText = new StringBuilder();
            while (matcher.find()) {
                cleanText.append(matcher.group(1));
            }

            if (rawString.startsWith("Param(")) {
                Pattern paramPattern = Pattern.compile(", name=([^)]+)\\)$");
                Matcher paramMatcher = paramPattern.matcher(rawString);
                String paramName = paramMatcher.find() ? paramMatcher.group(1) : "param";
                return "@param " + paramName + " - " + cleanText.toString();
            }

            if (rawString.startsWith("Author(")) {
                return "Author: " + cleanText.toString();
            }

            return cleanText.toString();
        }
    }

    // 2. The Extension to register the Filter
    public static class DokkaExtension extends AbstractExtension {
        @Override
        public Map<String, Filter> getFilters() {
            Map<String, Filter> filters = new HashMap<>();
            filters.put("cleanDoc", new CleanDocFilter());
            return filters;
        }
    }

    // Thread-safe logging method for the parallel stream
    private static synchronized void logToFile(Path logFile, String message) {
        if (logFile == null) return;
        try {
            Files.writeString(logFile, message + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }

    // 3. Main Method with Directory Traversal and Parallel Processing
    public static void main(String[] args) {
        String inputDirStr = null;
        String templatePathStr = null;
        String outputDirStr = null;
        String logFileStr = null;

        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            if ("--inputDir".equals(args[i]) && i + 1 < args.length) {
                inputDirStr = args[i + 1];
                i++;
            } else if ("--template".equals(args[i]) && i + 1 < args.length) {
                templatePathStr = args[i + 1];
                i++;
            } else if ("--outputDir".equals(args[i]) && i + 1 < args.length) {
                outputDirStr = args[i + 1];
                i++;
            } else if ("--logFile".equals(args[i]) && i + 1 < args.length) {
                logFileStr = args[i + 1];
                i++;
            }
        }

        // Validate that the required flags were provided
        if (inputDirStr == null || templatePathStr == null || outputDirStr == null) {
            System.err.println("❌ Missing required arguments.");
            System.err.println("Usage: java -jar PebbleTemplate.jar --inputDir <JSON dir> --template <template file> --outputDir <output dir> [--logFile <log path>]");
            System.exit(1);
        }

        Path inputDir = Paths.get(inputDirStr).toAbsolutePath().normalize();
        Path outputDir = Paths.get(outputDirStr).toAbsolutePath().normalize();
        Path templatePath = Paths.get(templatePathStr).toAbsolutePath().normalize();
        Path logPath = logFileStr != null ? Paths.get(logFileStr).toAbsolutePath().normalize() : null;

        try {
            // Clear previous log file if it exists
            if (logPath != null) {
                Files.deleteIfExists(logPath);
            }

            System.out.println("⏳ Initializing Pebble Engine with template: " + templatePath);
            
            ObjectMapper mapper = new ObjectMapper();
            FileLoader loader = new FileLoader();
            PebbleEngine engine = new PebbleEngine.Builder()
                    .loader(loader)
                    .extension(new DokkaExtension()) 
                    .build();
            
            PebbleTemplate template = engine.getTemplate(templatePathStr);

            System.out.println("🚀 Starting parallel processing of JSON files in: " + inputDir);
            long startTime = System.currentTimeMillis();

            // Recursively walk the directory, filter for JSON, and process in parallel
            try (Stream<Path> paths = Files.walk(inputDir)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".json"))
                     .parallel() // Enable multi-threading across all CPU cores
                     .forEach(jsonFile -> {
                         Path outPath = null;
                         try {
                             // 1. Calculate relative path to mirror the directory structure
                             Path relativePath = inputDir.relativize(jsonFile);
                             Path relativeParent = relativePath.getParent() == null ? Paths.get("") : relativePath.getParent();
                             
                             // 2. Swap extension to .html
                             String htmlFileName = relativePath.getFileName().toString().replaceFirst("\\.json$", ".html");
                             
                             // 3. Resolve output directories and create them if missing
                             Path resolvedOutputDir = outputDir.resolve(relativeParent);
                             outPath = resolvedOutputDir.resolve(htmlFileName);
                             Files.createDirectories(resolvedOutputDir);

                             // 4. Parse the JSON
                             Map<String, Object> contextMap = mapper.readValue(
                                 jsonFile.toFile(), 
                                 new TypeReference<Map<String, Object>>() {}
                             );

                             // 5. Evaluate the Template
                             StringWriter writer = new StringWriter();
                             template.evaluate(writer, contextMap); 
                             
                             // 6. Write to disk
                             Files.writeString(outPath, writer.toString());

                             // 7. Log Success
                             String successMsg = String.format("Input file: %s -- Template file: %s -- Output file: %s", 
                                     jsonFile.toAbsolutePath(), templatePath, outPath.toAbsolutePath());
                             logToFile(logPath, successMsg);
                             System.out.println("✅ Generated: " + relativePath + " -> " + outPath);

                         } catch (Exception e) {
                             // Construct detailed error message
                             String outPathStr = outPath != null ? outPath.toAbsolutePath().toString() : "UNKNOWN (Failed before resolution)";
                             String errorContext = String.format("Input file: %s\nTemplate file: %s\nOutput file: %s", 
                                     jsonFile.toAbsolutePath(), templatePath, outPathStr);
                             
                             // Print to Standard Error
                             System.err.println("\n❌ Failed to process file!");
                             System.err.println(errorContext);
                             e.printStackTrace(); // Prints the stack trace to the console
                             
                             // Log Verbose Failure to file if specified
                             if (logPath != null) {
                                 StringWriter sw = new StringWriter();
                                 e.printStackTrace(new PrintWriter(sw));
                                 String errorMsg = errorContext.replace("\n", " -- ") + "\nERROR:\n" + sw.toString();
                                 logToFile(logPath, errorMsg);
                             }
                         }
                     });
            }

            long endTime = System.currentTimeMillis();
            System.out.println("🎉 Processing complete in " + (endTime - startTime) + "ms.");

        } catch (Exception e) {
            System.err.println("❌ An error occurred during initialization:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}