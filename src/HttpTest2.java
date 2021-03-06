import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import javax.activation.MimetypesFileTypeMap;
import javax.net.ssl.HttpsURLConnection;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Created by kieChang on 2017/5/12.
 */

public class HttpTest2 {
    private static final int YOUTUBE_VIDEO_QUALITY_SMALL_240 = 36;
    private static final int YOUTUBE_VIDEO_QUALITY_MEDIUM_360 = 18;
    private static final int YOUTUBE_VIDEO_QUALITY_HD_720 = 22;
    private static final int YOUTUBE_VIDEO_QUALITY_HD_1080 = 37;
    //private final List<String> mElFields;
    private List<Integer> mPreferredVideoQualities;
    HttpsURLConnection connection = null;
    JsonObject jsonObj = null;

    public HttpTest2() {
        //mElFields = new ArrayList<>(asList("embedded", "detailpage", "vevo", "youtube", ""));

        mPreferredVideoQualities = asList(YOUTUBE_VIDEO_QUALITY_HD_1080, YOUTUBE_VIDEO_QUALITY_HD_720,
                YOUTUBE_VIDEO_QUALITY_MEDIUM_360, YOUTUBE_VIDEO_QUALITY_SMALL_240);
    }

    public String getHTML(String url) {
        String t = null;
        try {
            t = getHTML(new URL(url));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return t;
    }

    public final class YouTubeExtractorException extends Exception {
        public YouTubeExtractorException(String detailMessage) {
            super(detailMessage);
        }
    }

    public interface YouTubeExtractorListener {

        void onSuccess(YouTubeExtractor.YouTubeExtractorResult result);

        void onFailure(Error error);
    }

    public static final class YouTubeExtractorResult {
        private final URI mVideoURI, mMediumThumbURI, mHighThumbURI;
        private final URI mDefaultThumbURI, mStandardThumbURI;

        private YouTubeExtractorResult(URI videoURI, URI mediumThumbURI, URI highThumbURI, URI defaultThumbURI, URI standardThumbURI) {
            mVideoURI = videoURI;
            mMediumThumbURI = mediumThumbURI;
            mHighThumbURI = highThumbURI;
            mDefaultThumbURI = defaultThumbURI;
            mStandardThumbURI = standardThumbURI;
        }
    }

    public String getHTML(URL url) {
        StringBuilder response = new StringBuilder();
        try {
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            connection.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return response.toString();
    }

    private static HashMap<String, String> getQueryMap(String queryString, String charsetName) {
        HashMap<String, String> map = new HashMap<>();

        String[] fields = queryString.split("&");

        for (String field : fields) {
            String[] pair = field.split("=");
            if (pair.length == 2) {
                String key = pair[0];
                String value = null;
                try {
                    value = URLDecoder.decode(pair[1], charsetName).replace('+', ' ');
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                map.put(key, value);
            }
        }

        return map;
    }

    private HttpTest2.YouTubeExtractorResult getYouTubeResult(String html) throws UnsupportedEncodingException, HttpTest2.YouTubeExtractorException {
        HashMap<String, String> video = getQueryMap(html, "UTF-8");
        URI videoURI = null;

        if (video.containsKey("url_encoded_fmt_stream_map")) {
            List<String> streamQueries = new ArrayList<String>(asList(video.get("url_encoded_fmt_stream_map").split(",")));

            String adaptiveFmts = video.get("adaptive_fmts");
            String[] split = adaptiveFmts.split(",");

            streamQueries.addAll(asList(split));

            HashMap<Integer, String> streamLinks = new HashMap<>();
            for (String streamQuery : streamQueries) {
                HashMap<String, String> stream = getQueryMap(streamQuery, "UTF-8");
                String type = stream.get("type").split(";")[0];
                String urlString = stream.get("url");

                if (urlString != null && MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(type) != null) {
                    String signature = stream.get("sig");

                    if (signature != null) {
                        urlString = urlString + "&signature=" + signature;
                    }

                    if (getQueryMap(urlString, "UTF-8").containsKey("signature")) {
                        streamLinks.put(Integer.parseInt(stream.get("itag")), urlString);
                    }
                }
            }

            for (Integer videoQuality : mPreferredVideoQualities) {
                if (streamLinks.get(videoQuality) != null) {
                    String streamLink = streamLinks.get(videoQuality);
                    videoURI = URI.create(streamLink);
                    break;
                }
            }

            final URI mediumThumbURI = video.containsKey("iurlmq") ? URI.create(video.get("iurlmq")) : null;
            final URI highThumbURI = video.containsKey("iurlhq") ? URI.create(video.get("iurlhq")) : null;
            final URI defaultThumbURI = video.containsKey("iurl") ? URI.create(video.get("iurl")) : null;
            final URI standardThumbURI = video.containsKey("iurlsd") ? URI.create(video.get("iurlsd")) : null;

            return new HttpTest2.YouTubeExtractorResult(videoURI, mediumThumbURI, highThumbURI, defaultThumbURI, standardThumbURI);
        } else {
            try {
                throw new HttpTest2.YouTubeExtractorException("Status: " + video.get("status") + "\nReason: " + video.get("reason") + "\nError code: " + video.get("errorcode"));
            } catch (YouTubeExtractorException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public HashMap<String, String> getVideo(String html) {

        HashMap<String, String> links = new HashMap<>();
        Document jsoup = Jsoup.parse(html);
        Elements q = jsoup.body().getElementsByTag("script");
        Elements j = q.eq(1);
        String part = j.toString();
        part = part.replaceAll("</?script>", "");
        int begin = part.indexOf("ytplayer.config = ");
        int l = 0;
        int r = 0;
        String ytplayer = null;
        for (int a = begin; a < part.length(); a++) {
            char i = (char) part.codePointAt(a);
            if (i == '{') {
                l++;
            } else if (i == '}') {
                r++;
            }
            if (r == l && r != 0) {
                ytplayer = part.substring(begin + 18, a + 1);
                break;
            }
        }

        jsonObj = new JsonParser().parse(ytplayer).getAsJsonObject();
        String basejsurl = "https://www.youtube.com" + jsonObj.getAsJsonObject("assets")
                .get("js").getAsString().replaceAll("\"", "");
        JsonObject videojson = jsonObj.getAsJsonObject("args");


        if (videojson.has("url_encoded_fmt_stream_map")) {

            String encoded_s = videojson.get("url_encoded_fmt_stream_map").getAsString();
            String adaptiveurl = videojson.get("adaptive_fmts").getAsString();
            List<String> videos = new LinkedList<>(asList(encoded_s.split(",")));
            videos.addAll(asList(adaptiveurl.split(",")));

            for (String e : videos) {
                e = decode(e);
                String[] fields = e.split("[&\\?;]");
                HashMap<String, String> splitmap = new HashMap<>();
                for (String i : fields) {
                    String[] pair = i.split("=");
                    if (pair.length == 2) {
                        splitmap.put(pair[0], pair[1]);
                    }
                }

                String[] params = splitmap.get("sparams").split(",");
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(splitmap.get("url") + "?" + "sparams=" + splitmap.get
                        ("sparams") + "&key=" + splitmap.get("key"));
                if (splitmap.containsKey("s")) {
                    String fake = splitmap.get("s");
                    stringBuilder.append("&signature=" + decypher(basejsurl, fake));

                } else {
                    stringBuilder.append("&signature=" + splitmap.get("signature"));
                }

                for (String par : params) {
                    stringBuilder.append("&" + par + "=" + splitmap.get(par));
                }

                links.put(splitmap.get("itag"), stringBuilder.toString());
                System.out.println(stringBuilder.toString());
            }


        }
        return links;
    }

    public void runScript() {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine scriptEngine = manager.getEngineByName("JavaScript");
        try {
            scriptEngine.eval(new FileReader(new File("D:\\development\\MyProjects\\IdeaProjects\\java\\YoutubeSearch\\src\\resources\\base.js")));
        } catch (ScriptException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String decode(String encoded_s) {
        String decode = null;
        try {
            decode = URLDecoder.decode(URLDecoder.decode(encoded_s, "UTF-8"),
                    "UTF-8");
            //decode = decode.replaceAll("\\\\u0026", "&");
            decode = decode.replaceAll(" ", "");

        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        }
        return decode;
    }

    private String decypher(String basejsurl, String in) {
        String out = null;
        String basejs = getHTML(basejsurl);
        return out;
    }


    //    https://www.youtube.com/watch?v=ftGQLvUwzjY //vevo
    //    https://www.youtube.com/watch?v=kJQP7kiw5Fk //vevo
    //    https://www.youtube.com/watch?v=h3cDZFEIoB8 //normal
    //    https://www.youtube.com/watch?v=xWzlwGVQ6_Q //sem

    public static void main(String[] args) {
        HttpTest2 test = new HttpTest2();
        String result = test.getHTML("https://www.youtube.com/watch?v=xWzlwGVQ6_Q");
        test.getVideo(result);
//        test.runScript();

    }
}
