#ifndef METRICS_SDK_IMAGE_PROCESSOR_H
#define METRICS_SDK_IMAGE_PROCESSOR_H

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstdint>

namespace metrics {

/**
 * ImageProcessor - Native image processing engine
 * 
 * Handles ALL image processing in C++:
 * - Bitmap downscaling
 * - JPEG compression
 * - Base64 encoding
 * 
 * Kotlin only captures the bitmap (Android API requirement) and calls this.
 */
class ImageProcessor {
public:
    static ImageProcessor& getInstance();

    // Prevent copying
    ImageProcessor(const ImageProcessor&) = delete;
    ImageProcessor& operator=(const ImageProcessor&) = delete;

    /**
     * Set processing configuration
     */
    void setConfig(int targetWidth, int targetHeight, int quality);

    /**
     * Process bitmap and return base64-encoded JPEG string.
     * This does ALL processing in C++:
     * 1. Read bitmap pixels via JNI
     * 2. Downscale to target dimensions
     * 3. Compress to JPEG
     * 4. Encode to base64
     * 
     * @param env JNI environment
     * @param bitmap Android Bitmap object
     * @return Base64-encoded JPEG string, or empty string on failure
     */
    std::string processAndEncode(JNIEnv* env, jobject bitmap);

    /**
     * Decode base64 string back to JPEG bytes.
     * Useful for extracting/viewing screenshots.
     */
    static std::vector<uint8_t> base64Decode(const std::string& encoded);

    /**
     * Set low memory state - will skip processing if true
     */
    void setLowMemory(bool isLowMemory);
    bool isLowMemory() const;

private:
    ImageProcessor();
    ~ImageProcessor();

    std::vector<uint8_t> downscaleRGBA(
        const uint8_t* srcPixels,
        int srcWidth,
        int srcHeight,
        int srcStride,
        int dstWidth,
        int dstHeight
    );

    static std::string base64Encode(const std::vector<uint8_t>& data);

    mutable std::mutex mutex_;
    int targetWidth_;
    int targetHeight_;
    int quality_;
    bool isLowMemory_;
};

} // namespace metrics

#endif // METRICS_SDK_IMAGE_PROCESSOR_H
