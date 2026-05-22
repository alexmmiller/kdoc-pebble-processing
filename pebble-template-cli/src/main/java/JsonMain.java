import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.loader.FileLoader; // <-- Required for CLI paths
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // 3. Main Method with CLI Argument Parsing
    public static void main(String[] args) {
        String dataPath = null;
        String templatePath = null;
        String outputPath = "output.html"; // Default output file if not specified

        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            if ("--data".equals(args[i]) && i + 1 < args.length) {
                dataPath = args[i + 1];
                i++;
            } else if ("--template".equals(args[i]) && i + 1 < args.length) {
                templatePath = args[i + 1];
                i++;
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputPath = args[i + 1];
                i++;
            }
        }

        // Validate that the required flags were provided
        if (dataPath == null || templatePath == null) {
            System.err.println("❌ Missing required arguments.");
            System.err.println("Usage: java -jar PebbleTemplate.jar --data <JSON file> --template <template file> [--output <output file>]");
            System.exit(1);
        }

        try {
            System.out.println("⏳ Loading JSON data from: " + dataPath);
            ObjectMapper mapper = new ObjectMapper();
            
            Map<String, Object> contextMap = mapper.readValue(
                Paths.get(dataPath).toFile(), 
                new TypeReference<Map<String, Object>>() {}
            );

            System.out.println("⏳ Compiling Pebble template from: " + templatePath);
            
            // IMPORTANT: Use FileLoader so Pebble checks the local filesystem, not the JAR resources
            FileLoader loader = new FileLoader();
            PebbleEngine engine = new PebbleEngine.Builder()
                    .loader(loader)
                    .extension(new DokkaExtension()) 
                    .build();
            
            PebbleTemplate template = engine.getTemplate(templatePath);
            StringWriter writer = new StringWriter();

            // Evaluate template
            template.evaluate(writer, contextMap); 
            String htmlOutput = writer.toString();

            // Write to disk
            Path outPath = Paths.get(outputPath);
            Files.writeString(outPath, htmlOutput);

            System.out.println("✅ HTML successfully generated and written to: " + outPath.toAbsolutePath());

        } catch (Exception e) {
            System.err.println("❌ An error occurred during processing:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}