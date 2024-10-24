# league-youtube-uploader
This project houses a simple Spring `ApplicationRunner` that will read the video files out of the specified path and 
upload them to a specified YouTube channel.

## Gaining access to the YouTube API
1. Create a Google Cloud project and enable the YouTube Data API v3
   1. You can follow the guide [here](https://developers.google.com/youtube/v3/quickstart/java) for how to do so. It is
free
2. Create OAuth 2.0 credentials and download the `client_secret_CLIENTID.json`, where `CLIENTID` is the client ID for
your project
3. Rename the downloaded file to `client_secret.json`

## Running the application
1. Make sure you have Java 23 or higher installed
    1. If needed, you can download it from [jdk.java.net](https://jdk.java.net/23/)
2. Clone the repository
3. Navigate to the project directory
4. Place the `client_secret.json` file in `src/main/resources`
5. Ensure that the `VIDEOS_PATH` environment variable is set to the path where your video files are located
6. Build the project using Maven:
    ```bash
    ./mvnw clean install
    ```
7. Run the application:
    ```bash
    ./mvnw spring-boot:run
    ```
8. Follow the prompts to authenticate with your Google account and authorize the application to access your YouTube 
channel
9. The application will then read the video files from the specified path and upload them to your YouTube channel

