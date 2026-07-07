import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Runner {

    public static void main(String[] args) throws Exception {
        // 1 y 2: token y llaves 
        List<GeneracionQR.Request> coleccion = List.of(
                GeneracionQR.Request.postForm("1. Generacion exitosa de token",
                                "https://core.dev-biometria-facial.com/identidad-digital/biometria-facial/oauth2/v1/token",
                                Map.of("grant_type", "client_credentials"))
                        .header("Accept", "application/json")
                        .authBasic("BiometriaFacialQA", "BiometriaFacialQA")
                        .extraer("access_token", "access_token"),

                GeneracionQR.Request.get("2. Generacion-obtencion exitosa de llaves",
                                "https://core.dev-biometria-facial.com/identidad-digital/biometria-facial/seguridad/v1/aplicaciones/llaves")
                        .header("Accept", "application/json")
                        .authBearer("{{access_token}}")
                        .extraer("idAcceso", "id_Acceso")
        );

        try (PrintWriter out = new PrintWriter(new FileWriter("Request.txt"))) {
            for (GeneracionQR.Request req : coleccion) {
                GeneracionQR.ejecutar(req, out);
            }
        }

        // 3: liga / JWT RS256 
        String contenido = Files.readString(Path.of("Request.txt"));

        int inicio = contenido.indexOf("=== 2. Generacion-obtencion exitosa de llaves ===");
        if (inicio == -1) {
            throw new IllegalStateException("No se encontro el request de llaves en Request.txt");
        }
        int fin = contenido.indexOf("=== 3.", inicio);
        String bloqueLlaves = fin == -1 ? contenido.substring(inicio) : contenido.substring(inicio, fin);

        String idAcceso = GeneracionLigas.extraerCampo(bloqueLlaves, "idAcceso");
        String accesoPrivado = GeneracionLigas.extraerCampo(bloqueLlaves, "accesoPrivado");
        if (idAcceso == null || accesoPrivado == null) {
            throw new IllegalStateException("No se pudo extraer idAcceso/accesoPrivado de Request.txt");
        }

        String payload = """
                {
                  "folio": "%s",
                  "redirecciona": "https://www.google.com",
                  "iss": "1d8ec3ede3ea4760b62546e14eecb617",
                  "aud": "BF-QR",
                  "clienteUnico": {
                    "idPais": "1",
                    "idCanal": "1",
                    "idSucursal": "9567",
                    "folio": "6010"
                  },
                  "idAcceso": "%s"
                }""".formatted(UUID.randomUUID(), idAcceso);

        String jwt = GeneracionLigas.firmarJwtRS256(payload, accesoPrivado);

        try (PrintWriter out = new PrintWriter(new FileWriter("Request.txt", true))) {
            out.println("=== 3. Generacion de liga (JWT RS256) ===");
            out.println("Payload:");
            out.println(payload);
            out.println("JWT:");
            out.println(jwt);
            out.println();
            out.println("---Liga 1---");
            out.println("https://auth-qr.dev-biometria-facial.com/?token=" + jwt);
            out.println();
            out.println("---Liga 2---");
            out.println("https://faceauth.dev-biometria-facial.com/?token=" + jwt);
            out.println();
        }
    }
}
