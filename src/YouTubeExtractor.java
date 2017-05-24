import javax.activation.MimetypesFileTypeMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Arrays.asList;

/**
 * Created by Pietro Caselani
 * On 06/03/14
 * YouTubeExtractor
 */
public final class YouTubeExtractor {
    //region Fields
    private static final int YOUTUBE_VIDEO_QUALITY_SMALL_240 = 36;
    private static final int YOUTUBE_VIDEO_QUALITY_MEDIUM_360 = 18;
    private static final int YOUTUBE_VIDEO_QUALITY_HD_720 = 22;
    private static final int YOUTUBE_VIDEO_QUALITY_HD_1080 = 37;
    private final String mVideoIdentifier;
    private final List<String> mElFields;
    private URLConnection mConnection;
    private List<Integer> mPreferredVideoQualities;
    private boolean mCancelled;
    //endregion

    //region Constructors
    public YouTubeExtractor(String videoIdentifier) {
        mVideoIdentifier = videoIdentifier;
        mElFields = new ArrayList<>(asList("info", "embed", "detailpage", "vevo", "youtu.be"));

        mPreferredVideoQualities = asList(YOUTUBE_VIDEO_QUALITY_HD_1080, YOUTUBE_VIDEO_QUALITY_HD_720,
                YOUTUBE_VIDEO_QUALITY_MEDIUM_360, YOUTUBE_VIDEO_QUALITY_SMALL_240);
    }
    //endregion

    //region Getters and Setters
    public List<Integer> getPreferredVideoQualities() {
        return mPreferredVideoQualities;
    }

    public void setPreferredVideoQualities(List<Integer> preferredVideoQualities) {
        mPreferredVideoQualities = preferredVideoQualities;
    }
    //endregion

    //region Public
    public void startExtracting(final YouTubeExtractorListener listener) {
        String elField = mElFields.get(0);
        mElFields.remove(0);
        if (elField.length() > 0) elField = "&el=" + elField;

        final String language = "en";//Locale.getDefault().getLanguage();


        final String link = String.format("https://www.youtube.com/get_video_info?video_id=%s%s&ps=default&eurl=&gl=US&hl=%s", mVideoIdentifier, elField, language); //correct
//        final String link = "https://www.youtube.com/get_video_info?&video_id=" + mVideoIdentifier + "&el=info&ps=default&eurl=&gl=US&hl=en";       //correct too

        final ExecutorService executors = Executors.newFixedThreadPool(2);

        executors.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mConnection = new URL(link).openConnection();
                    mConnection.setRequestProperty("Accept-Language", language);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(mConnection.getInputStream()));;
                    StringBuilder builder = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null && !mCancelled) builder.append(line);

                    reader.close();

                    if (!mCancelled) {
                        final YouTubeExtractorResult result = getYouTubeResult(builder.toString());
                        if (!mCancelled && listener != null) {
                            listener.onSuccess(result);
                        }

                    }
                } catch (final Exception e) {

                    if (!mCancelled && listener != null) {
                        listener.onFailure(new Error(e));
                    }

                } finally {
                    if (mConnection != null) {

                    }
                    executors.shutdown();
                }
            }
        });
    }

    public void cancelExtracting() {
        mCancelled = true;
    }
    //endregion

    //region Private
    private static HashMap<String, String> getQueryMap(String queryString, String charsetName) throws UnsupportedEncodingException {
        HashMap<String, String> map = new HashMap<>();

        String[] fields = queryString.split("&");

        for (String field : fields) {
            String[] pair = field.split("=");
            if (pair.length == 2) {
                String key = pair[0];
                String value = URLDecoder.decode(pair[1], charsetName).replace('+', ' ');
                map.put(key, value);
            }
        }

        return map;
    }

    private YouTubeExtractorResult getYouTubeResult(String html) throws UnsupportedEncodingException, YouTubeExtractorException {
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

            return new YouTubeExtractorResult(videoURI, mediumThumbURI, highThumbURI, defaultThumbURI, standardThumbURI);
        } else {
            throw new YouTubeExtractorException("Status: " + video.get("status") + "\nReason: " + video.get("reason") + "\nError code: " + video.get("errorcode"));
        }
    }
    //endregion

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

        public URI getVideoURI() {
            return mVideoURI;
        }

        public URI getMediumThumbURI() {
            return mMediumThumbURI;
        }

        public URI getHighThumbURI() {
            return mHighThumbURI;
        }

        public URI getDefaultThumbURI() {
            return mDefaultThumbURI;
        }

        public URI getStandardThumbURI() {
            return mStandardThumbURI;
        }
    }

    public final class YouTubeExtractorException extends Exception {
        public YouTubeExtractorException(String detailMessage) {
            super(detailMessage);
        }
    }

    public interface YouTubeExtractorListener {

        void onSuccess(YouTubeExtractorResult result);

        void onFailure(Error error);
    }

    //    https://www.youtube.com/watch?v=ftGQLvUwzjY //vevo
    //    https://www.youtube.com/watch?v=h3cDZFEIoB8 //normal
    public static void main(String[] args) {

        YouTubeExtractor extractor = new YouTubeExtractor("ftGQLvUwzjY");
        extractor.startExtracting(new YouTubeExtractorListener() {
            @Override
            public void onSuccess(YouTubeExtractorResult result) {
                System.out.println("video " + result.getVideoURI());
            }

            @Override
            public void onFailure(Error error) {
                System.out.println(error.getMessage());

            }
        });
    }
}