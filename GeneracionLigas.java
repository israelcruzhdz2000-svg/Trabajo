import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeneracionLigas {

    static final String URL_HERRAMIENTA = "https://10015.io/tools/jwt-encoder-decoder";

    static String extraerCampo(String texto, String campo) {
        Pattern p = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(texto);
        return m.find() ? m.group(1) : null;
    }

    
    static void clickCompleto(WebDriver driver, WebElement el) {
        String script = "var el = arguments[0];" +
                "['mouseover','mousedown','mouseup','click'].forEach(function(tipo){" +
                "  var ev = new MouseEvent(tipo, {bubbles: true, cancelable: true, view: window});" +
                "  el.dispatchEvent(ev);" +
                "});";
        ((JavascriptExecutor) driver).executeScript(script, el);
    }

    static String firmarJwtRS256(String payloadJson, String llavePrivadaPkcs1) throws Exception {
        ChromeOptions opciones = new ChromeOptions();
        opciones.addArguments("--headless=new", "--window-size=1400,1000");

        WebDriver driver = new ChromeDriver(opciones);
        try {
            WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(20));
            driver.get(URL_HERRAMIENTA);

            for (WebElement cerrar : driver.findElements(By.id("cookie-disclaimer-close"))) {
                clickCompleto(driver, cerrar);
            }

            // Seleccionar RS256 (reintenta: el menu de react-select a veces no abre a tiempo)
            for (int intento = 0; intento < 5; intento++) {
                WebElement valorAlgoritmo = driver.findElement(By.cssSelector(".css-1vurhgc-singleValue"));
                clickCompleto(driver, valorAlgoritmo);
                Thread.sleep(400);
                for (WebElement opcion : driver.findElements(By.xpath("//*[contains(@class,'option')]"))) {
                    if (opcion.getText().trim().equals("RS256")) {
                        clickCompleto(driver, opcion);
                        break;
                    }
                }
                Thread.sleep(400);
                if (driver.findElement(By.cssSelector(".css-1vurhgc-singleValue")).getText().equals("RS256")) break;
            }

            
            String llavePem = "-----BEGIN RSA PRIVATE KEY-----\n" + llavePrivadaPkcs1 + "\n-----END RSA PRIVATE KEY-----";
            WebElement campoLlave = espera.until(ExpectedConditions.presenceOfElementLocated(By.id("privateKey")));
            campoLlave.click();
            campoLlave.sendKeys(llavePem);

            
            WebElement campoPayload = driver.findElement(By.id("text"));
            campoPayload.click();
            campoPayload.sendKeys(Keys.chord(Keys.COMMAND, "a"));
            campoPayload.sendKeys(Keys.DELETE);
            campoPayload.sendKeys(payloadJson);

            
            WebElement boton = driver.findElement(By.xpath("//div[contains(@class,'button-text') and text()='Encode']"));
            clickCompleto(driver, boton);

            
            WebElement resultado = espera.until(d -> {
                WebElement el = d.findElement(By.cssSelector("textarea[readonly]"));
                String valor = el.getDomProperty("value");
                return (valor != null && !valor.isBlank()) ? el : null;
            });
            return resultado.getDomProperty("value");
        } finally {
            driver.quit();
        }
    }
}
