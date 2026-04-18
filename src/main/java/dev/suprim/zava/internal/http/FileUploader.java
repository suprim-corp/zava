package dev.suprim.zava.internal.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.conversation.ThreadType;
import dev.suprim.zava.exception.ZavaException;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.crypto.Hashing;
import dev.suprim.zava.internal.session.Context;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * File upload engine for the Zalo protocol.
 *
 * <p>Handles chunked uploads for images, videos, and other files.
 * Images resolve immediately from the HTTP response; videos and other
 * files wait for a WebSocket {@code file_done} callback.
 *
 * <p>Equivalent to zca-js {@code uploadAttachment.ts}.
 */
public class FileUploader {

    private static final Logger log = LoggerFactory.getLogger(FileUploader.class);
    private static final ObjectMapper MAPPER = ResponseHandler.mapper();
    private static final MediaType OCTET = MediaType.parse("application/octet-stream");
    private static final MediaType IMAGE_JPEG = MediaType.parse("image/jpeg");

    private final Context context;
    private final HttpClient http;
    private final ResponseHandler responseHandler;

    public FileUploader(Context context, HttpClient http, ResponseHandler responseHandler) {
        this.context = context;
        this.http = http;
        this.responseHandler = responseHandler;
    }

    /**
     * Upload file(s) and return upload results.
     *
     * @param files    list of files to upload
     * @param threadId the target thread ID
     * @param type     USER or GROUP
     * @return list of upload results (one per file)
     */
    public List<UploadResult> upload(List<FileSource> files, String threadId, ThreadType type) {
        if (files == null || files.isEmpty()) throw new ZavaException("No files to upload");

        String baseUrl = context.getServiceUrl("file") + "/api/"
                + (type == ThreadType.GROUP ? "group" : "message") + "/";
        String typeParam = type == ThreadType.GROUP ? "11" : "2";

        int chunkSize = getChunkSize();
        List<UploadResult> results = new ArrayList<>();
        long clientId = System.currentTimeMillis();

        for (FileSource file : files) {
            try {
                byte[] data = file.getData() != null ? file.getData() : Files.readAllBytes(file.getPath());
                String fileName = file.getFileName();
                String ext = getExtension(fileName).toLowerCase();
                FileType fileType = classifyFile(ext);
                int totalChunk = Math.max(1, (int) Math.ceil((double) data.length / chunkSize));

                Map<String, Object> params = new LinkedHashMap<>();
                if (type == ThreadType.GROUP) {
                    params.put("grid", threadId);
                } else {
                    params.put("toid", threadId);
                }
                params.put("totalChunk", totalChunk);
                params.put("fileName", fileName);
                params.put("clientId", clientId++);
                params.put("totalSize", data.length);
                params.put("imei", context.getImei());
                params.put("isE2EE", 0);
                params.put("jxl", 0);
                params.put("chunkId", 1);

                String uploadPath = fileType == FileType.IMAGE
                        ? "photo_original/upload"
                        : "asyncfile/upload";

                UploadResult result = null;

                // Upload chunks
                for (int i = 0; i < totalChunk; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(start + chunkSize, data.length);
                    byte[] chunk = Arrays.copyOfRange(data, start, end);

                    String encrypted = AesCbc.encodeAES(
                            context.getSecretKey(), MAPPER.writeValueAsString(params));

                    String url = baseUrl + uploadPath
                            + "?type=" + typeParam
                            + "&params=" + java.net.URLEncoder.encode(encrypted, "UTF-8")
                            + "&zpw_ver=" + context.getOptions().getApiVersion()
                            + "&zpw_type=" + context.getOptions().getApiType();

                    MultipartBody body = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("chunkContent", fileName,
                                    RequestBody.create(chunk, OCTET))
                            .build();

                    Response response = http.postMultipart(url, body);
                    JsonNode resNode = responseHandler.handleRaw(response, true);

                    if (i == totalChunk - 1) {
                        // Last chunk — build result
                        if (fileType == FileType.IMAGE) {
                            result = new UploadResult(FileType.IMAGE);
                            result.photoId = resNode.path("photoId").asText(null);
                            result.normalUrl = resNode.path("normalUrl").asText(null);
                            result.hdUrl = resNode.path("hdUrl").asText(null);
                            result.thumbUrl = resNode.path("thumbUrl").asText(null);
                            result.clientFileId = resNode.path("clientFileId").asLong(0);
                            result.width = file.getWidth();
                            result.height = file.getHeight();
                            result.totalSize = data.length;
                        } else {
                            // Video/Others — need WS callback
                            String fileId = resNode.path("fileId").asText(null);
                            if (fileId != null && !"-1".equals(fileId)) {
                                CompletableFuture<String[]> future = new CompletableFuture<>();
                                context.getUploadCallbacks().put(fileId, (url2, fid) -> {
                                    future.complete(new String[]{url2, fid});
                                });

                                try {
                                    String[] wsResult = future.get(60, TimeUnit.SECONDS);
                                    result = new UploadResult(fileType);
                                    result.fileUrl = wsResult[0];
                                    result.fileId = wsResult[1];
                                    result.totalSize = data.length;
                                    result.fileName = fileName;
                                    result.checksum = Hashing.md5Chunked(
                                            file.getPath() != null ? file.getPath() : writeTempFile(data));
                                } catch (Exception e) {
                                    throw new ZavaException("Upload callback timeout for file: " + fileName, e);
                                }
                            }
                        }
                    }

                    params.put("chunkId", i + 2);
                }

                if (result != null) results.add(result);

            } catch (ZavaException e) {
                throw e;
            } catch (Exception e) {
                throw new ZavaException("Failed to upload file: " + file.getFileName(), e);
            }
        }

        return results;
    }

