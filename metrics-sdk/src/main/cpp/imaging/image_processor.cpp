#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"
#include "image_processor.h"
#include <android/log.h>
#include <android/bitmap.h>
#include <vector>
#include <string>
#include <cstring>
#include <cmath>
#include <mutex>

#define LOG_TAG "MetricsSDK"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace metrics {

// Base64 encoding table
static const char base64_chars[] = 
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

// JPEG write callback - appends to vector
static void jpeg_write_callback(void* context, void* data, int size) {
    std::vector<uint8_t>* buffer = static_cast<std::vector<uint8_t>*>(context);
    uint8_t* bytes = static_cast<uint8_t*>(data);
    buffer->insert(buffer->end(), bytes, bytes + size);
}

ImageProcessor& ImageProcessor::getInstance() {
    static ImageProcessor instance;
    return instance;
}

ImageProcessor::ImageProcessor() 
    : targetWidth_(360), targetHeight_(640), quality_(40), isLowMemory_(false) {
    LOGI("ImageProcessor initialized (C++ processing)");
}

ImageProcessor::~ImageProcessor() {
    LOGI("ImageProcessor destroyed");
}

void ImageProcessor::setConfig(int targetWidth, int targetHeight, int quality) {
    std::lock_guard<std::mutex> lock(mutex_);
    targetWidth_ = targetWidth;
    targetHeight_ = targetHeight;
    quality_ = quality;
    LOGD("Config set: %dx%d, quality=%d", targetWidth, targetHeight, quality);
}

void ImageProcessor::setLowMemory(bool isLowMemory) {
    std::lock_guard<std::mutex> lock(mutex_);
    isLowMemory_ = isLowMemory;
}

bool ImageProcessor::isLowMemory() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return isLowMemory_;
}

std::string ImageProcessor::base64Encode(const std::vector<uint8_t>& data) {
    std::string result;
    result.reserve(((data.size() + 2) / 3) * 4);
    
    size_t i = 0;
    size_t len = data.size();
    
    while (i < len) {
        uint32_t octet_a = i < len ? data[i++] : 0;
        uint32_t octet_b = i < len ? data[i++] : 0;
        uint32_t octet_c = i < len ? data[i++] : 0;
        
        uint32_t triple = (octet_a << 16) + (octet_b << 8) + octet_c;
        
        result += base64_chars[(triple >> 18) & 0x3F];
        result += base64_chars[(triple >> 12) & 0x3F];
        result += (i > len + 1) ? '=' : base64_chars[(triple >> 6) & 0x3F];
        result += (i > len) ? '=' : base64_chars[triple & 0x3F];
    }
    
    return result;
}

std::vector<uint8_t> ImageProcessor::downscaleRGBA(
    const uint8_t* srcPixels, 
    int srcWidth, 
    int srcHeight,
    int srcStride,
    int dstWidth, 
    int dstHeight
) {
    // Output is RGB (3 bytes per pixel) for JPEG encoding
    std::vector<uint8_t> dst(dstWidth * dstHeight * 3);
    
    float xRatio = static_cast<float>(srcWidth) / dstWidth;
    float yRatio = static_cast<float>(srcHeight) / dstHeight;
    
    for (int y = 0; y < dstHeight; ++y) {
        for (int x = 0; x < dstWidth; ++x) {
            // Simple nearest-neighbor for speed
            int srcX = static_cast<int>(x * xRatio);
            int srcY = static_cast<int>(y * yRatio);
            
            srcX = std::min(srcX, srcWidth - 1);
            srcY = std::min(srcY, srcHeight - 1);
            
            const uint8_t* srcPixel = srcPixels + (srcY * srcStride) + (srcX * 4);
            uint8_t* dstPixel = dst.data() + (y * dstWidth + x) * 3;
            
            // ARGB_8888 format: R, G, B, A -> copy RGB only
            dstPixel[0] = srcPixel[0]; // R
            dstPixel[1] = srcPixel[1]; // G
            dstPixel[2] = srcPixel[2]; // B
        }
    }
    
    return dst;
}

std::string ImageProcessor::processAndEncode(
    JNIEnv* env,
    jobject bitmap
) {
    if (isLowMemory()) {
        LOGD("Low memory - skipping screenshot");
        return "";
    }
    
    if (env == nullptr || bitmap == nullptr) {
        LOGE("Invalid JNI environment or bitmap");
        return "";
    }
    
    // Get bitmap info
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to get bitmap info");
        return "";
    }
    
    LOGD("Processing bitmap: %dx%d, stride=%d, format=%d",
         info.width, info.height, info.stride, info.format);
    
    // Lock pixels
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to lock bitmap pixels");
        return "";
    }
    
    // Calculate target dimensions maintaining aspect ratio
    int targetW, targetH;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        float aspectRatio = static_cast<float>(info.width) / info.height;
        targetW = targetWidth_;
        targetH = static_cast<int>(targetW / aspectRatio);
        
        if (targetH > targetHeight_) {
            targetH = targetHeight_;
            targetW = static_cast<int>(targetH * aspectRatio);
        }
    }
    
    // Downscale to RGB
    std::vector<uint8_t> rgbData = downscaleRGBA(
        static_cast<uint8_t*>(pixels),
        info.width,
        info.height,
        info.stride,
        targetW,
        targetH
    );
    
    // Unlock pixels - we're done with the bitmap
    AndroidBitmap_unlockPixels(env, bitmap);
    
    // Compress to JPEG
    std::vector<uint8_t> jpegData;
    jpegData.reserve(targetW * targetH); // Rough estimate
    
    int quality;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        quality = quality_;
    }
    
    int result = stbi_write_jpg_to_func(
        jpeg_write_callback,
        &jpegData,
        targetW,
        targetH,
        3, // RGB components
        rgbData.data(),
        quality
    );
    
    if (result == 0) {
        LOGE("Failed to compress JPEG");
        return "";
    }
    
    // Encode to base64
    std::string base64 = base64Encode(jpegData);
    
    LOGI("Screenshot processed: %dx%d -> %dx%d, JPEG=%zu bytes, Base64=%zu chars",
         (int)info.width, (int)info.height, targetW, targetH, 
         jpegData.size(), base64.size());
    
    return base64;
}

// Decode base64 to bytes (for extracting images)
std::vector<uint8_t> ImageProcessor::base64Decode(const std::string& encoded) {
    std::vector<uint8_t> result;
    
    if (encoded.empty()) return result;
    
    // Build decode table
    int decodeTable[256];
    memset(decodeTable, -1, sizeof(decodeTable));
    for (int i = 0; i < 64; i++) {
        decodeTable[(unsigned char)base64_chars[i]] = i;
    }
    
    result.reserve(encoded.size() * 3 / 4);
    
    int val = 0, valb = -8;
    for (unsigned char c : encoded) {
        if (decodeTable[c] == -1) break;
        val = (val << 6) + decodeTable[c];
        valb += 6;
        if (valb >= 0) {
            result.push_back((val >> valb) & 0xFF);
            valb -= 8;
        }
    }
    
    return result;
}

} // namespace metrics
