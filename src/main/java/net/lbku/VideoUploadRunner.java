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
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public final class VideoUploadRunner implements ApplicationRunner {
    private final String videosPath;

    private final String leagueYear;

    private final String leagueSeason;

    private final String leagueAct;

    private final GsonFactory gsonFactory;

    private static final Logger LOGGER;

    static {
        LOGGER = LoggerFactory.getLogger(VideoUploadRunner.class);
    }

    @Autowired
    public VideoUploadRunner(@Value("${videos.path}") String videosPath,
        @Value("${league-of-legends.year}") String leagueYear,
        @Value("${league-of-legends.season}") String leagueSeason,
        @Value("${league-of-legends.act}") String leagueAct) {
        this.videosPath = Objects.requireNonNull(videosPath);

        this.leagueYear = Objects.requireNonNull(leagueYear);

        this.leagueSeason = Objects.requireNonNull(leagueSeason);

        this.leagueAct = Objects.requireNonNull(leagueAct);

        this.gsonFactory = GsonFactory.getDefaultInstance();
    }

    private Map<LocalDate, List<File>> getDatesToVideos() {
        Path videosDirectory = Path.of(this.videosPath);

        Map<LocalDate, List<File>> datesToVideos = new TreeMap<>();

        try (var stream = Files.newDirectoryStream(videosDirectory)) {
            for (Path path : stream) {
                File file = path.toFile();

                if (file.isDirectory()) {
                    continue;
                }

                String name = file.getName();

                String[] parts = name.split(" ");

                if (parts.length < 2) {
                    VideoUploadRunner.LOGGER.warn("File \"{}\" does not start with a date", name);

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
        Path path = Path.of("src/main/resources/client_secret.json");

        InputStream inputStream = Files.newInputStream(path);

        List<String> scopes = List.of(
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube",
            "https://www.googleapis.com/auth/youtubepartner",
            "https://www.googleapis.com/auth/youtube.force-ssl"
        );

        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(this.gsonFactory, inputStreamReader);

            GoogleAuthorizationCodeFlow.Builder builder = new GoogleAuthorizationCodeFlow.Builder(httpTransport,
                this.gsonFactory, clientSecrets, scopes);

            GoogleAuthorizationCodeFlow flow = builder.build();

            LocalServerReceiver receiver = new LocalServerReceiver();

            AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(flow, receiver);

            return app.authorize("user");
        }
    }

    private YouTube getYouTube() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        Credential credential = this.getCredential(httpTransport);

        YouTube.Builder builder = new YouTube.Builder(httpTransport, this.gsonFactory, credential);

        builder.setApplicationName("YouTube League Uploader");

        return builder.build();
    }

    private Video getVideo(LocalDate date, Integer index, Integer count) {
        Video video = new Video();

        VideoSnippet snippet = new VideoSnippet();

        snippet.setCategoryId("20");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

        String dateString = formatter.format(date);

        String title = "LoL %s Season %s - Act %s -- %s %d/%d".formatted(this.leagueYear, this.leagueSeason,
                this.leagueAct, dateString, index, count);

        VideoUploadRunner.LOGGER.info("Uploading {}...", title);

        snippet.setTitle(title);

        video.setSnippet(snippet);

        VideoStatus status = new VideoStatus();

        status.setPrivacyStatus("unlisted");

        video.setStatus(status);

        return video;
    }

    private void uploadVideo(YouTube youTube, File file, LocalDate date, int index, int count) {
        Objects.requireNonNull(youTube);

        Objects.requireNonNull(file);

        Video video = this.getVideo(date, index, count);

        try (FileInputStream fileInputStream = new FileInputStream(file);
             BufferedInputStream inputStream = new BufferedInputStream(fileInputStream)) {
            String type = "application/octet-stream";

            InputStreamContent mediaContent = new InputStreamContent(type, inputStream);

            YouTube.Videos.Insert request = youTube.videos()
                                                   .insert(List.of("snippet", "status"), video, mediaContent);

            Video response = request.execute();

            VideoUploadRunner.LOGGER.info("Upload response: {}", response);
        } catch (IOException e) {
            String message = e.getMessage();

            VideoUploadRunner.LOGGER.error(message, e);

            throw new RuntimeException(e);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        YouTube youTube;

        try {
            youTube = this.getYouTube();
        } catch (GeneralSecurityException | IOException e) {
            String message = e.getMessage();

            VideoUploadRunner.LOGGER.error(message, e);

            throw new RuntimeException(e);
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
