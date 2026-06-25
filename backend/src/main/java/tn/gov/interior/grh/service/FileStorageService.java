package tn.gov.interior.grh.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.UUID;

@Service
public class FileStorageService {

    @Autowired
    private MinioClient minioClient;

    @Value("${aws.s3.bucket-name}")
    private String defaultBucketName;

    @Value("${aws.s3.endpoint}")
    private String endpoint;

    /**
     * Uploads a base64 encoded file to S3/MinIO.
     *
     * @param base64Content The base64 file data (can contain data URI prefix e.g., "data:image/png;base64,...")
     * @param originalFilename The original filename or reference identifier
     * @param folderName The folder/prefix inside the bucket (e.g. "profiles", "leaves")
     * @return The public URL of the uploaded file
     */
    public String uploadBase64(String base64Content, String originalFilename, String folderName) throws Exception {
        if (base64Content == null || base64Content.trim().isEmpty()) {
            return null;
        }

        // 1. Parse content type and base64 data
        String contentType = "application/octet-stream";
        String base64Data = base64Content;

        if (base64Content.contains(",")) {
            int commaIndex = base64Content.indexOf(",");
            String header = base64Content.substring(0, commaIndex);
            base64Data = base64Content.substring(commaIndex + 1);
            if (header.contains(":") && header.contains(";")) {
                contentType = header.substring(header.indexOf(":") + 1, header.indexOf(";"));
            }
        }

        // 2. Decode the Base64 data
        byte[] decodedBytes = Base64.getDecoder().decode(base64Data.trim());
        InputStream inputStream = new ByteArrayInputStream(decodedBytes);

        // 3. Generate a unique filename
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        } else {
            // Deduce extension from content type
            if ("image/jpeg".equalsIgnoreCase(contentType)) extension = ".jpg";
            else if ("image/png".equalsIgnoreCase(contentType)) extension = ".png";
            else if ("image/gif".equalsIgnoreCase(contentType)) extension = ".gif";
            else if ("application/pdf".equalsIgnoreCase(contentType)) extension = ".pdf";
        }
        
        String uniqueFilename = folderName + "/" + UUID.randomUUID().toString() + extension;

        // 4. Ensure default bucket exists
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(defaultBucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(defaultBucketName).build());
        }

        // Set bucket policy to read-only for public anonymous access
        String policy = "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [\n" +
                "    {\n" +
                "      \"Effect\": \"Allow\",\n" +
                "      \"Principal\": \"*\",\n" +
                "      \"Action\": [\"s3:GetObject\"],\n" +
                "      \"Resource\": [\"arn:aws:s3:::" + defaultBucketName + "/*\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                        .bucket(defaultBucketName)
                        .config(policy)
                        .build()
        );

        // 5. Upload the file
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(defaultBucketName)
                        .object(uniqueFilename)
                        .stream(inputStream, decodedBytes.length, -1)
                        .contentType(contentType)
                        .build()
        );

        // 6. Return the URL
        return endpoint + "/" + defaultBucketName + "/" + uniqueFilename;
    }
}