    /**
     * Upload a single image for avatar (group or profile).
     *
     * @param imageData raw image bytes
     * @param endpoint  the avatar endpoint path (e.g. "/api/group/upavatar")
     * @param params    additional params to encrypt
     * @return raw response
     */
    public JsonNode uploadAvatar(byte[] imageData, String endpoint, Map<String, Object> params) {
        try {
            String encrypted = AesCbc.encodeAES(
                    context.getSecretKey(), MAPPER.writeValueAsString(params));

            String url = context.getServiceUrl("file") + endpoint
                    + "?zpw_ver=" + context.getOptions().getApiVersion()
                    + "&zpw_type=" + context.getOptions().getApiType();

            MultipartBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("params", encrypted)
                    .addFormDataPart("fileContent", "avatar.jpg",
                            RequestBody.create(imageData, IMAGE_JPEG))
                    .build();

            Response response = http.postMultipart(url, body);
            return responseHandler.handleRaw(response, true);
        } catch (ZavaException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaException("Failed to upload avatar", e);
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private int getChunkSize() {
        if (context.getSettings() != null
                && context.getSettings().getFeatures() != null
                && context.getSettings().getFeatures().getShareFile() != null) {
            int configured = context.getSettings().getFeatures().getShareFile().getChunkSizeFile();
            if (configured > 0) return configured;
        }
        return 512 * 1024; // default 512KB
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    private static FileType classifyFile(String ext) {
        switch (ext) {
            case "jpg": case "jpeg": case "png": case "webp":
                return FileType.IMAGE;
            case "mp4":
                return FileType.VIDEO;
            case "gif":
                return FileType.GIF;
            default:
                return FileType.OTHER;
        }
    }

    private static Path writeTempFile(byte[] data) throws IOException {
        Path tmp = Files.createTempFile("zava-upload-", ".tmp");
        Files.write(tmp, data);
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    // ── Types ────────────────────────────────────────────────────────────

    public enum FileType {
        IMAGE, VIDEO, GIF, OTHER
    }

    /**
     * Source for a file upload.
     */
    public static class FileSource {
        private final String fileName;
        private final Path path;
        private final byte[] data;
        private int width;
        private int height;

        /** From file path. */
        public FileSource(Path path) {
            this.path = path;
            this.fileName = path.getFileName().toString();
            this.data = null;
        }

        /** From byte array. */
        public FileSource(String fileName, byte[] data) {
            this.fileName = fileName;
            this.data = data;
            this.path = null;
        }

        public FileSource dimensions(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public String getFileName() { return fileName; }
        public Path getPath() { return path; }
        public byte[] getData() { return data; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }

    /**
     * Result of a file upload.
     */
    public static class UploadResult {
        private final FileType fileType;
        // Image fields
        String photoId;
        String normalUrl;
        String hdUrl;
        String thumbUrl;
        long clientFileId;
        int width;
        int height;
        // Video/File fields
        String fileUrl;
        String fileId;
        String checksum;
        String fileName;
        // Common
        long totalSize;

        public UploadResult(FileType fileType) { this.fileType = fileType; }

        public FileType getFileType() { return fileType; }
        public String getPhotoId() { return photoId; }
        public String getNormalUrl() { return normalUrl; }
        public String getHdUrl() { return hdUrl; }
        public String getThumbUrl() { return thumbUrl; }
        public long getClientFileId() { return clientFileId; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public String getFileUrl() { return fileUrl; }
        public String getFileId() { return fileId; }
        public String getChecksum() { return checksum; }
        public String getFileName() { return fileName; }
        public long getTotalSize() { return totalSize; }
    }
}
