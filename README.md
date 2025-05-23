# league-youtube-uploader
This project houses a simple Java class that will read the video files out of the specified path and 
upload them to a specified YouTube channel.

## Gaining access to the YouTube API
1. Create a Google Cloud project and enable the YouTube Data API v3
   1. You can follow the guide [here](https://developers.google.com/youtube/v3/quickstart/java) for how to do so. It is
free
2. Create OAuth 2.0 credentials and download the `client_secret_CLIENTID.json`, where `CLIENTID` is the client ID for
your project
3. Rename the downloaded file to `client_secret.json`

## Running the application
_NOTE:_ This application assumes your videos start with a date of the form `yyyy-mm-dd`. This happens by default when recording with OBS

1. Make sure you have Java 21 or higher installed
    1. If needed, you can download it from [AWS](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html)
2. Clone the repository
3. Navigate to the project directory
4. Place the `client_secret.json` file in `src/main/resources`
5. Ensure that the following environment variables are set:
   1. `APP_YOUTUBE_CLIENT_SECRET_PATH` — The path where your YouTube client secret is stored 
   2. `APP_VIDEOS_PATH` — The path where your video files are located
   3. `APP_LEAGUE_OF_LEGENDS_YEAR` — The year that the videos are from
   4. `APP_LEAGUE_OF_LEGENDS_SEASON` — The League of Legends season that the videos are from
   5. `APP_LEAGUE_OF_LEGENDS_ACT` — The League of Legends act that the videos are from
   6. `APP_LEAGUE_OF_LEGENDS_CHAMPION` — An optional field specifying the Legends of Legends champion you played in the
video
6. Build the project using Maven:
    ```bash
    ./mvnw clean install
    ```
7. Run the application:
    ```bash
    ./mvnw exec:java
    ```
8. Follow the prompts to authenticate with your Google account and authorize the application to access your YouTube 
channel
9. The application will then read the video files from the specified path and upload them to your YouTube channel

