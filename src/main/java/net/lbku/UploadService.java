package net.lbku;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import io.avaje.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class UploadService {
    private final static GsonFactory GSON_FACTORY;

    private static final FileDataStoreFactory DATA_STORE_FACTORY;

    private static final String GAMING_CATEGORY_ID;

    private static final Logger LOGGER;

    static {
        GSON_FACTORY = new GsonFactory();

        File directory = new File(System.getProperty("user.home"), ".credentials/youtube-upload");

        try {
            DATA_STORE_FACTORY = new FileDataStoreFactory(directory);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        GAMING_CATEGORY_ID = "20";

        LOGGER = LoggerFactory.getLogger(Application.class);
    }

    private Map<LocalDate, List<File>> getDatesToVideos() {
        String videosPath = Config.get("app.videos.path");

        Path videosDirectory = Path.of(videosPath);

        Map<LocalDate, List<File>> datesToVideos = new TreeMap<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(videosDirectory)) {
            for (Path path : stream) {
                File file = path.toFile();

                if (file.isDirectory()) {
                    continue;
                }

                String name = file.getName();

                String[] parts = name.split(" ");

                if (parts.length < 2) {
                    LOGGER.warn("File \"{}\" does not start with a date", name);

                    continue;
                }

                String dateString = parts[0];

                LocalDate date = LocalDate.parse(dateString);

                datesToVideos.compute(date, (ignored, value) -> {
                    if (value == null) {
                        value = new ArrayList<>();
                    }

                    value.add(file);

                    return value;
                });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return datesToVideos;
    }

    private Credential getCredential(NetHttpTransport httpTransport) throws IOException {
        String secretPathString = Config.get("app.youtube.client-secret-path");

        Path secretPath = Path.of(secretPathString);

        InputStream inputStream = Files.newInputStream(secretPath);

        GoogleAuthorizationCodeFlow flow;

        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(GSON_FACTORY, inputStreamReader);

            flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, GSON_FACTORY, clientSecrets,
                List.of(YouTubeScopes.YOUTUBE_UPLOAD))
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build();
        }

        LocalServerReceiver receiver = new LocalServerReceiver();

        AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(flow, receiver);

        return app.authorize("user");
    }

    private YouTube getYouTube() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        Credential credential = this.getCredential(httpTransport);

        YouTube.Builder builder = new YouTube.Builder(httpTransport, GSON_FACTORY, credential);

        builder.setApplicationName("YouTube League Uploader");

        return builder.build();
    }

    private String getTitle(LocalDate date, int index, int count) {
        String year = Config.get("app.league-of-legends.year");

        String season = Config.get("app.league-of-legends.season");

        String act = Config.get("app.league-of-legends.act");

        String champion = Config.get("app.league-of-legends.champion");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

        String dateString = formatter.format(date);

        return  "LoL %s Season %s Act %s (%s) -- %s %d/%d".formatted(year, season, act, champion, dateString, index,
            count);
    }

    private Video getVideo(LocalDate date, Integer index, Integer count) {
        Video video = new Video();

        VideoSnippet snippet = new VideoSnippet();

        snippet.setCategoryId(GAMING_CATEGORY_ID);

        String title = this.getTitle(date, index, count);

        LOGGER.info("Uploading {}...", title);

        snippet.setTitle(title);

        video.setSnippet(snippet);

        VideoStatus status = new VideoStatus();

        status.setPrivacyStatus("unlisted");

        status.setSelfDeclaredMadeForKids(false);

        video.setStatus(status);

        return video;
    }

    private void uploadVideo(YouTube youTube, File file, LocalDate date, int index, int count) {
        Objects.requireNonNull(youTube);

        Objects.requireNonNull(file);

        Video video = this.getVideo(date, index, count);

        try (FileInputStream fileInputStream = new FileInputStream(file);
             BufferedInputStream inputStream = new BufferedInputStream(fileInputStream)) {
            InputStreamContent mediaContent = new InputStreamContent("application/octet-stream", inputStream);

            YouTube.Videos.Insert request = youTube.videos()
                                                   .insert(List.of("snippet", "status"), video, mediaContent);

            Video response = request.execute();

            LOGGER.info("Upload response: {}", response);
        } catch (IOException e) {
            String message = e.getMessage();

            LOGGER.error(message, e);

            throw new IllegalStateException(e);
        }
    }

    public void uploadVideos() {
        YouTube youTube;

        try {
            youTube = this.getYouTube();
        } catch (GeneralSecurityException | IOException e) {
            String message = e.getMessage();

            LOGGER.error(message, e);

            return;
        }

        Map<LocalDate, List<File>> datesToVideos = this.getDatesToVideos();

        datesToVideos.forEach((date, files) -> {
            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);

                int index = i + 1;

                int count = files.size();

                this.uploadVideo(youTube, file, date, index, count);
            }
        });
    }
}
