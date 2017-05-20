package com.google.api.services.samples.youtube.cmdline.data;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import javax.activation.MimetypesFileTypeMap;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Created by kieChang on 2017/5/12.
 */

public class HttpTest {
    private static final int YOUTUBE_VIDEO_QUALITY_SMALL_240 = 36;
    private static final int YOUTUBE_VIDEO_QUALITY_MEDIUM_360 = 18;
    private static final int YOUTUBE_VIDEO_QUALITY_HD_720 = 22;
    private static final int YOUTUBE_VIDEO_QUALITY_HD_1080 = 37;
    //private final List<String> mElFields;
    private List<Integer> mPreferredVideoQualities;
    HttpsURLConnection connection = null;

    public HttpTest() {
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

    private HttpTest.YouTubeExtractorResult getYouTubeResult(String html) throws UnsupportedEncodingException, HttpTest.YouTubeExtractorException {
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

            return new HttpTest.YouTubeExtractorResult(videoURI, mediumThumbURI, highThumbURI, defaultThumbURI, standardThumbURI);
        } else {
            try {
                throw new HttpTest.YouTubeExtractorException("Status: " + video.get("status") + "\nReason: " + video.get("reason") + "\nError code: " + video.get("errorcode"));
            } catch (YouTubeExtractorException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public HashMap<String, String> getVideo(String html) {
        HashMap<String, String> video = new HashMap<>();
        Document jsoup = Jsoup.parse(html);
        Elements e = jsoup.body().getElementsByTag("script");
        Elements q = e.eq(1);
        String part = q.toString();
        String[] s = part.split(",");
        for (String i : s) {
            String[] pair = i.split(":");
            for (int a = 0; a < pair.length; a++) {
                pair[a] = pair[a].replaceAll("\"", "");
            }
            if (pair.length == 2) {
                video.put(pair[0], pair[1]);
            }
        }
        if (video.containsKey("url_encoded_fmt_stream_map")) {
            HashMap<String, String> splitmap = new HashMap<>();
            String encoded_s = video.get("url_encoded_fmt_stream_map");
            String decode = null;
            try {
                decode = URLDecoder.decode(URLDecoder.decode(encoded_s, "UTF-8"), "UTF-8");

                decode = decode.replaceAll("\\\\u0026| ", "&");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }
            List<String> fields = asList(decode.split("[&\\?;]"));
            for (String i : fields) {
                String[] pair = i.split("=");
                if (pair.length == 2) {
                    splitmap.put(pair[0], pair[1]);
                }
            }
            String[] params = splitmap.get("sparams").split(",");
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(splitmap.get("url") + "?");
            stringBuilder.append("sparams=" + splitmap.get("sparams") + "&signature=" + splitmap
                    .get("signature") + "&key=" + splitmap.get("key"));
            for (String par : params) {
                stringBuilder.append("&" + par + "=" + splitmap.get(par));
            }

            System.out.println(stringBuilder.toString());
        }
        return null;
    }

    //    https://www.youtube.com/watch?v=ftGQLvUwzjY //vevo
    //    https://www.youtube.com/watch?v=kJQP7kiw5Fk //vevo
    //    https://www.youtube.com/watch?v=h3cDZFEIoB8 //normal
    //    https://www.youtube.com/watch?v=xWzlwGVQ6_Q //sem

    public static void main(String[] args) {
        HttpTest test = new HttpTest();
        String result = test.getHTML("https://www.youtube.com/watch?v=xWzlwGVQ6_Q");
        test.getVideo(result);

    }
}
