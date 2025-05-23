package net.lbku;

public final class Application {
    public static void main(String[] args) {
        UploadService service = new UploadService();

        service.uploadVideos();
    }
}
