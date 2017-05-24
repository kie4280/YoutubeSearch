import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.*;

/**
 * Created by kieChang on 2017/5/21.
 */
public class VideoRetriver {
    public static void main(String[] args) {

    }
    public String extractsigfunc() {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");

        extractjs();
        return null;
    }

    public String extractjs() {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("base.js");
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder builder = new StringBuilder();
        String out = null;
        try {
            String in;
            while((in = bufferedReader.readLine())!=null) {
                builder.append(in);
            }
            bufferedReader.close();
            out = builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
       return out;
    }
}
