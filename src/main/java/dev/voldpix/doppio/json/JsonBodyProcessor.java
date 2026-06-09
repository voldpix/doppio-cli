package dev.voldpix.doppio.json;

public class JsonBodyProcessor {
    public String process(String body) throws JsonBodyException {
        if (body == null || body.isBlank()) {
            return null;
        }

        var parser = new JsonParser(body);
        parser.parse();
        return body;
    }
}
