package io.outcomeescrow.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict canonical JSON support for small trusted schema payloads. JSON structure is intentionally
 * tiny: only object keys, numbers, strings, booleans and null. Objects are accepted only when their
 * keys are valid; duplicate keys, trailing data and other malformed content are rejected as invalid
 * evidence.
 */
public final class CanonicalJson {
  private CanonicalJson() {}

  // Parses one JSON object string. Throws IllegalArgumentException on any malformed content.
  public static Map<String, Object> parseObject(String json) {
    if (json == null) {
      throw new IllegalArgumentException("payload is required");
    }
    Parser parser = new Parser(json.trim());
    Object value = parser.parseValue();
    parser.consumeWhitespace();
    if (parser.index != json.trim().length()) {
      throw new IllegalArgumentException("unexpected trailing JSON data");
    }
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("payload must be a JSON object");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new IllegalArgumentException("JSON keys must be strings");
      }
      result.put(key, entry.getValue());
    }
    return result;
  }

  public static boolean isNumber(Object value) {
    return value instanceof Number;
  }

  public static double number(Map<String, Object> object, String key) {
    Object value = requireKey(object, key);
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException("missing number: " + key);
    }
    return number.doubleValue();
  }

  public static String string(Map<String, Object> object, String key) {
    Object value = requireKey(object, key);
    if (!(value instanceof String text)) {
      throw new IllegalArgumentException("missing string: " + key);
    }
    return text;
  }

  public static void requireExactKeys(Map<String, Object> object, String... keys) {
    if (object.size() != keys.length) {
      throw new IllegalArgumentException("unexpected JSON keys");
    }
    for (String key : keys) {
      if (!object.containsKey(key)) {
        throw new IllegalArgumentException("missing JSON key: " + key);
      }
    }
  }

  private static Object requireKey(Map<String, Object> object, String key) {
    if (!object.containsKey(key)) {
      throw new IllegalArgumentException("missing JSON key: " + key);
    }
    return object.get(key);
  }

  private static final class Parser {
    private final String text;
    private int index;

    private Parser(String text) {
      this.text = text;
    }

    private Object parseValue() {
      skipWhitespace();
      if (index >= text.length()) {
        throw new IllegalArgumentException("unexpected JSON end");
      }
      char c = text.charAt(index);
      if (c == '{') {
        return parseObject();
      }
      if (c == '[') {
        return parseArray();
      }
      if (c == '"') {
        return parseString();
      }
      if (c == 't' || c == 'f') {
        return parseBoolean();
      }
      if (c == 'n') {
        return parseNull();
      }
      return parseNumber();
    }

    private Map<String, Object> parseObject() {
      expect('{');
      skipWhitespace();
      Map<String, Object> result = new LinkedHashMap<>();
      if (peek('}')) {
        expect('}');
        return result;
      }
      while (true) {
        skipWhitespace();
        String key = parseString();
        if (result.containsKey(key)) {
          throw new IllegalArgumentException("duplicate JSON key: " + key);
        }
        skipWhitespace();
        expect(':');
        Object value = parseValue();
        result.put(key, value);
        skipWhitespace();
        if (peek(',')) {
          expect(',');
          continue;
        }
        if (peek('}')) {
          expect('}');
          break;
        }
        throw new IllegalArgumentException("invalid JSON object");
      }
      return result;
    }

    private List<Object> parseArray() {
      expect('[');
      skipWhitespace();
      List<Object> result = new ArrayList<>();
      if (peek(']')) {
        expect(']');
        return result;
      }
      while (true) {
        result.add(parseValue());
        skipWhitespace();
        if (peek(',')) {
          expect(',');
          continue;
        }
        if (peek(']')) {
          expect(']');
          break;
        }
        throw new IllegalArgumentException("invalid JSON array");
      }
      return result;
    }

    private String parseString() {
      expect('"');
      StringBuilder builder = new StringBuilder();
      while (index < text.length()) {
        char c = text.charAt(index++);
        if (c == '"') {
          return builder.toString();
        }
        if (c == '\\') {
          if (index >= text.length()) {
            throw new IllegalArgumentException("invalid JSON escape");
          }
          char escape = text.charAt(index++);
          switch (escape) {
            case '"' -> builder.append('"');
            case '\\' -> builder.append('\\');
            case '/' -> builder.append('/');
            case 'b' -> builder.append('\b');
            case 'f' -> builder.append('\f');
            case 'n' -> builder.append('\n');
            case 'r' -> builder.append('\r');
            case 't' -> builder.append('\t');
            case 'u' -> {
              if (index + 4 > text.length()) {
                throw new IllegalArgumentException("invalid unicode escape");
              }
              try {
                builder.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
              } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid unicode escape");
              }
              index += 4;
            }
            default -> throw new IllegalArgumentException("invalid JSON escape");
          }
        } else if (c < 0x20) {
          throw new IllegalArgumentException("invalid control character in JSON");
        } else {
          builder.append(c);
        }
      }
      throw new IllegalArgumentException("unterminated JSON string");
    }

    private Boolean parseBoolean() {
      if (text.startsWith("true", index)) {
        index += 4;
        return true;
      }
      if (text.startsWith("false", index)) {
        index += 5;
        return false;
      }
      throw new IllegalArgumentException("invalid JSON boolean");
    }

    private Object parseNull() {
      if (text.startsWith("null", index)) {
        index += 4;
        return null;
      }
      throw new IllegalArgumentException("invalid JSON null");
    }

    private Number parseNumber() {
      int start = index;
      if (index < text.length() && text.charAt(index) == '-') {
        index++;
      }
      while (index < text.length() && Character.isDigit(text.charAt(index))) {
        index++;
      }
      if (index < text.length() && text.charAt(index) == '.') {
        index++;
        while (index < text.length() && Character.isDigit(text.charAt(index))) {
          index++;
        }
      }
      if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
        index++;
        if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
          index++;
        }
        while (index < text.length() && Character.isDigit(text.charAt(index))) {
          index++;
        }
      }
      if (index == start) {
        throw new IllegalArgumentException("invalid JSON number");
      }
      String token = text.substring(start, index);
      try {
        if (token.contains(".") || token.contains("e") || token.contains("E")) {
          return Double.parseDouble(token);
        }
        return Long.parseLong(token);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("invalid JSON number");
      }
    }

    private void skipWhitespace() {
      while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
        index++;
      }
    }

    private void consumeWhitespace() {
      skipWhitespace();
    }

    private boolean peek(char c) {
      return index < text.length() && text.charAt(index) == c;
    }

    private void expect(char c) {
      skipWhitespace();
      if (index >= text.length() || text.charAt(index) != c) {
        throw new IllegalArgumentException("expected character '" + c + "'");
      }
      index++;
    }
  }
}
