import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeneracionQR {

    //VARS
    static final Map<String, String> VARS = new LinkedHashMap<>();

    static String resolve(String texto) {
        if (texto == null) return null;
        String resultado = texto;
        for (Map.Entry<String, String> v : VARS.entrySet()) {
            resultado = resultado.replace("{{" + v.getKey() + "}}", v.getValue());
        }
        return resultado;
    }

    //JSON extractor
    static String extraerCampoJson(String json, String campo) {
        Pattern p = Pattern.compile("\"" + campo + "\"\\s*:\\s*(\"([^\"]*)\"|[0-9.eE+-]+|true|false|null)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(2) != null ? m.group(2) : m.group(1);
        }
        return null;
    }

    //JSON como estructura Bruno
    static String prettyJson(String json) {
        if (json == null || json.isBlank()) return json;
        StringBuilder out = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    out.append(c);
                    inString = true;
                }
                case '{', '[' -> {
                    char cierre = (c == '{') ? '}' : ']';
                    int j = i + 1;
                    while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
                    if (j < json.length() && json.charAt(j) == cierre) {
                        out.append(c).append(cierre);
                        i = j;
                    } else {
                        indent++;
                        out.append(c).append('\n').append("  ".repeat(indent));
                    }
                }
                case '}', ']' -> {
                    indent--;
                    out.append('\n').append("  ".repeat(indent)).append(c);
                }
                case ',' -> out.append(c).append('\n').append("  ".repeat(indent));
                case ':' -> out.append(": ");
                default -> {
                    if (!Character.isWhitespace(c)) out.append(c);
                }
            }
        }
        return out.toString();
    }

    record Extraccion(String campoJson, String nombreVar) {}

    record Request(String nombre, String metodo, String url, Map<String, String> headers, String body,
                    List<Extraccion> extracciones) {

        static Request get(String nombre, String url) {
            return new Request(nombre, "GET", url, new LinkedHashMap<>(), null, new ArrayList<>());
        }

        //BODY JSON
        static Request post(String nombre, String url, String body) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            return new Request(nombre, "POST", url, headers, body, new ArrayList<>());
        }

        //BODY form-urlencoded
        static Request postForm(String nombre, String url, Map<String, String> datos) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/x-www-form-urlencoded");
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : datos.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
            return new Request(nombre, "POST", url, headers, sb.toString(), new ArrayList<>());
        }

        //HEADERS
        Request header(String clave, String valor) {
            headers.put(clave, valor);
            return this;
        }

        //AUTH Basic
        Request authBasic(String usuario, String clave) {
            String cred = Base64.getEncoder().encodeToString((usuario + ":" + clave).getBytes());
            headers.put("Authorization", "Basic " + cred);
            return this;
        }

        //AUTH Bearer (acepta {{vars}}
        Request authBearer(String token) {
            headers.put("Authorization", "Bearer " + token);
            return this;
        }

        //Extrae un campo del JSON 
        Request extraer(String campoJson, String nombreVar) {
            extracciones.add(new Extraccion(campoJson, nombreVar));
            return this;
        }
    }

    static final HttpClient CLIENT = HttpClient.newHttpClient();

    static void ejecutar(Request req, PrintWriter out) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(resolve(req.url())));

            for (Map.Entry<String, String> h : req.headers().entrySet()) {
                builder.header(h.getKey(), resolve(h.getValue()));
            }

            HttpRequest.BodyPublisher bodyPublisher = req.body() != null
                    ? BodyPublishers.ofString(resolve(req.body()))
                    : BodyPublishers.noBody();

            builder.method(req.metodo(), bodyPublisher);

            HttpResponse<String> response = CLIENT.send(builder.build(), BodyHandlers.ofString());

            out.println("=== " + req.nombre() + " ===");
            out.println("Status: " + response.statusCode());
            out.println("Body:");
            out.println(prettyJson(response.body()));

            for (Extraccion ex : req.extracciones()) {
                String valor = extraerCampoJson(response.body(), ex.campoJson());
                if (valor != null) {
                    VARS.put(ex.nombreVar(), valor);
                }
            }
            out.println();
        } catch (Exception e) {
            out.println("=== " + req.nombre() + " (ERROR) ===");
            e.printStackTrace(out);
        }
    }

}
